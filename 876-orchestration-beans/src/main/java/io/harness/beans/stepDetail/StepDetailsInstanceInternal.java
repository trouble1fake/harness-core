package io.harness.beans.stepDetail;

import io.harness.pms.data.stepdetails.PmsStepDetails;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StepDetailsInstanceInternal {
  String name;
  PmsStepDetails stepDetails;
}
