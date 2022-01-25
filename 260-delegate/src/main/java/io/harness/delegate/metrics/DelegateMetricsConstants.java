/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.metrics;

import static io.harness.delegate.metrics.DelegateMetricDetails.create;

import com.google.common.collect.Maps;
import java.util.Map;

public class DelegateMetricsConstants {
  public static Map<String, DelegateMetricDetails> DELEGATE_AGENT_METRIC_MAP = Maps.newHashMap();

  public static final String CURRENTLY_ACQUIRING_TASKS = "acquiring_tasks";
  public static final String CURRENTLY_VALIDATING_TASKS = "validating_tasks";
  public static final String CURRENTLY_EXECUTING_TASKS = "executing_tasks";
  public static final String CURRENTLY_VALIDATING_FUTURES = "validating_futures";
  public static final String CURRENTLY_EXECUTING_FUTURES = "executing_futures";
  public static final String TASK_EXECUTION_TIME = "task_execution_time";
  public static final String TASK_ACQUIRE_TIME = "task_acquire_time";

  static {
    put(CURRENTLY_ACQUIRING_TASKS, create("Number of the tasks being acquired", "delegate_name"));
    put(CURRENTLY_VALIDATING_TASKS, create("Number of the tasks being validated", "delegate_name"));
    put(CURRENTLY_EXECUTING_TASKS, create("Number of the tasks being executed", "delegate_name"));
    put(CURRENTLY_VALIDATING_FUTURES, create("Number of the java runnable futures being validated", "delegate_name"));
    put(CURRENTLY_EXECUTING_FUTURES, create("Number of the java runnable futures being executed", "delegate_name"));
    put(TASK_EXECUTION_TIME, create("Time needed to execute the task ", "delegate_name", "task_type"));
    put(TASK_ACQUIRE_TIME, create("Time needed to acquire the task ", "delegate_name", "task_type"));
  }

  private static void put(String metricName, DelegateMetricDetails metricDetails) {
    DELEGATE_AGENT_METRIC_MAP.put(metricName, metricDetails);
  }
}
