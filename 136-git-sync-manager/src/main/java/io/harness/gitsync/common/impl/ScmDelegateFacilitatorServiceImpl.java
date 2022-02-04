/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.gitsync.GitFileDetails.GitFileDetailsBuilder;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.impl.ConnectorErrorMessagesHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.scm.GitFileTaskResponseData;
import io.harness.delegate.task.scm.GitFileTaskType;
import io.harness.delegate.task.scm.GitPRTaskType;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.delegate.task.scm.ScmGitFileTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.delegate.task.scm.ScmGitWebhookTaskParams;
import io.harness.delegate.task.scm.ScmGitWebhookTaskResponseData;
import io.harness.delegate.task.scm.ScmPRTaskParams;
import io.harness.delegate.task.scm.ScmPRTaskResponseData;
import io.harness.delegate.task.scm.ScmPushTaskParams;
import io.harness.delegate.task.scm.ScmPushTaskResponseData;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.beans.InfoForGitPush;
import io.harness.gitsync.common.dtos.CreatePRDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileListDTO;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.helper.FileBatchResponseMapper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.PRFileListMapper;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.FileBatchContentResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.ListBranchesResponse;
import io.harness.product.ci.scm.proto.ListCommitsResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

// Don't inject this directly go through ScmClientOrchestrator.
@Slf4j
@OwnedBy(DX)
public class ScmDelegateFacilitatorServiceImpl extends AbstractScmClientFacilitatorServiceImpl {
  private SecretManagerClientService secretManagerClientService;
  private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject
  public ScmDelegateFacilitatorServiceImpl(@Named("connectorDecoratorService") ConnectorService connectorService,
      ConnectorErrorMessagesHelper connectorErrorMessagesHelper, YamlGitConfigService yamlGitConfigService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper,
      UserProfileHelper userProfileHelper, GitSyncConnectorHelper gitSyncConnectorHelper) {
    super(connectorService, connectorErrorMessagesHelper, yamlGitConfigService, userProfileHelper,
        gitSyncConnectorHelper);
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
  }

