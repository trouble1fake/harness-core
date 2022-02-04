package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
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
