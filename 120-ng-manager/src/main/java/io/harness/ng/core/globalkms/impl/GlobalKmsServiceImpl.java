/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.globalkms.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.helpers.GlobalSecretManagerUtils.GLOBAL_ACCOUNT_ID;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.globalkms.dto.ConnectorSecretResponseDTO;
import io.harness.ng.core.globalkms.services.GlobalKmsService;
import io.harness.ng.core.globalkms.services.NgConnectorManagerClientService;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.ws.rs.NotFoundException;

public class GlobalKmsServiceImpl implements GlobalKmsService {
  private final ConnectorService connectorService;
  private final SecretCrudService ngSecretService;
  private final NgConnectorManagerClientService ngConnectorManagerClientService;

  @Inject
  public GlobalKmsServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretCrudService ngSecretService, NgConnectorManagerClientService ngConnectorManagerClientService) {
    this.connectorService = connectorService;
    this.ngSecretService = ngSecretService;
    this.ngConnectorManagerClientService = ngConnectorManagerClientService;
  }

  @Override
  public ConnectorSecretResponseDTO updateGlobalKms(ConnectorDTO connector, SecretDTOV2 secret) {
    canUpdateGlobalKms(connector, secret);
    ConnectorResponseDTO existingConnector = getExistingGlobalKmsConnectorOrThrow(connector);
    SecretDTOV2 existingSecret = getGlobalKmsSecretOrThrow(secret);
    GcpKmsConnectorDTO connectorConfig = (GcpKmsConnectorDTO) connector.getConnectorInfo().getConnectorConfig();
    GcpKmsConnectorDTO existingConnectorConfig =
        (GcpKmsConnectorDTO) existingConnector.getConnector().getConnectorConfig();
    existingConnectorConfig.setKeyName(connectorConfig.getKeyName());
    existingConnectorConfig.setKeyRing(connectorConfig.getKeyRing());
    existingConnectorConfig.setProjectId(connectorConfig.getProjectId());
    existingConnectorConfig.setRegion(connectorConfig.getRegion());
    existingSecret.setSpec(secret.getSpec());
    SecretResponseWrapper secretResponse = ngSecretService.update(GLOBAL_ACCOUNT_ID, existingSecret.getOrgIdentifier(),
        existingSecret.getProjectIdentifier(), existingSecret.getIdentifier(), existingSecret);
    ConnectorResponseDTO connectorResponse = connectorService.update(
        ConnectorDTO.builder().connectorInfo(existingConnector.getConnector()).build(), GLOBAL_ACCOUNT_ID);
    return ConnectorSecretResponseDTO.builder()
        .connectorResponseDTO(connectorResponse)
        .secretResponseWrapper(secretResponse)
        .build();
  }

  private void canUpdateGlobalKms(ConnectorDTO connector, SecretDTOV2 secret) {
    UserPrincipal principal = getUserPrincipalOrThrow();
    checkForHarnessSupportUser(principal.getName());
    checkConnectorTypeAndCredentialsMatch(connector, secret);
    checkConnectorHasOnlyAccountScopeInfo(connector);
  }

  private UserPrincipal getUserPrincipalOrThrow() {
    GlobalContext globalContext = GlobalContextManager.obtainGlobalContext();
    if (globalContext == null || !(globalContext.get(PRINCIPAL_CONTEXT) instanceof PrincipalContextData)
        || !(((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal() instanceof UserPrincipal)) {
      throw new InvalidRequestException("Not authorized to update in current context");
    }
    return (UserPrincipal) ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
  }

  private void checkForHarnessSupportUser(String userId) {
    boolean isSupportUser = ngConnectorManagerClientService.isHarnessSupportUser(userId);
    if (!isSupportUser) {
      throw new InvalidRequestException("User is not authorized");
    }
  }

  private void checkConnectorTypeAndCredentialsMatch(ConnectorDTO connectorDTO, SecretDTOV2 secretDTO) {
    if (!HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connectorDTO.getConnectorInfo().getIdentifier())
        || !ConnectorType.GCP_KMS.equals(connectorDTO.getConnectorInfo().getConnectorType())) {
      throw new InvalidRequestException("Update operation not supported");
    }
    GcpKmsConnectorDTO gcpKmsConnectorDTO = (GcpKmsConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    if (!gcpKmsConnectorDTO.getCredentials().getIdentifier().equals(secretDTO.getIdentifier())) {
      throw new InvalidRequestException("Secret credential reference cannot be changed");
    }
    if (!gcpKmsConnectorDTO.getCredentials().getScope().equals(Scope.ACCOUNT)) {
      throw new InvalidRequestException("Invalid credential scope");
    }
  }

  private void checkConnectorHasOnlyAccountScopeInfo(ConnectorDTO connectorDTO) {
    if (null != connectorDTO.getConnectorInfo().getOrgIdentifier()
        || null != connectorDTO.getConnectorInfo().getProjectIdentifier()) {
      throw new InvalidRequestException("Global connector cannot have org/project identifier");
    }
  }

  private ConnectorResponseDTO getExistingGlobalKmsConnectorOrThrow(ConnectorDTO connector) {
    Optional<ConnectorResponseDTO> existingConnector =
        connectorService.get(GLOBAL_ACCOUNT_ID, null, null, connector.getConnectorInfo().getIdentifier());
    if (!existingConnector.isPresent()
        || !existingConnector.get().getConnector().getConnectorType().equals(ConnectorType.GCP_KMS)) {
      throw new NotFoundException(String.format("Global connector of type %s not found", ConnectorType.GCP_KMS));
    }
    return existingConnector.get();
  }

  private SecretDTOV2 getGlobalKmsSecretOrThrow(SecretDTOV2 secretDTO) {
    SecretResponseWrapper secret = ngSecretService
                                       .get(GLOBAL_ACCOUNT_ID, secretDTO.getOrgIdentifier(),
                                           secretDTO.getProjectIdentifier(), secretDTO.getIdentifier())
                                       .orElse(null);
    if (null == secret) {
      throw new InvalidRequestException(
          String.format("Secret with identifier %s does not exist in global scope", secretDTO.getIdentifier()));
    }
    return secret.getSecret();
  }
}
