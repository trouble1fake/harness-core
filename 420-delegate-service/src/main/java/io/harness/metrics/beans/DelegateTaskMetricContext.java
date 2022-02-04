/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.metrics.beans;

import io.harness.metrics.AutoMetricContext;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class DelegateTaskMetricContext extends AutoMetricContext {
  public DelegateTaskMetricContext(String accountId, boolean ng, String taskType, boolean async) {
    put("accountId", accountId);
    put("ng", String.valueOf(ng));
    put("taskType", taskType);
    put("async", String.valueOf(async));
  }

  public DelegateTaskMetricContext(String accountId) {
    put("accountId", accountId);
  }
}
