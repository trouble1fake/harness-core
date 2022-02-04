/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package ci.pipeline.execution;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.scm.GitConnectionType.ACCOUNT;
import static io.harness.govern.Switch.unhandled;
import static io.harness.pms.execution.utils.StatusUtils.isFinalStatus;
import static io.harness.steps.StepUtils.buildAbstractions;

import io.harness.PipelineUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.task.ci.CIBuildStatusPushParameters;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.states.codebase.CodeBaseTaskStep;
import io.harness.states.codebase.CodeBaseTaskStepParameters;
import io.harness.stateutils.buildstate.ConnectorUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class GitBuildStatusUtility {
  private static final String UNSUPPORTED = "UNSUPPORTED";
  private static final String GITHUB_ERROR = "error";
  private static final String GITHUB_SUCCESS = "success";
  private static final String GITHUB_FAILED = "failure";
  private static final String GITHUB_PENDING = "pending";
  private static final String BITBUCKET_FAILED = "FAILED";
  private static final String BITBUCKET_SUCCESS = "SUCCESSFUL";
  private static final String BITBUCKET_PENDING = "INPROGRESS";
  private static final String GITLAB_FAILED = "failed";
  private static final String GITLAB_CANCELED = "canceled";
  private static final String GITLAB_PENDING = "pending";
  private static final String GITLAB_SUCCESS = "success";
  private static final String DASH = "-";
  private static final int IDENTIFIER_LENGTH = 30;

  @Inject GitClientHelper gitClientHelper;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject @Named("ngBaseUrl") private String ngBaseUrl;
  @Inject private PipelineUtils pipelineUtils;
  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public boolean shouldSendStatus(StepCategory stepCategory) {
    return stepCategory == StepCategory.STAGE;
  }

  public boolean isCodeBaseStepSucceeded(Level level, Status status) {
    return (level.getStepType().getType().equals(CodeBaseTaskStep.STEP_TYPE.getType())) && status == Status.SUCCEEDED;
  }

  /**
   * Status, ResolvedStepParameters
   * @param nodeExecution
   * @param ambiance
   * @param accountId
   */
  public void sendStatusToGit(Status status, StepParameters stepParameters, Ambiance ambiance, String accountId) {
    String commitSha = null;
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    CodebaseSweepingOutput codebaseSweepingOutput = null;
    if (optionalSweepingOutput.isFound()) {
      codebaseSweepingOutput = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
      if (codebaseSweepingOutput != null) {
        commitSha = codebaseSweepingOutput.getCommitSha();
      }
    }
    BuildStatusUpdateParameter buildStatusUpdateParameter = fetchBuildStatusUpdateParameter(stepParameters, ambiance);

    if (commitSha == null && buildStatusUpdateParameter != null) {
      /* This will be used only in case of sending running state of stage build

      Running state status will not work for manual executed builds
       */
      commitSha = buildStatusUpdateParameter.getSha();
    }

    if (buildStatusUpdateParameter != null && isNotEmpty(commitSha)) {
      CIBuildStatusPushParameters ciBuildStatusPushParameters =
          getCIBuildStatusPushParams(ambiance, buildStatusUpdateParameter, status, commitSha);

      /* This check is require because delegate is not honouring the ordering and
         there are instances where we are overriding final status with prev state status i.e running specially in case
         time interval is minuscule
      */

      if (isFinalStatus(status)) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException exception) {
          // Ignore
        }
      }

      if (ciBuildStatusPushParameters.getState() != UNSUPPORTED) {
        Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
        DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                      .accountId(accountId)
                                                      .taskSetupAbstractions(abstractions)
                                                      .executionTimeout(java.time.Duration.ofSeconds(60))
                                                      .taskType("BUILD_STATUS")
                                                      .taskParameters(ciBuildStatusPushParameters)
                                                      .taskDescription("CI git build status task")
                                                      .build();

        String taskId = delegateGrpcClientWrapper.submitAsyncTask(delegateTaskRequest, Duration.ZERO);
        log.info("Submitted git status update request for stage {}, planId {}, commitId {}, status {} with taskId {}",
            buildStatusUpdateParameter.getIdentifier(), ambiance.getPlanExecutionId(), commitSha,
            ciBuildStatusPushParameters.getState(), taskId);
      } else {
        log.info("Skipping git status update request for stage {}, planId {}, commitId {}, status {}, scm type {}",
            buildStatusUpdateParameter.getIdentifier(), ambiance.getPlanExecutionId(), commitSha,
            ciBuildStatusPushParameters.getState(), ciBuildStatusPushParameters.getGitSCMType());
      }
    }
  }

  public CIBuildStatusPushParameters getCIBuildStatusPushParams(
      Ambiance ambiance, BuildStatusUpdateParameter buildStatusUpdateParameter, Status status, String commitSha) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails gitConnector = getGitConnector(ngAccess, buildStatusUpdateParameter.getConnectorIdentifier());

    boolean isAccountLevelConnector = isAccountLevelConnector(gitConnector, buildStatusUpdateParameter.getRepoName());

    String repoName = buildStatusUpdateParameter.getRepoName();
    String url = retrieveURL(gitConnector);
    if (!url.endsWith("/")) {
      url = url + "/";
    }
    String ownerName;
    String finalRepo;
    if (!isAccountLevelConnector) {
      finalRepo = gitClientHelper.getGitRepo(url);
      ownerName = gitClientHelper.getGitOwner(url, false);
    } else {
      finalRepo = gitClientHelper.getGitRepo(url + repoName);
      // Append the url and use the repo level connector owner parsing logic
      ownerName = gitClientHelper.getGitOwner(url + repoName, false);
    }

    GitSCMType gitSCMType = retrieveSCMType(gitConnector);
    return CIBuildStatusPushParameters.builder()
        .detailsUrl(getBuildDetailsUrl(
            ngAccess, ambiance.getMetadata().getPipelineIdentifier(), ambiance.getMetadata().getExecutionUuid()))
        .desc(generateDesc(ambiance.getMetadata().getPipelineIdentifier(), ambiance.getMetadata().getExecutionUuid(),
            buildStatusUpdateParameter.getName(), status.name()))
        .sha(commitSha)
        .gitSCMType(gitSCMType)
        .connectorDetails(gitConnector)
        .userName(connectorUtils.fetchUserName(gitConnector))
        .owner(ownerName)
        .repo(finalRepo)
        .identifier(generateIdentifier(
            ambiance.getMetadata().getPipelineIdentifier(), buildStatusUpdateParameter.getIdentifier()))
        .state(retrieveBuildStatusState(gitSCMType, status))
        .build();
  }

  private String generateDesc(String pipeline, String executionId, String stage, String status) {
    return String.format(
        "Execution status of Pipeline - %s (%s) Stage - %s was %s", pipeline, executionId, stage, status);
  }

  private String generateIdentifier(String pipelineIdentifer, String stageIdentifer) {
    return String.join(DASH, StringUtils.abbreviate(pipelineIdentifer, IDENTIFIER_LENGTH),
        StringUtils.abbreviate(stageIdentifer, IDENTIFIER_LENGTH));
  }

  private GitSCMType retrieveSCMType(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      return GitSCMType.GITHUB;
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      return GitSCMType.BITBUCKET;
    } else if (gitConnector.getConnectorType() == GITLAB) {
      return GitSCMType.GITLAB;
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }
  }

  private String retrieveURL(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrl();
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }
  }

  private boolean isAccountLevelConnector(ConnectorDetails gitConnector, String repoName) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      if (gitConfigDTO.getConnectionType() == ACCOUNT) {
        return true;
      }
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      if (gitConfigDTO.getConnectionType() == ACCOUNT) {
        return true;
      }
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      if (gitConfigDTO.getConnectionType() == ACCOUNT) {
        return true;
      }
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }

    return false;
  }

  private String retrieveBuildStatusState(GitSCMType gitSCMType, Status status) {
    switch (gitSCMType) {
      case GITHUB:
        return getGitHubStatus(status);
      case GITLAB:
        return getGitLabStatus(status);
      case BITBUCKET:
        return getBitBucketStatus(status);
      default:
        unhandled(gitSCMType);
        return UNSUPPORTED;
    }
  }

  private String getGitHubStatus(Status status) {
    if (status == Status.ERRORED) {
      return GITHUB_ERROR;
    }
    if (status == Status.ABORTED || status == Status.FAILED || status == Status.EXPIRED) {
      return GITHUB_FAILED;
    }
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      return GITHUB_SUCCESS;
    }
    if (status == Status.RUNNING) {
      return GITHUB_PENDING;
    }
    if (status == Status.QUEUED) {
      return GITHUB_PENDING;
    }

    return UNSUPPORTED;
  }

  private String getGitLabStatus(Status status) {
    if (status == Status.ERRORED || status == Status.FAILED || status == Status.EXPIRED) {
      return GITLAB_FAILED;
    }
    if (status == Status.ABORTED) {
      return GITLAB_CANCELED;
    }
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      return GITLAB_SUCCESS;
    }
    if (status == Status.RUNNING) {
      return GITLAB_PENDING;
    }
    if (status == Status.QUEUED) {
      return GITLAB_PENDING;
    }

    return UNSUPPORTED;
  }

  private String getBitBucketStatus(Status status) {
    if (status == Status.ERRORED) {
      return BITBUCKET_FAILED;
    }
    if (status == Status.ABORTED || status == Status.FAILED || status == Status.EXPIRED) {
      return BITBUCKET_FAILED;
    }
    if (status == Status.SUCCEEDED || status == Status.IGNORE_FAILED) {
      return BITBUCKET_SUCCESS;
    }
    if (status == Status.RUNNING) {
      return BITBUCKET_PENDING;
    }
    if (status == Status.QUEUED) {
      return BITBUCKET_PENDING;
    }

    return UNSUPPORTED;
  }

  private BuildStatusUpdateParameter fetchBuildStatusUpdateParameter(StepParameters stepParameters, Ambiance ambiance) {
    if (stepParameters instanceof StageElementParameters) {
      StageElementParameters stageElementParameters = (StageElementParameters) stepParameters;
      IntegrationStageStepParametersPMS integrationStageStepParameters =
          (IntegrationStageStepParametersPMS) stageElementParameters.getSpecConfig();
      return integrationStageStepParameters.getBuildStatusUpdateParameter();
    } else if (stepParameters instanceof CodeBaseTaskStepParameters) {
      OptionalSweepingOutput optionalSweepingOutputStageDetails = executionSweepingOutputResolver.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
      if (optionalSweepingOutputStageDetails.isFound()) {
        StageDetails stageDetails = (StageDetails) optionalSweepingOutputStageDetails.getOutput();
        return stageDetails.getBuildStatusUpdateParameter();
      }
    }

    return null;
  }

  private ConnectorDetails getGitConnector(NGAccess ngAccess, String connectorRef) {
    return connectorUtils.getConnectorDetails(ngAccess, connectorRef);
  }

  private String getBuildDetailsUrl(NGAccess ngAccess, String pipelineId, String executionId) {
    return pipelineUtils.getBuildDetailsUrl(ngAccess, pipelineId, executionId, ngBaseUrl);
  }
}
