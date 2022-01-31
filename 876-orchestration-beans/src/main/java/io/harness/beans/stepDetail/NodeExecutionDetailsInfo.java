package io.harness.beans.stepDetail;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.data.stepdetails.PmsStepDetails;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionDetailsInfo {
  String name;
  PmsStepDetails stepDetails;
}
