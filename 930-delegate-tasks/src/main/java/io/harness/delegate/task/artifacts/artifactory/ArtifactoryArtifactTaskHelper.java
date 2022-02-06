/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.context.MdcGlobalContextData;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.exception.runtime.NexusServerRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.manage.GlobalContextManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class ArtifactoryArtifactTaskHelper {
  private final ArtifactoryArtifactTaskHandler artifactoryArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    NexusArtifactDelegateRequest attributes = (NexusArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    String registryUrl = attributes.getNexusConnectorDTO().getNexusServerUrl();
    artifactoryArtifactTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    try {
      switch (artifactTaskParameters.getArtifactTaskType()) {
        case GET_LAST_SUCCESSFUL_BUILD:
          saveLogs(executionLogCallback, "Fetching Artifact details");
          artifactTaskResponse =
              getSuccessTaskResponse(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(attributes));
          NexusArtifactDelegateResponse nexusArtifactDelegateResponse =
              (NexusArtifactDelegateResponse) (artifactTaskResponse.getArtifactTaskExecutionResponse()
                                                   .getArtifactDelegateResponses()
                                                   .size()
                          != 0
                      ? artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0)
                      : NexusArtifactDelegateResponse.builder().build());
          saveLogs(executionLogCallback,
              "Fetched Artifact details \n  type: Artifactory Artifact\n  imagePath: "
                  + nexusArtifactDelegateResponse.getImagePath()
                  + "\n  tag: " + nexusArtifactDelegateResponse.getTag());
          break;
        case GET_BUILDS:
          saveLogs(executionLogCallback, "Fetching artifact details");
          artifactTaskResponse = getSuccessTaskResponse(artifactoryArtifactTaskHandler.getBuilds(attributes));
          saveLogs(executionLogCallback,
              "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                  + " artifacts");
          break;
        case GET_LABELS:
          saveLogs(executionLogCallback, "Fetching labels");
          artifactTaskResponse = getSuccessTaskResponse(artifactoryArtifactTaskHandler.getLabels(attributes));
          saveLogs(executionLogCallback,
              "Fetched labels: "
                  + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().toString());
          break;
        case VALIDATE_ARTIFACT_SERVER:
          saveLogs(executionLogCallback, "Validating  Artifact Server");
          artifactTaskResponse =
              getSuccessTaskResponse(artifactoryArtifactTaskHandler.validateArtifactServer(attributes));
          saveLogs(executionLogCallback, "validated artifact server: " + registryUrl);
          break;
        case VALIDATE_ARTIFACT_SOURCE:
          saveLogs(executionLogCallback, "Validating Artifact Source");
          artifactTaskResponse =
              getSuccessTaskResponse(artifactoryArtifactTaskHandler.validateArtifactImage(attributes));
          saveLogs(executionLogCallback,
              "Artifact Source is valid: " + registryUrl + (registryUrl.endsWith("/") ? "" : "/")
                  + attributes.getImagePath());
          break;
        default:
          saveLogs(executionLogCallback,
              "No corresponding Artifactory artifact task type [{}]: " + artifactTaskParameters.toString());
          log.error("No corresponding Artifactory artifact task type [{}]", artifactTaskParameters.toString());
          return ArtifactTaskResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .errorMessage("There is no Artifactory artifact task type impl defined for - "
                  + artifactTaskParameters.getArtifactTaskType().name())
              .errorCode(ErrorCode.INVALID_ARGUMENT)
              .build();
      }
    } catch (NexusServerRuntimeException ex) {
      if (GlobalContextManager.get(MdcGlobalContextData.MDC_ID) == null) {
        MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(new HashMap<>()).build();
        GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      }
      ((MdcGlobalContextData) GlobalContextManager.get(MdcGlobalContextData.MDC_ID))
          .getMap()
          .put(ExceptionMetadataKeys.CONNECTOR.name(), attributes.getConnectorRef());
      throw ex;
    }
    return artifactTaskResponse;
  }

  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }
  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
}
