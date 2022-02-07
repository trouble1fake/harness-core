package io.harness.metrics.beans;

import io.harness.metrics.AutoMetricContext;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class PerpetualTaskMetricContext extends AutoMetricContext {
  public PerpetualTaskMetricContext(String accountId, String perpetualTaskType) {
    put("accountId", accountId);
    put("perpetualTaskType", perpetualTaskType);
  }
}
