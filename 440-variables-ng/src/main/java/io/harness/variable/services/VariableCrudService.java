/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.variable.services;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.variable.VariableDTO;
import io.harness.variable.VariableFilterPropertiesDTO;
import io.harness.variable.VariableResponseDTO;
import io.harness.variable.VariableValidationResult;
import io.harness.delegate.beans.variable.VariableType;
import io.harness.git.model.ChangeType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

@OwnedBy(DX)
public interface VariableCrudService {
    Page<VariableResponseDTO> list(int page, int size, String accountIdentifier,
                                    VariableFilterPropertiesDTO filterProperties, String orgIdentifier, String projectIdentifier,
                                    String filterIdentifier, String searchTerm, Boolean includeAllVariablesAccessibleAtScope,
                                    Boolean getDistinctFromBranches);

    Page<VariableResponseDTO> list(int page, int size, String accountIdentifier, String orgIdentifier,
                                    String projectIdentifier, String searchTerm, VariableType type, VariableCategory category,
                                    VariableCategory sourceCategory);

    Optional<VariableResponseDTO> get(
            String accountIdentifier, String orgIdentifier, String projectIdentifier, String variableIdentifier);

    Optional<VariableResponseDTO> getByName(
            String accountIdentifier, String orgIdentifier, String projectIdentifier, String name, boolean isDeletedAllowed);

    Optional<VariableResponseDTO> getFromBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier,
                                                 String variableIdentifier, String repo, String branch);

    VariableResponseDTO create(VariableDTO variable, String accountIdentifier);

    VariableResponseDTO create(VariableDTO variable, String accountIdentifier, ChangeType gitChangeType);

    VariableResponseDTO update(VariableDTO variableRequestDTO, String accountIdentifier);

    VariableResponseDTO update(VariableDTO variableRequestDTO, String accountIdentifier, ChangeType gitChangeType);

    boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier, String variableIdentifier);

    VariableCatalogueResponseDTO getVariableCatalogue();

    void updateVariableEntityWithPerpetualtaskId(String accountIdentifier, String variableOrgIdentifier,
                                                  String variableProjectIdentifier, String variableIdentifier, String perpetualTaskId);

    void updateActivityDetailsInTheVariable(String accountIdentifier, String orgIdentifier, String projectIdentifier,
                                             String identifier, VariableValidationResult variableValidationResult, Long activityTime);

    List<VariableResponseDTO> listbyFQN(String accountIdentifier, List<String> variablesFQN);

    long count(String accountIdentifier, String orgIdentifier, String projectIdentifier);

    void deleteBatch(
            String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> variableIdentifiersList);
}
