package io.harness.steps.policy.custom;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.policy.PolicyStepConstants.CUSTOM_POLICY_STEP_TYPE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.policy.PolicySpec;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonTypeName(CUSTOM_POLICY_STEP_TYPE)
@RecasterAlias("io.harness.steps.policy.custom.CustomPolicyStepSpec")
public class CustomPolicyStepSpec implements PolicySpec {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> payload;

  @Override
  public String getType() {
    return CUSTOM_POLICY_STEP_TYPE;
  }
}