  @Override
  public List<String> listBranchesForRepoByConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifierRef, String repoURL, PageRequest pageRequest,
      String searchTerm) {
    final ScmConnector scmConnector =
        getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifierRef);
    scmConnector.setUrl(repoURL);
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams =
        getScmGitRefTaskParams(scmConnector, encryptionDetails, GitRefType.BRANCH);
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return ListBranchesResponse.parseFrom(scmGitRefTaskResponseData.getListBranchesResponse()).getBranchesList();
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public GitFileContent getFileContent(String yamlGitConfigIdentifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filePath, String branch, String commitId) {
    validateFileContentParams(branch, commitId);
    YamlGitConfigDTO yamlGitConfigDTO =
        getYamlGitConfigDTO(accountIdentifier, orgIdentifier, projectIdentifier, yamlGitConfigIdentifier);
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    final GitFilePathDetails gitFilePathDetails = getGitFilePathDetails(filePath, branch, commitId);
    final ScmGitFileTaskParams scmGitFileTaskParams = getScmGitFileTaskParams(
        scmConnector, encryptionDetails, gitFilePathDetails, GitFileTaskType.GET_FILE_CONTENT, null, branch, null);
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(accountIdentifier, yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return validateAndGetGitFileContent(FileContent.parseFrom(gitFileTaskResponseData.getFileContent()));
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public CreatePRDTO createPullRequest(GitPRCreateRequest gitCreatePRRequest) {
    validateTheCreatePRRequest(gitCreatePRRequest);
    YamlGitConfigDTO yamlGitConfigDTO =
        getYamlGitConfigDTO(gitCreatePRRequest.getAccountIdentifier(), gitCreatePRRequest.getOrgIdentifier(),
            gitCreatePRRequest.getProjectIdentifier(), gitCreatePRRequest.getYamlGitConfigRef());
    final IdentifierRef gitConnectorIdentifierRef =
        getConnectorIdentifierRef(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    ConnectorResponseDTO connectorResponseDTO =
        getConnectorResponseDTO(yamlGitConfigDTO, gitCreatePRRequest.getAccountIdentifier());
    checkAndSetUserFromUserProfile(gitCreatePRRequest.isUseUserFromToken(), yamlGitConfigDTO, connectorResponseDTO);
    final ScmConnector scmConnector = (ScmConnector) connectorResponseDTO.getConnector().getConnectorConfig();
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(gitCreatePRRequest.getAccountIdentifier(), gitCreatePRRequest.getOrgIdentifier(),
            gitCreatePRRequest.getProjectIdentifier(), scmConnector);
    ScmPRTaskParams scmPRTaskParams = ScmPRTaskParams.builder()
                                          .scmConnector(scmConnector)
                                          .gitPRCreateRequest(gitCreatePRRequest)
                                          .gitPRTaskType(GitPRTaskType.CREATE_PR)
                                          .encryptedDataDetails(encryptionDetails)
                                          .build();
    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(gitConnectorIdentifierRef.getAccountIdentifier(),
            gitConnectorIdentifierRef.getOrgIdentifier(), gitConnectorIdentifierRef.getProjectIdentifier());
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(gitConnectorIdentifierRef.getAccountIdentifier())
                                                  .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
                                                  .taskType(TaskType.SCM_PULL_REQUEST_TASK.name())
                                                  .taskParameters(scmPRTaskParams)
                                                  .executionTimeout(Duration.ofMinutes(2))
                                                  .build();
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPRTaskResponseData scmCreatePRResponse = (ScmPRTaskResponseData) delegateResponseData;
    final CreatePRResponse createPRResponse = scmCreatePRResponse.getCreatePRResponse();
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          createPRResponse.getStatus(), createPRResponse.getError());
    } catch (WingsException e) {
      throw new ExplanationException(String.format("Could not create the pull request from %s to %s",
                                         gitCreatePRRequest.getSourceBranch(), gitCreatePRRequest.getTargetBranch()),
          e);
    }
    return CreatePRDTO.builder().prNumber(createPRResponse.getNumber()).build();
  }

  private void validateTheCreatePRRequest(GitPRCreateRequest gitCreatePRRequest) {
    if (gitCreatePRRequest == null) {
      throw new InvalidRequestException("The CreatePR request cannot be null");
    }
    if (gitCreatePRRequest.getSourceBranch().equals(gitCreatePRRequest.getTargetBranch())) {
      throw new InvalidRequestException("The PR cannot be created for the same source and target branch");
    }
  }

  @Override
  public List<GitFileChangeDTO> listFilesOfBranches(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlGitConfigRef, Set<String> foldersList, String branchName) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(yamlGitConfigRef, accountIdentifier, orgIdentifier, projectIdentifier);
    YamlGitConfigDTO yamlGitConfigDTO = getYamlGitConfigDTO(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    ScmGitFileTaskParams scmGitFileTaskParams = ScmGitFileTaskParams.builder()
                                                    .gitFileTaskType(GitFileTaskType.GET_FILE_CONTENT_BATCH)
                                                    .branch(branchName)
                                                    .scmConnector(scmConnector)
                                                    .foldersList(foldersList)
                                                    .encryptedDataDetails(encryptionDetails)
                                                    .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(identifierRef.getAccountIdentifier(),
        orgIdentifier, projectIdentifier, scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return FileBatchResponseMapper.createGitFileChangeList(
          FileBatchContentResponse.parseFrom(gitFileTaskResponseData.getFileBatchContentResponse()),
          gitFileTaskResponseData.getCommitId());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public List<GitFileChangeDTO> listFilesByFilePaths(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String branchName) {
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitFileTaskParams scmGitFileTaskParams = getScmGitFileTaskParams(scmConnector, encryptionDetails, null,
        GitFileTaskType.GET_FILE_CONTENT_BATCH_BY_FILE_PATHS, null, branchName, filePaths);
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return FileBatchResponseMapper.createGitFileChangeList(
          FileBatchContentResponse.parseFrom(gitFileTaskResponseData.getFileBatchContentResponse()),
          gitFileTaskResponseData.getCommitId());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public List<GitFileChangeDTO> listFilesByCommitId(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String commitId) {
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitFileTaskParams scmGitFileTaskParams = getScmGitFileTaskParams(scmConnector, encryptionDetails, null,
        GitFileTaskType.GET_FILE_CONTENT_BATCH_BY_REF, commitId, null, filePaths);
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return FileBatchResponseMapper.createGitFileChangeList(
          FileBatchContentResponse.parseFrom(gitFileTaskResponseData.getFileBatchContentResponse()),
          gitFileTaskResponseData.getCommitId());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public GitDiffResultFileListDTO listCommitsDiffFiles(
      YamlGitConfigDTO yamlGitConfigDTO, String initialCommitId, String finalCommitId) {
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.COMPARE_COMMITS)
                                                        .initialCommitId(initialCommitId)
                                                        .finalCommitId(finalCommitId)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return PRFileListMapper.toGitDiffResultFileListDTO(
          CompareCommitsResponse.parseFrom(scmGitRefTaskResponseData.getCompareCommitsResponse()).getFilesList());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public List<String> listCommits(YamlGitConfigDTO yamlGitConfigDTO, String branch) {
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.COMMIT)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(branch)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return new ArrayList<>(
          ListCommitsResponse.parseFrom(scmGitRefTaskResponseData.getListCommitsResponse()).getCommitIdsList());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public Commit getLatestCommit(YamlGitConfigDTO yamlGitConfigDTO, String branch) {
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.LATEST_COMMIT_ID)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(branch)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return GetLatestCommitResponse.parseFrom(scmGitRefTaskResponseData.getGetLatestCommitResponse()).getCommit();
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public CreateFileResponse createFile(InfoForGitPush infoForPush) {
    GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getYaml(), infoForPush.getFilePath(),
        infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .changeType(ChangeType.ADD)
                                              .scmConnector(infoForPush.getScmConnector())
                                              .gitFileDetails(gitFileDetails.build())
                                              .encryptedDataDetails(encryptionDetails)
                                              .isNewBranch(infoForPush.isNewBranch())
                                              .baseBranch(infoForPush.getBaseBranch())
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return CreateFileResponse.parseFrom(scmPushTaskResponseData.getCreateFileResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public UpdateFileResponse updateFile(InfoForGitPush infoForPush) {
    GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getYaml(), infoForPush.getFilePath(),
        infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch());
    gitFileDetails.oldFileSha(infoForPush.getOldFileSha());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .changeType(ChangeType.MODIFY)
                                              .scmConnector(infoForPush.getScmConnector())
                                              .gitFileDetails(gitFileDetails.build())
                                              .encryptedDataDetails(encryptionDetails)
                                              .isNewBranch(infoForPush.isNewBranch())
                                              .baseBranch(infoForPush.getBaseBranch())
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return UpdateFileResponse.parseFrom(scmPushTaskResponseData.getUpdateFileResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public DeleteFileResponse deleteFile(InfoForGitPush infoForPush) {
    GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getYaml(), infoForPush.getFilePath(),
        infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch());
    gitFileDetails.oldFileSha(infoForPush.getOldFileSha());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .changeType(ChangeType.DELETE)
                                              .scmConnector(infoForPush.getScmConnector())
                                              .gitFileDetails(gitFileDetails.build())
                                              .encryptedDataDetails(encryptionDetails)
                                              .isNewBranch(infoForPush.isNewBranch())
                                              .baseBranch(infoForPush.getBaseBranch())
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return DeleteFileResponse.parseFrom(scmPushTaskResponseData.getDeleteFileResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public Commit findCommitById(YamlGitConfigDTO yamlGitConfigDTO, String commitId) {
    final ScmConnector scmConnector =
        getScmConnector(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.FIND_COMMIT)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .ref(commitId)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return FindCommitResponse.parseFrom(scmGitRefTaskResponseData.getFindCommitResponse()).getCommit();
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public CreateWebhookResponse upsertWebhook(
      UpsertWebhookRequestDTO upsertWebhookRequestDTO, String target, GitWebhookTaskType gitWebhookTaskType) {
    final ScmConnector scmConnector =
        getScmConnector(upsertWebhookRequestDTO.getAccountIdentifier(), upsertWebhookRequestDTO.getOrgIdentifier(),
            upsertWebhookRequestDTO.getProjectIdentifier(), upsertWebhookRequestDTO.getConnectorIdentifierRef());
    if (!isEmpty(upsertWebhookRequestDTO.getRepoURL())) {
      scmConnector.setUrl(upsertWebhookRequestDTO.getRepoURL());
    }
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(upsertWebhookRequestDTO.getAccountIdentifier(),
            upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getProjectIdentifier(), scmConnector);
    final ScmGitWebhookTaskParams gitWebhookTaskParams =
        ScmGitWebhookTaskParams.builder()
            .gitWebhookTaskType(gitWebhookTaskType)
            .scmConnector(scmConnector)
            .encryptedDataDetails(encryptionDetails)
            .gitWebhookDetails(GitWebhookDetails.builder()
                                   .hookEventType(upsertWebhookRequestDTO.getHookEventType())
                                   .target(target)
                                   .build())
            .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(upsertWebhookRequestDTO.getAccountIdentifier(),
        upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getProjectIdentifier(),
        gitWebhookTaskParams, TaskType.SCM_GIT_WEBHOOK_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitWebhookTaskResponseData scmGitWebhookTaskResponseData = (ScmGitWebhookTaskResponseData) delegateResponseData;
    try {
      return CreateWebhookResponse.parseFrom(scmGitWebhookTaskResponseData.getCreateWebhookResponse());

    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public CreateBranchResponse createBranch(InfoForGitPush infoForGitPush, String yamlGitConfigIdentifier) {
    YamlGitConfigDTO yamlGitConfigDTO = getYamlGitConfigDTO(infoForGitPush.getAccountId(),
        infoForGitPush.getOrgIdentifier(), infoForGitPush.getProjectIdentifier(), yamlGitConfigIdentifier);
    final ScmConnector scmConnector = getScmConnector(infoForGitPush.getAccountId(), infoForGitPush.getOrgIdentifier(),
        infoForGitPush.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForGitPush.getAccountId(),
        infoForGitPush.getOrgIdentifier(), infoForGitPush.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.CREATE_NEW_BRANCH)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(infoForGitPush.getBranch())
                                                        .baseBranch(infoForGitPush.getBaseBranch())
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(infoForGitPush.getAccountId(), infoForGitPush.getOrgIdentifier(),
            infoForGitPush.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return CreateBranchResponse.parseFrom(scmGitRefTaskResponseData.getCreateBranchResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  // ------------------------------- PRIVATE METHODS -------------------------------

  private List<EncryptedDataDetail> getEncryptedDataDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScmConnector scmConnector) {
    final BaseNGAccess baseNGAccess = getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    final DecryptableEntity apiAccessDecryptableEntity =
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector);
    return secretManagerClientService.getEncryptionDetails(baseNGAccess, apiAccessDecryptableEntity);
  }

  private ScmConnector getScmConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifierRef) {
    final IdentifierRef gitConnectorIdentifierRef =
        getConnectorIdentifierRef(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifierRef);
    return getScmConnector(gitConnectorIdentifierRef);
  }

  private DelegateTaskRequest getDelegateTaskRequest(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, TaskParameters taskParameters, TaskType taskType) {
    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountIdentifier, orgIdentifier, projectIdentifier);
    return DelegateTaskRequest.builder()
        .accountId(accountIdentifier)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .taskParameters(taskParameters)
        .taskType(taskType.name())
        .executionTimeout(Duration.ofMinutes(2))
        .build();
  }

  private BaseNGAccess getBaseNGAccess(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private ScmGitRefTaskParams getScmGitRefTaskParams(
      ScmConnector scmConnector, List<EncryptedDataDetail> encryptionDetails, GitRefType gitRefType) {
    return ScmGitRefTaskParams.builder()
        .scmConnector(scmConnector)
        .gitRefType(gitRefType)
        .encryptedDataDetails(encryptionDetails)
        .build();
  }

  private ScmGitFileTaskParams getScmGitFileTaskParams(ScmConnector scmConnector,
      List<EncryptedDataDetail> encryptionDetails, GitFilePathDetails gitFilePathDetails,
      GitFileTaskType gitFileTaskType, String ref, String branch, List<String> filePathLists) {
    return ScmGitFileTaskParams.builder()
        .gitFileTaskType(gitFileTaskType)
        .scmConnector(scmConnector)
        .gitFilePathDetails(gitFilePathDetails)
        .filePathsList(filePathLists)
        .encryptedDataDetails(encryptionDetails)
        .ref(ref)
        .branch(branch)
        .build();
  }

  private DelegateResponseData executeDelegateSyncTask(DelegateTaskRequest delegateTaskRequest) {
    final DelegateResponseData delegateResponseData;
    try {
      delegateResponseData = delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), ex, WingsException.USER));
    }

    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException("Delegates are not available", WingsException.USER));
    }
    return delegateResponseData;
  }
}
