package io.harness.cdng.artifact.resources.artifactory.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.delegate.beans.artifactory.ArtifactoryTaskParams.TaskType.FETCH_BUILDS;
import static io.harness.delegate.beans.artifactory.ArtifactoryTaskParams.TaskType.FETCH_REPOSITORIES;
import static io.harness.eraro.ErrorCode.ARTIFACT_SERVER_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.beans.TaskType.NG_ARTIFACTORY_TASK;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRepoDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.mappers.ArtifactoryResourceMapper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchBuildsResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchRepositoriesResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskParams;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

@Singleton
public class ArtifactoryResourceServiceImpl implements ArtifactoryResourceService {
  private final ConnectorService connectorService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final SecretManagerClientService secretManagerClientService;

  @VisibleForTesting static final int timeoutInSecs = 30;
  private static final String FAILED_TO_FETCH_REPOSITORIES = "Failed to fetch repositories";
  private static final String FAILED_TO_FETCH_ARTIFACTS = "Failed to fetch artifacts";

  @Inject
  public ArtifactoryResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      DelegateGrpcClientWrapper delegateGrpcClientWrapper, SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public ArtifactoryRepoDetailsDTO getRepositories(
      String repositoryType, IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()
        || ConnectorType.ARTIFACTORY != connectorDTO.get().getConnector().getConnectorType()) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectors.getConnectorConfig();

    DelegateResponseData delegateResponseData = getResponseData(baseNGAccess,
        ArtifactoryTaskParams.builder()
            .artifactoryConnectorDTO(artifactoryConnectorDTO)
            .encryptedDataDetails(getEncryptionDetails(artifactoryConnectorDTO, baseNGAccess))
            .taskType(FETCH_REPOSITORIES)
            .repoType(repositoryType)
            .build(),
        NG_ARTIFACTORY_TASK.name());

    return ArtifactoryRepoDetailsDTO.builder()
        .repositories(getArtifactoryFetchRepositoriesResponse(delegateResponseData).getRepositories())
        .build();
  }

  @Override
  public List<ArtifactoryArtifactBuildDetailsDTO> getBuildDetails(String repositoryName, String filePath,
      int maxVersions, IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()
        || ConnectorType.ARTIFACTORY != connectorDTO.get().getConnector().getConnectorType()) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectors.getConnectorConfig();
    ArtifactoryTaskParams artifactoryTaskParams =
        ArtifactoryTaskParams.builder()
            .artifactoryConnectorDTO(artifactoryConnectorDTO)
            .encryptedDataDetails(getEncryptionDetails(artifactoryConnectorDTO, baseNGAccess))
            .taskType(FETCH_BUILDS)
            .repoName(repositoryName)
            .filePath(filePath)
            .maxVersions(maxVersions)
            .build();

    DelegateResponseData delegateResponseData =
        getResponseData(baseNGAccess, artifactoryTaskParams, NG_ARTIFACTORY_TASK.name());

    return ArtifactoryResourceMapper.toArtifactoryArtifactBuildDetailsDTO(
        getArtifactoryFetchBuildsResponse(delegateResponseData).getBuildDetails());
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess, TaskParameters taskParameters, String taskType) {
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(taskType)
            .taskParameters(taskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstraction(SetupAbstractionKeys.orgIdentifier, ngAccess.getOrgIdentifier())
            .taskSetupAbstraction(SetupAbstractionKeys.projectIdentifier, ngAccess.getProjectIdentifier())
            .build();
    return delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
  }

  private ArtifactoryFetchRepositoriesResponse getArtifactoryFetchRepositoriesResponse(
      DelegateResponseData responseData) {
    handleErrorResponseData(responseData, FAILED_TO_FETCH_REPOSITORIES);

    ArtifactoryFetchRepositoriesResponse taskResponse = (ArtifactoryFetchRepositoriesResponse) responseData;
    if (taskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactoryServerException(FAILED_TO_FETCH_REPOSITORIES, ARTIFACT_SERVER_ERROR, USER);
    }
    return taskResponse;
  }

  private ArtifactoryFetchBuildsResponse getArtifactoryFetchBuildsResponse(DelegateResponseData responseData) {
    handleErrorResponseData(responseData, FAILED_TO_FETCH_ARTIFACTS);

    ArtifactoryFetchBuildsResponse taskResponse = (ArtifactoryFetchBuildsResponse) responseData;
    if (taskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactoryServerException(FAILED_TO_FETCH_ARTIFACTS, ARTIFACT_SERVER_ERROR, USER);
    }
    return taskResponse;
  }

  private void handleErrorResponseData(DelegateResponseData responseData, String errorMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactoryServerException(
          errorMessage + " - " + errorNotifyResponseData.getErrorMessage(), ARTIFACT_SERVER_ERROR, USER);
    }
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
    if (artifactoryConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, artifactoryConnectorDTO.getAuth().getCredentials());
    }
    return Collections.emptyList();
  }
}
