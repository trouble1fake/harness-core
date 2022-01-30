/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.nexus.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.mappers.NexusResourceMapper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class NexusResourceServiceImpl implements NexusResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public NexusResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public NexusResponseDTO getBuildDetails(IdentifierRef nexusConnectorRef, String repositoryName,
      Integer repositoryPort, String imagePath, String repositoryFormat, String dockerRepositoryServer,
      String orgIdentifier, String projectIdentifier) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest = ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(
        repositoryName, repositoryPort, imagePath, repositoryFormat, dockerRepositoryServer, null, null, null, null,
        connector, encryptionDetails, ArtifactSourceType.NEXUS_REGISTRY);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(nexusRequest,
          ArtifactTaskType.GET_BUILDS, baseNGAccess, "Nexus Artifact Get Builds task failure due to error");
      return getNexusResponseDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_NEXUS_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public NexusBuildDetailsDTO getSuccessfulBuild(IdentifierRef nexusConnectorRef, String repositoryName,
      Integer repositoryPort, String imagePath, String repositoryFormat, String dockerRepositoryServer,
      NexusRequestDTO nexusRequestDTO, String orgIdentifier, String projectIdentifier) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest =
        ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(repositoryName, repositoryPort, imagePath,
            repositoryFormat, dockerRepositoryServer, nexusRequestDTO.getTag(), nexusRequestDTO.getTagRegex(),
            nexusRequestDTO.getTagsList(), null, connector, encryptionDetails, ArtifactSourceType.NEXUS_REGISTRY);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(nexusRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
            "Nexus Get last successful build task failure due to error");
    NexusResponseDTO nexusResponseDTO = getNexusResponseDTO(artifactTaskExecutionResponse);
    if (nexusResponseDTO.getBuildDetailsList().size() != 1) {
      throw new ArtifactServerException("Nexus get last successful build task failure.");
    }
    return nexusResponseDTO.getBuildDetailsList().get(0);
  }

  @Override
  public boolean validateArtifactServer(
      IdentifierRef nexusConnectorRef, String orgIdentifier, String projectIdentifier) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest = ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(null, null,
        null, null, null, null, null, null, null, connector, encryptionDetails, ArtifactSourceType.NEXUS_REGISTRY);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(nexusRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SERVER, baseNGAccess,
            "Nexus validate artifact server task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  private NexusConnectorDTO getConnector(IdentifierRef nexusConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(nexusConnectorRef.getAccountIdentifier(), nexusConnectorRef.getOrgIdentifier(),
            nexusConnectorRef.getProjectIdentifier(), nexusConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isANexusConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            nexusConnectorRef.getIdentifier(), nexusConnectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (NexusConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isANexusConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.NEXUS == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull NexusConnectorDTO nexusConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (nexusConnectorDTO.getAuth() != null && nexusConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, nexusConnectorDTO.getAuth().getCredentials());
    }
    return new ArrayList<>();
  }

  private ArtifactTaskExecutionResponse executeSyncTask(NexusArtifactDelegateRequest nexusRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, nexusRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, NexusArtifactDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.NEXUS_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
            .taskSetupAbstraction("ng", "true")
            .taskSetupAbstraction("owner", ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier())
            .taskSetupAbstraction("projectIdentifier", ngAccess.getProjectIdentifier())
            .taskSelectors(delegateRequest.getNexusConnectorDTO().getDelegateSelectors())
            .build();
    return delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new ArtifactServerException(
            "Unexpected error during authentication to nexus server " + remoteMethodReturnValueData.getReturnValue(),
            WingsException.USER);
      }
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private NexusResponseDTO getNexusResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<NexusArtifactDelegateResponse> nexusArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (NexusArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return NexusResourceMapper.toNexusResponse(nexusArtifactDelegateResponses);
  }
}
