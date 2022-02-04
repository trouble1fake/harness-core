package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.http.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.HTTP)
@TypeAlias("HttpStepNode")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.policy.PolicyStepNode")
public class PolicyStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.Policy;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  PolicyStepInfo policyStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.POLICY_STEP;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return policyStepInfo;
  }

  enum StepType {
    Policy(StepSpecTypeConstants.POLICY_STEP);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
