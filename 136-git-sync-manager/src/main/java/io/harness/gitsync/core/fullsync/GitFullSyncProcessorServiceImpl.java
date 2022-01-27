/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.AuthorizationServiceHeader.GIT_SYNC_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.encryption.Scope;
import io.harness.gitsync.FullSyncChangeSet;
import io.harness.gitsync.FullSyncFileResponse;
import io.harness.gitsync.FullSyncRequest;
import io.harness.gitsync.FullSyncResponse;
import io.harness.gitsync.FullSyncServiceGrpc;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.core.beans.FullSyncMsvcProcessingResponse;
import io.harness.gitsync.core.beans.GitFullSyncEntityInfo;
import io.harness.gitsync.core.fullsync.beans.FullSyncFilesGroupedByMsvc;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class GitFullSyncProcessorServiceImpl implements io.harness.gitsync.core.fullsync.GitFullSyncProcessorService {
  Map<Microservice, FullSyncServiceGrpc.FullSyncServiceBlockingStub> fullSyncServiceBlockingStubMap;
  YamlGitConfigService yamlGitConfigService;
  EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  GitFullSyncEntityService gitFullSyncEntityService;
  FullSyncJobService fullSyncJobService;
  ScmOrchestratorService scmOrchestratorService;
  List<Microservice> microservicesProcessingOrder;

  private static int MAX_RETRY_COUNT = 2;

  @Override
  public FullSyncMsvcProcessingResponse processFiles(
      Microservice microservice, List<GitFullSyncEntityInfo> entityInfoList) {
    boolean failed = false;
    FullSyncResponse fullSyncResponse = null;
    try {
      fullSyncResponse = performSyncForEntities(microservice, entityInfoList);
    } catch (Exception e) {
      failed = true;
    }
    boolean processingStatusFromFiles = setTheProcessingStatusOfFiles(fullSyncResponse, entityInfoList);
    return FullSyncMsvcProcessingResponse.builder()
        .fullSyncFileResponses(
            fullSyncResponse == null ? Collections.emptyList() : fullSyncResponse.getFileResponseList())
        .isStatusSuccess(processingStatusFromFiles || failed)
        .build();
  }

  private boolean setTheProcessingStatusOfFiles(
      FullSyncResponse fullSyncResponse, List<GitFullSyncEntityInfo> entityInfoList) {
    boolean anyFilesFailed = false;
    if (fullSyncResponse == null) {
      return anyFilesFailed;
    }
    for (FullSyncFileResponse fullSyncFileResponse : emptyIfNull(fullSyncResponse.getFileResponseList())) {
      if (fullSyncFileResponse == null) {
        continue;
      }
      GitFullSyncEntityInfo fullSyncEntityInfo =
          emptyIfNull(entityInfoList)
              .stream()
              .filter(x -> x.getFilePath().equals(fullSyncFileResponse.getFilePath()))
              .findFirst()
              .get();
      String errorMsg = "";
      errorMsg = fullSyncFileResponse.getErrorMsg();
      if (isNotEmpty(errorMsg)) {
        gitFullSyncEntityService.markQueuedOrFailed(fullSyncEntityInfo.getUuid(),
            fullSyncEntityInfo.getAccountIdentifier(), fullSyncEntityInfo.getRetryCount(), MAX_RETRY_COUNT, errorMsg);
      } else {
        anyFilesFailed = true;
        gitFullSyncEntityService.markSuccessful(
            fullSyncEntityInfo.getUuid(), fullSyncEntityInfo.getAccountIdentifier());
      }
    }
    return anyFilesFailed;
  }

  private FullSyncResponse performSyncForEntities(
      Microservice microservice, List<GitFullSyncEntityInfo> entityInfoList) {
    if (isEmpty(entityInfoList)) {
      return FullSyncResponse.newBuilder().build();
    }
    final FullSyncServiceGrpc.FullSyncServiceBlockingStub fullSyncServiceBlockingStub =
        fullSyncServiceBlockingStubMap.get(microservice);
    GitFullSyncEntityInfo gitFullSyncEntityInfo = entityInfoList.get(0);
    final YamlGitConfigDTO yamlGitConfigDTO = yamlGitConfigService.getByProjectIdAndRepo(
        gitFullSyncEntityInfo.getAccountIdentifier(), gitFullSyncEntityInfo.getOrgIdentifier(),
        gitFullSyncEntityInfo.getProjectIdentifier(), gitFullSyncEntityInfo.getRepoUrl());
    List<FullSyncChangeSet> fullSyncChangeSets = new ArrayList<>();
    for (GitFullSyncEntityInfo fullSyncEntityInfo : entityInfoList) {
      fullSyncChangeSets.add(getFullSyncChangeSet(fullSyncEntityInfo, yamlGitConfigDTO));
    }
    Map<String, String> logContext = new HashMap<>();
    logContext.put("messageId", gitFullSyncEntityInfo.getMessageId());
    return GitSyncGrpcClientUtils.retryAndProcessException(fullSyncServiceBlockingStub::performEntitySync,
        FullSyncRequest.newBuilder().putAllLogContext(logContext).addAllFileChanges(fullSyncChangeSets).build());
  }

  private FullSyncChangeSet getFullSyncChangeSet(GitFullSyncEntityInfo entityInfo, YamlGitConfigDTO yamlGitConfigDTO) {
    return FullSyncChangeSet.newBuilder()
        .setBranchName(entityInfo.getBranchName())
        .setEntityDetail(entityDetailRestToProtoMapper.createEntityDetailDTO(entityInfo.getEntityDetail()))
        .setFilePath(entityInfo.getFilePath())
        .setYamlGitConfigIdentifier(yamlGitConfigDTO.getIdentifier())
        .setAccountIdentifier(entityInfo.getAccountIdentifier())
        .setFolderPath(yamlGitConfigDTO.getDefaultRootFolder().getRootFolder())
        .setCommitMessage(getCommitMessageForTheFullSyncFlow(entityInfo.getFilePath()))
        .build();
  }

  private String getCommitMessageForTheFullSyncFlow(String filePath) {
    return "Harness Full Sync: "
        + "Add file " + filePath;
  }

  @Override
  public void performFullSync(GitFullSyncJob fullSyncJob) {
    log.info("Started full sync for the job {}", fullSyncJob.getMessageId());
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(GIT_SYNC_SERVICE.getServiceId()));
      UpdateResult updateResult =
          fullSyncJobService.markJobAsRunning(fullSyncJob.getAccountIdentifier(), fullSyncJob.getUuid());
      if (updateResult.getModifiedCount() == 0L) {
        log.info("There is no job to run for the id {}, maybe the other thread is running it", fullSyncJob.getUuid());
      }
      List<GitFullSyncEntityInfo> allEntitiesToBeSynced =
          gitFullSyncEntityService.list(fullSyncJob.getAccountIdentifier(), fullSyncJob.getMessageId());
      boolean processingFailed = false;
      final List<FullSyncFilesGroupedByMsvc> fullSyncFilesGroupedByMsvcs =
          sortTheFilesInTheProcessingOrder(allEntitiesToBeSynced);
      for (FullSyncFilesGroupedByMsvc fullSyncFilesGroupedByMsvc : fullSyncFilesGroupedByMsvcs) {
        log.info("Number of files is {} for the microservice {}",
            emptyIfNull(fullSyncFilesGroupedByMsvc.getGitFullSyncEntityInfoList()).size(),
            fullSyncFilesGroupedByMsvc.getMicroservice());
        FullSyncMsvcProcessingResponse fullSyncMsvcProcessingResponse = processFiles(
            fullSyncFilesGroupedByMsvc.getMicroservice(), fullSyncFilesGroupedByMsvc.getGitFullSyncEntityInfoList());
        processingFailed = fullSyncMsvcProcessingResponse.isStatusSuccess();
        if (fullSyncFilesGroupedByMsvc.getMicroservice() == Microservice.CORE) {
          setTheRepoBranchForTheGitSyncConnector(fullSyncFilesGroupedByMsvc.getGitFullSyncEntityInfoList(),
              fullSyncMsvcProcessingResponse.getFullSyncFileResponses());
        }
      }

      updateTheStatusOfJob(processingFailed, fullSyncJob);
      if (!processingFailed && fullSyncJob.isCreatePullRequest()) {
        createAPullRequest(fullSyncJob);
      }
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
    log.info("Completed full sync for the job {}", fullSyncJob.getMessageId());
  }

  private void setTheRepoBranchForTheGitSyncConnector(
      List<GitFullSyncEntityInfo> ngCoreFullSyncFiles, List<FullSyncFileResponse> fullSyncMsvcProcessingResponses) {
    List<GitFullSyncEntityInfo> connectors = ngCoreFullSyncFiles.stream()
                                                 .filter(x -> x.getEntityDetail().getType() == EntityType.CONNECTORS)
                                                 .collect(Collectors.toList());
    if (isEmpty(connectors)) {
      return;
    }
    GitFullSyncEntityInfo gitFullSyncEntityInfo = ngCoreFullSyncFiles.get(0);
    String accountIdentifier = gitFullSyncEntityInfo.getAccountIdentifier();
    String orgIdentifier = gitFullSyncEntityInfo.getOrgIdentifier();
    String projectIdentifier = gitFullSyncEntityInfo.getProjectIdentifier();
    List<YamlGitConfigDTO> yamlGitConfigDTOS =
        yamlGitConfigService.list(projectIdentifier, orgIdentifier, accountIdentifier);
    populateRepoAndBranchForTheConnectorRef(accountIdentifier, orgIdentifier, projectIdentifier, ngCoreFullSyncFiles,
        fullSyncMsvcProcessingResponses, yamlGitConfigDTOS);
  }

  private void populateRepoAndBranchForTheConnectorRef(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<GitFullSyncEntityInfo> ngCoreFullSyncFiles,
      List<FullSyncFileResponse> fullSyncMsvcProcessingResponses, List<YamlGitConfigDTO> yamlGitConfigDTOS) {
    if (isEmpty(yamlGitConfigDTOS)) {
      return;
    }
    for (YamlGitConfigDTO yamlGitConfigDTO : yamlGitConfigDTOS) {
      String gitConnectorRef = yamlGitConfigDTO.getGitConnectorRef();
      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
          gitConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier, null);
      Optional<GitFullSyncEntityInfo> gitFullSyncEntityInfoOptional =
          getConnectorFullSyncEntity(identifierRef, ngCoreFullSyncFiles, fullSyncMsvcProcessingResponses);
      if (gitFullSyncEntityInfoOptional.isPresent()) {
        GitFullSyncEntityInfo gitFullSyncEntityInfo = gitFullSyncEntityInfoOptional.get();
        saveTheRepoAndBranchInYamlGitConfig(
            yamlGitConfigDTO, gitFullSyncEntityInfo.getYamlGitConfigId(), gitFullSyncEntityInfo.getBranchName());
      }
    }
  }

  private void saveTheRepoAndBranchInYamlGitConfig(
      YamlGitConfigDTO yamlGitConfigDTO, String yamlGitConfigId, String branchName) {
    yamlGitConfigService.updateTheConnectorRepoAndBranch(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getIdentifier(), yamlGitConfigId, branchName);
  }

  private Optional<GitFullSyncEntityInfo> getConnectorFullSyncEntity(IdentifierRef identifierRef,
      List<GitFullSyncEntityInfo> ngCoreFullSyncFiles, List<FullSyncFileResponse> fullSyncMsvcProcessingResponses) {
    if (isEmpty(fullSyncMsvcProcessingResponses)) {
      return Optional.empty();
    }

    if (identifierRef.getScope() == Scope.ACCOUNT || identifierRef.getScope() == Scope.ORG) {
      return Optional.empty();
    }

    return emptyIfNull(ngCoreFullSyncFiles)
        .stream()
        .filter(x -> checkIfEntityMatchesTheConnector(x, identifierRef))
        .findFirst();
  }

  private boolean checkIfEntityMatchesTheConnector(
      GitFullSyncEntityInfo gitFullSyncEntityInfo, IdentifierRef identifierRef) {
    if (identifierRef == null) {
      return false;
    }

    IdentifierRef entityRef = (IdentifierRef) gitFullSyncEntityInfo.getEntityDetail().getEntityRef();
    return identifierRef.getIdentifier().equals(entityRef.getIdentifier());
  }

  private void createAPullRequest(GitFullSyncJob fullSyncJob) {
    String projectIdentifier = fullSyncJob.getProjectIdentifier();
    String orgIdentifier = fullSyncJob.getOrgIdentifier();
    String accountIdentifier = fullSyncJob.getAccountIdentifier();
    String yamlGitConfigIdentifier = fullSyncJob.getYamlGitConfigIdentifier();
    YamlGitConfigDTO yamlGitConfigDTO =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigIdentifier);
    GitPRCreateRequest createPRRequest = GitPRCreateRequest.builder()
                                             .accountIdentifier(accountIdentifier)
                                             .orgIdentifier(orgIdentifier)
                                             .projectIdentifier(projectIdentifier)
                                             .yamlGitConfigRef(yamlGitConfigIdentifier)
                                             .title(fullSyncJob.getPrTitle())
                                             .sourceBranch(fullSyncJob.getBranch())
                                             .targetBranch(fullSyncJob.getTargetBranch())
                                             .build();
    scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.createPullRequest(createPRRequest),
        projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigDTO.getGitConnectorRef());
  }

  private List<FullSyncFilesGroupedByMsvc> sortTheFilesInTheProcessingOrder(
      List<GitFullSyncEntityInfo> allEntitiesToBeSynced) {
    List<FullSyncFilesGroupedByMsvc> filesGroupedByMicroservices = new ArrayList<>();
    Map<String, List<GitFullSyncEntityInfo>> filesGroupedByMsvc =
        emptyIfNull(allEntitiesToBeSynced)
            .stream()
            .filter(x -> !x.getSyncStatus().equals(GitFullSyncEntityInfo.SyncStatus.SUCCESS.toString()))
            .collect(Collectors.groupingBy(GitFullSyncEntityInfo::getMicroservice));
    for (Map.Entry<String, List<GitFullSyncEntityInfo>> entry : filesGroupedByMsvc.entrySet()) {
      Microservice microservice = Microservice.fromString(entry.getKey());
      List<GitFullSyncEntityInfo> filesForThisMicroservice = entry.getValue();
      if (microservice == Microservice.CORE) {
        keepTheFullSyncConnectorAtTheLast(filesForThisMicroservice);
      }
      FullSyncFilesGroupedByMsvc fullSyncFilesGroupedByMsvc = FullSyncFilesGroupedByMsvc.builder()
                                                                  .microservice(microservice)
                                                                  .gitFullSyncEntityInfoList(filesForThisMicroservice)
                                                                  .build();
      filesGroupedByMicroservices.add(fullSyncFilesGroupedByMsvc);
    }
    filesGroupedByMicroservices.sort(Comparator.comparingInt(x -> microservicesProcessingOrder.indexOf(x)));
    return filesGroupedByMicroservices;
  }

  private void keepTheFullSyncConnectorAtTheLast(List<GitFullSyncEntityInfo> filesForThisMicroservice) {
    // When we are doing the full sync of the entities, we do a get call of the full sync config connector.
    // If the git connector itself is full synced before any entity then we won't be able to find this
    // git connector in other repo and branch. Hence we are first syncing all other files and at last we are
    // syncing this git connector.
    if (isEmpty(filesForThisMicroservice)) {
      return;
    }
    GitFullSyncEntityInfo gitFullSyncEntityInfo = filesForThisMicroservice.get(0);
    String yamlGitConfigId = gitFullSyncEntityInfo.getYamlGitConfigId();
    String accountIdentifier = gitFullSyncEntityInfo.getAccountIdentifier();
    String orgIdentifier = gitFullSyncEntityInfo.getOrgIdentifier();
    String projectIdentifier = gitFullSyncEntityInfo.getProjectIdentifier();

    YamlGitConfigDTO yamlGitConfigDTO =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigId);
    String gitConnectorRef = yamlGitConfigDTO.getGitConnectorRef();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        gitConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier, null);

    if (identifierRef.getScope() == Scope.ACCOUNT || identifierRef.getScope() == Scope.ORG) {
      return;
    }

    final OptionalInt posOfConnectorFullSyncEntityOpt =
        IntStream.range(0, filesForThisMicroservice.size())
            .filter(x -> checkIfEntityMatchesTheConnector(filesForThisMicroservice.get(x), identifierRef))
            .findFirst();

    if (posOfConnectorFullSyncEntityOpt.isPresent()) {
      final int positionOfConnectorFullSyncEntity = posOfConnectorFullSyncEntityOpt.getAsInt();
      GitFullSyncEntityInfo connectorFullSyncEntity = filesForThisMicroservice.get(positionOfConnectorFullSyncEntity);
      filesForThisMicroservice.remove(positionOfConnectorFullSyncEntity);
      filesForThisMicroservice.add(connectorFullSyncEntity);
    }
  }

  private void updateTheStatusOfJob(boolean processingFailed, GitFullSyncJob fullSyncJob) {
    if (processingFailed) {
      if (fullSyncJob.getRetryCount() >= MAX_RETRY_COUNT) {
        fullSyncJobService.markFullSyncJobAsFailed(
            fullSyncJob.getAccountIdentifier(), fullSyncJob.getUuid(), GitFullSyncJob.SyncStatus.FAILED);
      } else {
        fullSyncJobService.markFullSyncJobAsFailed(fullSyncJob.getAccountIdentifier(), fullSyncJob.getUuid(),
            GitFullSyncJob.SyncStatus.FAILED_WITH_RETRIES_LEFT);
      }
    } else {
      fullSyncJobService.markFullSyncJobAsSuccess(fullSyncJob.getAccountIdentifier(), fullSyncJob.getUuid());
    }
  }
}
