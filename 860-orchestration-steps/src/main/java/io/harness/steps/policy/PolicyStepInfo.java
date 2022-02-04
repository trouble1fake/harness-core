package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PIPELINE)
@NoArgsConstructor
@Data
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
    return StepType.newBuilder().setType(StepSpecTypeConstants.POLICY_STEP).setStepCategory(StepCategory.STEP).build();
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
