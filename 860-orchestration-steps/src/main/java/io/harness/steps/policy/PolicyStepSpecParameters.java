package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@OwnedBy(PIPELINE)
@Value
public class PolicyStepSpecParameters extends PolicyStepBase implements SpecParameters {
  @Builder
  public PolicyStepSpecParameters(
      @NonNull ParameterField<List<String>> policySets, @NonNull String type, PolicySpec policySpec) {
    super(policySets, type, policySpec);
  }
}
