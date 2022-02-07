package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.policy.step.PolicyStep;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.POLICY_STEP)
@SimpleVisitorHelper(helperClass = PolicyStepInfoVisitorHelper.class)
@TypeAlias("policyStepInfo")
@RecasterAlias("io.harness.steps.policy.PolicyStepInfo")
public class PolicyStepInfo extends PolicyStepBase implements PMSStepInfo, Visitable {
  @Builder
  public PolicyStepInfo(ParameterField<List<String>> policySets, String type, PolicySpec policySpec) {
    super(policySets, type, policySpec);
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    // todo(@NamanVerma): Move this to PolicyStep when it is added
    return PolicyStep.STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return PolicyStepSpecParameters.builder().policySets(policySets).type(type).policySpec(policySpec).build();
  }
}
