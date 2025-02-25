/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.queue;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.persistence.HPersistence.returnOldOptions;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.String.format;

import io.harness.exception.UnexpectedException;
import io.harness.persistence.HPersistence;
import io.harness.queue.Queuable;
import io.harness.queue.Queuable.QueuableKeys;
import io.harness.queue.QueueConsumer;
import io.harness.queue.TopicUtils;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class MongoQueueConsumer<T extends Queuable> implements QueueConsumer<T> {
  private final Class<T> klass;
  @Setter private Duration heartbeat;
  List<String> topics;

  private Semaphore semaphore = new Semaphore(1);
  @Inject private HPersistence persistence;

  public MongoQueueConsumer(Class<T> klass, Duration heartbeat, List<List<String>> topicExpression) {
    Objects.requireNonNull(klass);
    this.klass = klass;
    this.heartbeat = heartbeat;
    this.topics = TopicUtils.resolveExpressionIntoListOfTopics(topicExpression);
  }

  @Override
  public T get(Duration wait, Duration poll) {
    long endTime = System.currentTimeMillis() + wait.toMillis();
    boolean acquired = false;
    try {
      acquired = semaphore.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
      if (acquired) {
        return getUnderLock(endTime, poll);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (acquired) {
        semaphore.release();
      }
    }
    return null;
  }

  private T getUnderLock(long endTime, Duration poll) {
    while (true) {
      final Date now = new Date();

      Query<T> query = createQuery()
                           .field(QueuableKeys.earliestGet)
                           .lessThanOrEq(now)
                           .order(Sort.ascending(QueuableKeys.earliestGet));

      UpdateOperations<T> updateOperations = persistence.createUpdateOperations(klass).set(
          QueuableKeys.earliestGet, new Date(now.getTime() + heartbeat().toMillis()));

      T message = HPersistence.retry(() -> persistence.findAndModify(query, updateOperations, returnOldOptions));
      if (message != null) {
        return message;
      }

      if (System.currentTimeMillis() >= endTime) {
        return null;
      }

      try {
        Thread.sleep(poll.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      } catch (final IllegalArgumentException ex) {
        poll = Duration.ofMillis(0);
      }
    }
  }

  @Override
  public void updateHeartbeat(T message) {
    Date earliestGet = new Date(System.currentTimeMillis() + heartbeat().toMillis());

    Query<T> query = persistence.createQuery(klass).filter(QueuableKeys.id, message.getId());
    UpdateOperations<T> updateOperations =
        persistence.createUpdateOperations(klass).set(QueuableKeys.earliestGet, earliestGet);

    if (persistence.findAndModify(query, updateOperations, returnOldOptions) != null) {
      message.setEarliestGet(earliestGet);
      return;
    }

    log.error("Update heartbeat failed for {}", message.getId());
  }

  @Override
  // This API is used only for testing, we do not need index for the running field. If you start using the
  // API in production, please consider adding such.
  public long count(final Filter filter) {
    final AdvancedDatastore datastore = persistence.getDatastore(klass);

    switch (filter) {
      case ALL:
        return datastore.getCount(klass);
      case RUNNING:
        return datastore.getCount(createQuery().field(QueuableKeys.earliestGet).greaterThan(new Date()));
      case NOT_RUNNING:
        return datastore.getCount(createQuery().field(QueuableKeys.earliestGet).lessThanOrEq(new Date()));
      default:
        unhandled(filter);
    }
    throw new UnexpectedException(format("Unknown filter type %s", filter));
  }

  @Override
  public void ack(final T message) {
    log.debug("Queue event consumer msg: [{}]", message);
    Objects.requireNonNull(message);
    persistence.delete(klass, message.getId());
  }

  @Override
  public void requeue(final String id, int retries) {
    requeue(id, retries, new Date());
  }

  @Override
  public void requeue(final String id, final int retries, final Date earliestGet) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(earliestGet);

    persistence.update(persistence.createQuery(klass, excludeAuthority).filter(QueuableKeys.id, id),
        persistence.createUpdateOperations(klass)
            .set(QueuableKeys.retries, retries)
            .set(QueuableKeys.earliestGet, earliestGet));
  }

  @Override
  public Duration heartbeat() {
    return heartbeat;
  }

  @Override
  public String getName() {
    return klass.getSimpleName();
  }

  private Query<T> createQuery() {
    final Query<T> query = persistence.createQuery(klass);
    if (isNotEmpty(topics)) {
      query.field(QueuableKeys.topic).in(topics);
    } else {
      query.field(QueuableKeys.topic).doesNotExist();
    }
    return query;
  }
}
