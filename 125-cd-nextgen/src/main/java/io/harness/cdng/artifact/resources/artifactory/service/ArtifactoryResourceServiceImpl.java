/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;
import io.harness.cdng.artifact.resources.artifactory.mappers.ArtifactoryResourceMapper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
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
public class ArtifactoryResourceServiceImpl implements ArtifactoryResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public ArtifactoryResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public ArtifactoryResponseDTO getBuildDetails(IdentifierRef artifactoryConnectorRef, String repository,
      String imagePath, String repositoryFormat, String dockerRepositoryServer, String orgIdentifier,
      String projectIdentifier) {
    ArtifactoryConnectorDTO connector = getConnector(artifactoryConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(artifactoryConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    ArtifactoryArtifactDelegateRequest artifactoryRequest =
        ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(repository, imagePath, repositoryFormat,
            dockerRepositoryServer, null, null, null, null, connector, encryptionDetails,
            ArtifactSourceType.ARTIFACTORY_REGISTRY);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(artifactoryRequest,
          ArtifactTaskType.GET_BUILDS, baseNGAccess, "Artifactory Artifact Get Builds task failure due to error");
      return getArtifactoryResponseDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_ARTIFACTORY_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public ArtifactoryBuildDetailsDTO getSuccessfulBuild(IdentifierRef artifactoryConnectorRef, String repository,
      String imagePath, String repositoryFormat, String dockerRepositoryServer,
      ArtifactoryRequestDTO artifactoryRequestDTO, String orgIdentifier, String projectIdentifier) {
    ArtifactoryConnectorDTO connector = getConnector(artifactoryConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(artifactoryConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    ArtifactoryArtifactDelegateRequest artifactoryRequest =
        ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(repository, imagePath, repositoryFormat,
            dockerRepositoryServer, artifactoryRequestDTO.getTag(), artifactoryRequestDTO.getTagRegex(),
            artifactoryRequestDTO.getTagsList(), null, connector, encryptionDetails,
            ArtifactSourceType.ARTIFACTORY_REGISTRY);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(artifactoryRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
            "Artifactory Get last successful build task failure due to error");
    ArtifactoryResponseDTO artifactoryResponseDTO = getArtifactoryResponseDTO(artifactTaskExecutionResponse);
    if (artifactoryResponseDTO.getBuildDetailsList().size() != 1) {
      throw new ArtifactServerException("Artifactory get last successful build task failure.");
    }
    return artifactoryResponseDTO.getBuildDetailsList().get(0);
  }

  @Override
  public boolean validateArtifactServer(
      IdentifierRef artifactoryConnectorRef, String orgIdentifier, String projectIdentifier) {
    ArtifactoryConnectorDTO connector = getConnector(artifactoryConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(artifactoryConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    ArtifactoryArtifactDelegateRequest artifactoryRequest =
        ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(null, null, null, null, null, null, null,
            null, connector, encryptionDetails, ArtifactSourceType.ARTIFACTORY_REGISTRY);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(artifactoryRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SERVER, baseNGAccess,
            "Artifactory validate artifact server task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  private ArtifactoryConnectorDTO getConnector(IdentifierRef artifactoryConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(artifactoryConnectorRef.getAccountIdentifier(), artifactoryConnectorRef.getOrgIdentifier(),
            artifactoryConnectorRef.getProjectIdentifier(), artifactoryConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAArtifactoryConnector(connectorDTO.get())) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier : [%s] with scope: [%s]",
              artifactoryConnectorRef.getIdentifier(), artifactoryConnectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (ArtifactoryConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAArtifactoryConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.ARTIFACTORY == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull ArtifactoryConnectorDTO artifactoryConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (artifactoryConnectorDTO.getAuth() != null && artifactoryConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, artifactoryConnectorDTO.getAuth().getCredentials());
    }
    return new ArrayList<>();
  }

  private ArtifactTaskExecutionResponse executeSyncTask(ArtifactoryArtifactDelegateRequest artifactoryRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, artifactoryRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, ArtifactoryArtifactDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.ARTIFACTORY_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
            .taskSetupAbstraction("ng", "true")
            .taskSetupAbstraction("owner", ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier())
            .taskSetupAbstraction("projectIdentifier", ngAccess.getProjectIdentifier())
            .taskSelectors(delegateRequest.getArtifactoryConnectorDTO().getDelegateSelectors())
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
        throw new ArtifactServerException("Unexpected error during authentication to artifactory server "
                + remoteMethodReturnValueData.getReturnValue(),
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

  private ArtifactoryResponseDTO getArtifactoryResponseDTO(
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<ArtifactoryArtifactDelegateResponse> artifactoryArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (ArtifactoryArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return ArtifactoryResourceMapper.toArtifactoryResponse(artifactoryArtifactDelegateResponses);
  }
}
