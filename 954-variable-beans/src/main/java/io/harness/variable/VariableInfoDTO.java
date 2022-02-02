/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.variable;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.inject.Inject;
import io.harness.VariableConstants;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.harness.delegate.beans.variable.ValidationType;
import io.harness.delegate.beans.variable.VariableType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotBlank;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(PL)
@Schema(name = "VariableInfo", description = "This has the Variable details defined in Harness")
public class VariableInfoDTO {

    @NotNull @NotBlank @NGEntityName @Schema(description = VariableConstants.VARIABLE_NAME) String name;
    @NotNull @NotBlank @EntityIdentifier @Schema(description = VariableConstants.VARIABLE_IDENTIFIER) String identifier;
    @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
    @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
    @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
    @Schema(description = NGCommonEntityConstants.TAGS) Map<String, String> tags;

    @NotNull
    @JsonProperty(VariableConstants.VARIABLE_TYPES)
    @Schema(description = VariableConstants.VARIABLE_TYPE)
    io.harness.delegate.beans.variable.VariableType variableType;

    @NotNull
    @Schema(description = VariableConstants.VARIABLE_SPEC)
    VariableSpecDTO spec;

    public void setOrgIdentifier(String orgIdentifier) {
        this.orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
    }

    public void setProjectIdentifier(String projectIdentifier) {
        this.projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
    }

    @Builder
    public VariableInfoDTO(String name, String identifier, String description, String orgIdentifier,
                           String projectIdentifier, Map<String, String> tags, VariableType variableType,
                           VariableSpecDTO spec) {
        this.name = name;
        this.identifier = identifier;
        this.description = description;
        this.orgIdentifier = isEmpty(orgIdentifier) ? null : orgIdentifier;
        this.projectIdentifier = isEmpty(projectIdentifier) ? null : projectIdentifier;
        this.tags = tags;
        this.variableType = variableType;
        this.spec = spec;
    }
}
