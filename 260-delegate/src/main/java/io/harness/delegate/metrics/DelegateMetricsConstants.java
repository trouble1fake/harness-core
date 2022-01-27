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
  public static final String CURRENTLY_EXECUTING_FUTURES = "executing_futures";
  public static final String TASK_EXECUTION_TIME = "task_execution_time";
  public static final String TASK_ACQUIRE_TIME = "task_acquire_time";

  private static final String DELEGATE_NAME_LABEL = "delegate_name";
  private static final String TASK_TYPE_LABEL = "task_type";

  static {
    put(CURRENTLY_ACQUIRING_TASKS, create("Number of the tasks being acquired", DELEGATE_NAME_LABEL));
    put(CURRENTLY_VALIDATING_TASKS, create("Number of the tasks being validated", DELEGATE_NAME_LABEL));
    put(CURRENTLY_EXECUTING_TASKS, create("Number of the tasks being executed", DELEGATE_NAME_LABEL));
    put(CURRENTLY_EXECUTING_FUTURES, create("Number of the java runnable futures being executed", DELEGATE_NAME_LABEL));
    put(TASK_EXECUTION_TIME, create("Time needed to execute the task ", DELEGATE_NAME_LABEL, TASK_TYPE_LABEL));
    put(TASK_ACQUIRE_TIME, create("Time needed to acquire the task ", DELEGATE_NAME_LABEL));
  }

  private static void put(String metricName, DelegateMetricDetails metricDetails) {
    DELEGATE_AGENT_METRIC_MAP.put(metricName, metricDetails);
  }
}
