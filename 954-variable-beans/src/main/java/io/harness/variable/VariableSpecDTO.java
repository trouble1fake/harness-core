/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.variable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.name.Named;
import io.harness.VariableConstants;
import io.harness.delegate.beans.variable.ValidationType;
import io.harness.delegate.beans.variable.VariableType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import javax.validation.constraints.NotNull;
import java.util.Map;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

@Builder
public class VariableSpecDTO {
    @NotNull
    @JsonProperty(VariableConstants.VALIDATION_TYPE)
    @Schema(description = VariableConstants.VALIDATION_TYPE)
    io.harness.delegate.beans.variable.ValidationType validationType;

    @NotNull
    @JsonProperty(VariableConstants.VALIDATION_TYPE)
    @Schema(description = VariableConstants.VALIDATION_TYPE)
    VariableValidationSpecDTO validation;

    public VariableSpecDTO(ValidationType validationType, VariableValidationSpecDTO spec_val) {
        this.validationType = validationType;
        this.validation = spec_val;
    }
}
