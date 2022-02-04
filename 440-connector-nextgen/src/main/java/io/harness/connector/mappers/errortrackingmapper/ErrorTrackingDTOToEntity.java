/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.errortrackingmapper;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.errortrackingconnector.ErrorTrackingConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.errortracking.ErrorTrackingConnectorDTO;
import io.harness.encryption.SecretRefHelper;

@OwnedBy(CV)
public class ErrorTrackingDTOToEntity
    implements ConnectorDTOToEntityMapper<ErrorTrackingConnectorDTO, ErrorTrackingConnector> {
  @Override
  public ErrorTrackingConnector toConnectorEntity(ErrorTrackingConnectorDTO connectorDTO) {
    return ErrorTrackingConnector.builder()
        .url(connectorDTO.getUrl())
        .sid(connectorDTO.getSid())
        .apiKeyRef(SecretRefHelper.getSecretConfigString(connectorDTO.getApiKeyRef()))
        .build();
  }
}
