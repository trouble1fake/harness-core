/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.services;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorCatalogueResponseDTO;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.git.model.ChangeType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

@OwnedBy(DX)
public interface ConnectorCrudService {
  Page<ConnectorResponseDTO> list(int page, int size, String accountIdentifier,
      ConnectorFilterPropertiesDTO filterProperties, String orgIdentifier, String projectIdentifier,
      String filterIdentifier, String searchTerm, Boolean includeAllConnectorsAccessibleAtScope,
      Boolean getDistinctFromBranches);

  Page<ConnectorResponseDTO> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, ConnectorType type, ConnectorCategory category,
      ConnectorCategory sourceCategory);

  Optional<ConnectorResponseDTO> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier);

  Optional<ConnectorResponseDTO> getByName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String name, boolean isDeletedAllowed);

  Optional<ConnectorResponseDTO> getFromBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, String repo, String branch);

  ConnectorResponseDTO create(ConnectorDTO connector, String accountIdentifier);

  ConnectorResponseDTO create(ConnectorDTO connector, String accountIdentifier, ChangeType gitChangeType);

  ConnectorResponseDTO update(ConnectorDTO connectorRequestDTO, String accountIdentifier);

  ConnectorResponseDTO update(ConnectorDTO connectorRequestDTO, String accountIdentifier, ChangeType gitChangeType);

  boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier);

  boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier,
      ChangeType changeType);

  ConnectorCatalogueResponseDTO getConnectorCatalogue();

  void updateConnectorEntityWithPerpetualtaskId(String accountIdentifier, String connectorOrgIdentifier,
      String connectorProjectIdentifier, String connectorIdentifier, String perpetualTaskId);

  void updateActivityDetailsInTheConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, ConnectorValidationResult connectorValidationResult, Long activityTime);

  List<ConnectorResponseDTO> listbyFQN(String accountIdentifier, List<String> connectorsFQN);

  long count(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  void deleteBatch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifiersList);
}
