/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.impl.redis.RedisUtils.REDIS_STREAM_INTERNAL_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.AbstractProducer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.impl.redis.monitoring.dto.RedisEventMetricDTOMapper;
import io.harness.eventsframework.impl.redis.monitoring.publisher.RedisEventMetricPublisher;
import io.harness.eventsframework.producer.Message;
import io.harness.redis.RedisConfig;

import com.google.inject.Inject;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.vavr.control.Try;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;

@OwnedBy(PL)
@Slf4j
public class RedisProducer extends AbstractProducer {
  private static final String PRODUCER = "producer";
  private static final String REDIS_PUSH_EVENT_METRIC = "redis_push_event_metric";
  private RStream<String, String> stream;
  private RedissonClient redissonClient;
  @Inject private RedisEventMetricPublisher redisEventMetricPublisher;
  // This is used when the consumer for the event are no longer accepting due to some failure and
  // the messages are continuously being accumulated in Redis. To come up with this number, it is
  // very important to understand the alerting on the consumers and the scale estimations of a
  // particular use-case which is pushing to the topic
  private int maxTopicSize;

  private Retry retry;

  public RedisProducer(String topicName, @NotNull RedisConfig redisConfig, int maxTopicSize, String producerName,
      RedisEventMetricPublisher redisEventMetricPublisher) {
    super(topicName, producerName);
    RedissonClient redissonClient = RedisUtils.getClient(redisConfig);
    initProducer(topicName, redissonClient, maxTopicSize, redisConfig.getEnvNamespace());
    this.redisEventMetricPublisher = redisEventMetricPublisher;
  }

  public RedisProducer(String topicName, @NotNull RedisConfig redisConfig, int maxTopicSize, String producerName) {
    super(topicName, producerName);
    RedissonClient redissonClient = RedisUtils.getClient(redisConfig);
    initProducer(topicName, redissonClient, maxTopicSize, redisConfig.getEnvNamespace());
  }

  public RedisProducer(String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize, String producerName,
      String envNamespace, RedisEventMetricPublisher redisEventMetricPublisher) {
    super(topicName, producerName);
    initProducer(topicName, redissonClient, maxTopicSize, envNamespace);
    this.redisEventMetricPublisher = redisEventMetricPublisher;
  }

  public RedisProducer(String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize, String producerName,
      String envNamespace) {
    super(topicName, producerName);
    initProducer(topicName, redissonClient, maxTopicSize, envNamespace);
  }

  private void initProducer(
      String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize, String envNamespace) {
    this.maxTopicSize = maxTopicSize;
    this.redissonClient = redissonClient;
    this.stream = RedisUtils.getStream(topicName, redissonClient, envNamespace);
    RetryConfig retryConfig =
        RetryConfig.custom().intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 1.5)).maxAttempts(6).build();

    this.retry = Retry.of("redisProducer:" + topicName, retryConfig);
  }

  @Override
  public String send(Message message) {
    return handleMessage(message);
  }

  private String sendInternal(Message message) {
    Map<String, String> redisData = new HashMap<>(message.getMetadataMap());
    redisData.put(REDIS_STREAM_INTERNAL_KEY, Base64.getEncoder().encodeToString(message.getData().toByteArray()));
    populateOtherProducerSpecificData(redisData);

    StreamMessageId messageId = stream.addAll(redisData, maxTopicSize, false);
    addMonitoring(message);
    redisData.remove(REDIS_STREAM_INTERNAL_KEY);
    log.info("Events framework message inserted - messageId: {}, metaData: {}", messageId, redisData);
    return messageId.toString();
  }

  protected void populateOtherProducerSpecificData(Map<String, String> redisData) {
    redisData.put(PRODUCER, this.getProducerName());
  }

  private String handleMessage(Message message) {
    Supplier<String> sendMessageSupplier = () -> sendInternal(message);

    Supplier<String> retryingSendMessage = Retry.decorateSupplier(retry, sendMessageSupplier);

    return Try.ofSupplier(retryingSendMessage)
        .recover(throwable -> {
          // Exhausted exponential backoff to try operating on redis
          throw new EventsFrameworkDownException(throwable.getMessage());
        })
        .get();
  }

  @Override
  public void shutdown() {
    redissonClient.shutdown();
  }

  public static RedisProducer of(
      String topicName, @NotNull RedisConfig redisConfig, int maxTopicLength, String producerName) {
    return new RedisProducer(topicName, redisConfig, maxTopicLength, producerName);
  }

  public static RedisProducer of(String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize,
      String producerName, String envNamespace) {
    return new RedisProducer(topicName, redissonClient, maxTopicSize, producerName, envNamespace);
  }

  private void waitForRedisToComeUp() {
    try {
      TimeUnit.MILLISECONDS.sleep(500);
    } catch (InterruptedException e) {
      log.error("Polling to redis was interrupted, shutting down producer", e);
      shutdown();
    }
  }

  private void addMonitoring(Message message) {
    try {
      redisEventMetricPublisher.sendMetricWithEventContext(
          RedisEventMetricDTOMapper.prepareRedisEventMetricDTO(message, getTopicName()), REDIS_PUSH_EVENT_METRIC);
    } catch (Exception ex) {
      log.warn("Error while sending metrics for redis producer events :", ex);
    }
  }
}
