/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.variable.mappers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.variable.VariableDTO;
import io.harness.variable.VariableInfoDTO;
import io.harness.variable.VariableResponseDTO;
import io.harness.variable.entities.Variable;
import io.harness.encryption.Scope;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PL)
public class VariableMapper {
    private Map<String, VariableDTOToEntityMapper> variableDTOToEntityMapperMap;
    private Map<String, VariableEntityToDTOMapper> variableEntityToDTOMapperMap;

    public Variable toVariable(VariableDTO variableRequestDTO, String accountIdentifier) {
        VariableInfoDTO variableInfo = variableRequestDTO.getVariableInfo();
        VariableDTOToEntityMapper variableDTOToEntityMapper =
                variableDTOToEntityMapperMap.get(variableInfo.getVariableType().toString());
        Variable variable = variableDTOToEntityMapper.toVariableEntity(variableInfo.getSpec());
        variable.setIdentifier(variableInfo.getIdentifier());
        variable.setName(variableInfo.getName());
        variable.setScope(getScopeFromVariableDTO(variableRequestDTO));
        variable.setAccountIdentifier(accountIdentifier);
        variable.setOrgIdentifier(variableInfo.getOrgIdentifier());
        variable.setProjectIdentifier(variableInfo.getProjectIdentifier());
        variable.setFullyQualifiedIdentifier(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
                variableInfo.getOrgIdentifier(), variableInfo.getProjectIdentifier(), variableInfo.getIdentifier()));
        variable.setTags(TagMapper.convertToList(variableInfo.getTags()));
        variable.setDescription(variableInfo.getDescription());
        variable.setType(variableInfo.getVariableType());
        variable.setEntityInvalid(false);
        variable.setYaml(null);
        return variable;
    }

    @VisibleForTesting
    Scope getScopeFromVariableDTO(VariableDTO variableRequestDTO) {
        VariableInfoDTO variableInfo = variableRequestDTO.getVariableInfo();
        if (isNotBlank(variableInfo.getProjectIdentifier())) {
            return Scope.PROJECT;
        }
        if (isNotBlank(variableInfo.getOrgIdentifier())) {
            return Scope.ORG;
        }
        return Scope.ACCOUNT;
    }

    private Long getTimeWhenTheVariableWasUpdated(Long timeWhenVariableIsLastUpdated, Long lastModifiedAt) {
        // todo @deepak: Remove this logic later, currently it handles the old records too where the new field
        // timeWhenVariableIsLastUpdated is not present
        if (timeWhenVariableIsLastUpdated == null) {
            return lastModifiedAt;
        }
        return timeWhenVariableIsLastUpdated;
    }
}
