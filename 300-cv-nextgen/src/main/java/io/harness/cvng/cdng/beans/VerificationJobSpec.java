/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobBuilder;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "VerificationJobSpecKeys")
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@OwnedBy(HarnessTeam.CV)
@SuperBuilder
@NoArgsConstructor
public abstract class VerificationJobSpec {
  @ApiModelProperty(hidden = true) public abstract String getType();
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH,
      value = "You can put expression <+serviceConfig.artifacts.primary.tag> to resolve primary tag")
  ParameterField<String> deploymentTag;
  @NotNull
  @ApiModelProperty(
      dataType = SwaggerConstants.STRING_CLASSPATH, value = "Format example: 5m, 30m, please put multiple of 5")
  ParameterField<String> duration;
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, value = "Possible values: [Low, Medium, High]")
  ParameterField<String> sensitivity;

  @ApiModelProperty(hidden = true)
  public VerificationJobBuilder getVerificationJobBuilder() {
    VerificationJobBuilder verificationJobBuilder = verificationJobBuilder();
    return verificationJobBuilder.duration(RuntimeParameter.builder().value(duration.getValue()).build());
  }
  @ApiModelProperty(hidden = true) protected abstract VerificationJobBuilder verificationJobBuilder();
  public void validate() {
    validateDuration();
    validateParams();
  }

  private void validateDuration() {
    if (duration.getValue() != null) {
      Preconditions.checkState(!duration.getValue().isEmpty(), "Value can not be empty");
      if (duration.getValue().charAt(duration.getValue().length() - 1) != 'm') {
        throw new IllegalArgumentException("duration should end with m, ex: 5m, 10m etc.");
      }
      String number = duration.getValue().substring(0, duration.getValue().length() - 1);
      try {
        Integer.parseInt(number);
      } catch (NumberFormatException numberFormatException) {
        throw new IllegalArgumentException(
            "can not parse duration please check format for duration., ex: 5m, 10m etc.", numberFormatException);
      }
    }
  }
  protected abstract void validateParams();
}
