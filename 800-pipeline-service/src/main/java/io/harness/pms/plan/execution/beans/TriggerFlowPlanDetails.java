package io.harness.pms.plan.execution.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.triggers.TriggerPayload;

import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class TriggerFlowPlanDetails {
  TriggerPayload triggerPayload;
  String payload;
  ExecutionTriggerInfo triggerInfo;
}
