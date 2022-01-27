/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.metrics;

import static io.harness.delegate.metrics.DelegateMetricsConstants.CURRENTLY_ACQUIRING_TASKS;
import static io.harness.delegate.metrics.DelegateMetricsConstants.CURRENTLY_EXECUTING_FUTURES;
import static io.harness.delegate.metrics.DelegateMetricsConstants.CURRENTLY_EXECUTING_TASKS;
import static io.harness.delegate.metrics.DelegateMetricsConstants.CURRENTLY_VALIDATING_TASKS;
import static io.harness.delegate.metrics.DelegateMetricsConstants.DELEGATE_AGENT_METRIC_MAP;

import io.harness.delegate.service.DelegateAgentService;
import io.harness.metrics.HarnessMetricRegistry;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DelegateAgentMetrics {
  private static final int METRICS_POLL_DELAY_SECONDS = 30;
  @Inject @Named("delegateAgentMetricsExecutor") protected ScheduledExecutorService executorService;

  @Inject private HarnessMetricRegistry metricRegistry;

  @Inject private DelegateAgentService delegateService;

  public void scheduleDelegateAgentMetricsPoll() {
    executorService.scheduleWithFixedDelay(this::recordMetric, 30, METRICS_POLL_DELAY_SECONDS, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  void recordMetric() {
    try {
      TaskExecutionMetrics taskExecutionMetrics = delegateService.getTaskExecutionMetrics();
      String delegateName = taskExecutionMetrics.getDelegateName();
      metricRegistry.recordGaugeValue(
          CURRENTLY_ACQUIRING_TASKS, new String[] {delegateName}, taskExecutionMetrics.getCurrentlyAcquiringTasks());
      metricRegistry.recordGaugeValue(
          CURRENTLY_VALIDATING_TASKS, new String[] {delegateName}, taskExecutionMetrics.getCurrentlyValidatingTasks());
      metricRegistry.recordGaugeValue(
          CURRENTLY_EXECUTING_TASKS, new String[] {delegateName}, taskExecutionMetrics.getCurrentlyExecutingTasks());
      metricRegistry.recordGaugeValue(CURRENTLY_EXECUTING_FUTURES, new String[] {delegateName},
          taskExecutionMetrics.getCurrentlyExecutingFutures());

    } catch (Exception e) {
      log.error("Could not record metrics.", e);
    }
  }

  public void registerDelegateMetrics() {
    DELEGATE_AGENT_METRIC_MAP.forEach((metricName, metricDetails)
                                          -> metricRegistry.registerGaugeMetric(
                                              metricName, metricDetails.getLabels(), metricDetails.getDescription()));
  }
}
