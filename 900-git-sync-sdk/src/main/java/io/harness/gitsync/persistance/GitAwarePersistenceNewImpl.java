/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.gitsync.interceptor.GitSyncConstants.DEFAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.gitsync.entityInfo.GitSdkEntityHandlerInterface;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmPushResponse;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.utils.RetryUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Singleton
@OwnedBy(DX)
@Slf4j
public class GitAwarePersistenceNewImpl implements GitAwarePersistence {
  private MongoTemplate mongoTemplate;
  private GitSyncSdkService gitSyncSdkService;
  private Map<String, GitSdkEntityHandlerInterface> gitPersistenceHelperServiceMap;
  private SCMGitSyncHelper scmGitSyncHelper;
  private GitSyncMsvcHelper gitSyncMsvcHelper;
  private ObjectMapper objectMapper;
  private TransactionTemplate transactionTemplate;

  private final RetryPolicy<Object> transactionRetryPolicy = RetryUtils.getRetryPolicy("[Retrying] attempt: {}",
      "[Failed] attempt: {}", ImmutableList.of(TransactionException.class), Duration.ofSeconds(1), 3, log);

  @Inject
  public GitAwarePersistenceNewImpl(MongoTemplate mongoTemplate, GitSyncSdkService gitSyncSdkService,
      Map<String, GitSdkEntityHandlerInterface> gitPersistenceHelperServiceMap, SCMGitSyncHelper scmGitSyncHelper,
      GitSyncMsvcHelper gitSyncMsvcHelper, @Named("GitSyncObjectMapper") ObjectMapper objectMapper,
      @Named("OUTBOX_TRANSACTION_TEMPLATE") TransactionTemplate transactionTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.gitSyncSdkService = gitSyncSdkService;
    this.gitPersistenceHelperServiceMap = gitPersistenceHelperServiceMap;
    this.scmGitSyncHelper = scmGitSyncHelper;
    this.gitSyncMsvcHelper = gitSyncMsvcHelper;
    this.objectMapper = objectMapper;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, String yaml, ChangeType changeType, Class<B> entityClass, Supplier functor) {
    final GitSdkEntityHandlerInterface gitSdkEntityHandlerInterface =
        gitPersistenceHelperServiceMap.get(entityClass.getCanonicalName());
    final EntityDetail entityDetail = gitSdkEntityHandlerInterface.getEntityDetail(objectToSave);
    final boolean gitSyncEnabled = isGitSyncEnabled(entityDetail.getEntityRef().getProjectIdentifier(),
        entityDetail.getEntityRef().getOrgIdentifier(), entityDetail.getEntityRef().getAccountIdentifier());
    if (changeType != ChangeType.NONE && gitSyncEnabled) {
      return saveWithGitSyncEnabled(objectToSave, yaml, changeType, entityDetail);
    }
    if (changeType == ChangeType.ADD) {
      objectToSave.setIsFromDefaultBranch(true);
    }
    if (!gitSyncEnabled) {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        final B mongoSavedObject = mongoTemplate.save(objectToSave);
        if (functor != null) {
          functor.get();
        }
        return mongoSavedObject;
      }));
    }
    // will come here in case of change type none and git sync enabled.
    return mongoTemplate.save(objectToSave);
  }

  private <B extends GitSyncableEntity, Y extends YamlDTO> B saveWithGitSyncEnabled(
      B objectToSave, String yamlString, ChangeType changeType, EntityDetail entityDetail) {
    final GitEntityInfo gitBranchInfo = getGitEntityInfo();
    final ScmPushResponse scmPushResponse =
        scmGitSyncHelper.pushToGit(gitBranchInfo, yamlString, changeType, entityDetail);

    updateObjectWithGitMetadata(objectToSave, scmPushResponse);
    final B savedObjectInMongo = mongoTemplate.save(objectToSave);

    gitSyncMsvcHelper.postPushInformationToGitMsvc(entityDetail, scmPushResponse, gitBranchInfo);
    return savedObjectInMongo;
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, String yaml, ChangeType changeType, Class<B> entityClass) {
    final GitSdkEntityHandlerInterface gitSdkEntityHandlerInterface =
        gitPersistenceHelperServiceMap.get(entityClass.getCanonicalName());
    final EntityDetail entityDetail = gitSdkEntityHandlerInterface.getEntityDetail(objectToSave);
    final boolean gitSyncEnabled = isGitSyncEnabled(entityDetail.getEntityRef().getProjectIdentifier(),
        entityDetail.getEntityRef().getOrgIdentifier(), entityDetail.getEntityRef().getAccountIdentifier());
    if (changeType != ChangeType.NONE && gitSyncEnabled) {
      return saveWithGitSyncEnabled(objectToSave, yaml, changeType, entityDetail);
    }
    if (changeType == ChangeType.ADD) {
      objectToSave.setIsFromDefaultBranch(true);
    }
    return mongoTemplate.save(objectToSave);
  }

  private <B extends GitSyncableEntity> void updateObjectWithGitMetadata(
      B objectToSave, ScmPushResponse scmPushResponse) {
    final String objectIdOfYaml = scmPushResponse.getObjectId();
    objectToSave.setObjectIdOfYaml(objectIdOfYaml);
    objectToSave.setYamlGitConfigRef(scmPushResponse.getYamlGitConfigId());
    objectToSave.setIsFromDefaultBranch(scmPushResponse.isPushToDefaultBranch());
    objectToSave.setBranch(scmPushResponse.getBranch());
    objectToSave.setFilePath(scmPushResponse.getFilePath());
    objectToSave.setRootFolder(scmPushResponse.getFolderPath());
  }

  private GitEntityInfo getGitEntityInfo() {
    final GitSyncBranchContext gitSyncBranchContext =
        GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
    if (gitSyncBranchContext == null) {
      log.warn("Git branch context set as null even git sync is enabled");
      // Setting to default branch in case it is not set.
      return GitEntityInfo.builder().yamlGitConfigId(DEFAULT).branch(DEFAULT).build();
    }
    return gitSyncBranchContext.getGitBranchInfo();
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> Long count(
      Criteria criteria, String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    final Criteria gitSyncCriteria = getCriteriaWithGitSync(projectIdentifier, orgIdentifier, accountId, entityClass);
    List<Criteria> criteriaList = Arrays.asList(criteria, gitSyncCriteria);
    Query query = new Query()
                      .addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[criteriaList.size()])))
                      .limit(-1)
                      .skip(-1);

    return mongoTemplate.count(query, entityClass);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> Optional<B> findOne(
      Criteria criteria, String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    final Criteria gitSyncCriteria = getCriteriaWithGitSync(projectIdentifier, orgIdentifier, accountId, entityClass);
    List<Criteria> criteriaList = Arrays.asList(criteria, gitSyncCriteria);
    Query query =
        new Query().addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[criteriaList.size()])));
    final B object = mongoTemplate.findOne(query, entityClass);
    return Optional.ofNullable(object);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> Optional<B> findOne(
      Criteria criteria, String repo, String branch, Class<B> entityClass) {
    final Criteria gitSyncCriteria = createGitSyncCriteriaForRepoAndBranch(repo, branch, null, entityClass);
    List<Criteria> criteriaList = Arrays.asList(criteria, gitSyncCriteria);
    Query query =
        new Query().addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[criteriaList.size()])));
    final B object = mongoTemplate.findOne(query, entityClass);
    return Optional.ofNullable(object);
  }

  private <B extends GitSyncableEntity> Criteria createGitSyncCriteriaForRepoAndBranch(
      String repo, String branch, Boolean isFindDefaultFromOtherBranches, Class<B> entityClass) {
    if (repo == null || branch == null || repo.equals(DEFAULT) || branch.equals(DEFAULT)) {
      return getCriteriaWhenGitSyncNotEnabled(entityClass);
    }
    return getRepoAndBranchCriteria(repo, branch, isFindDefaultFromOtherBranches, entityClass);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> List<B> find(Criteria criteria, Pageable pageable,
      String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    final Criteria gitSyncCriteria = getCriteriaWithGitSync(projectIdentifier, orgIdentifier, accountId, entityClass);
    List<Criteria> criteriaList = Arrays.asList(criteria, gitSyncCriteria);
    Query query = new Query()
                      .addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[criteriaList.size()])))
                      .with(pageable);
    return mongoTemplate.find(query, entityClass);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> boolean exists(
      Criteria criteria, String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    final Criteria gitSyncCriteria = getCriteriaWithGitSync(projectIdentifier, orgIdentifier, accountId, entityClass);
    List<Criteria> criteriaList = Arrays.asList(criteria, gitSyncCriteria);
    Query query =
        new Query().addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[criteriaList.size()])));
    return mongoTemplate.exists(query, entityClass);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, ChangeType changeType, Class<B> entityClass, Supplier functor) {
    final Supplier<Y> yamlFromEntity =
        gitPersistenceHelperServiceMap.get(entityClass.getCanonicalName()).getYamlFromEntity(objectToSave);
    final String yamlString = NGYamlUtils.getYamlString(yamlFromEntity.get(), objectMapper);
    return save(objectToSave, yamlString, changeType, entityClass, functor);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, ChangeType changeType, Class<B> entityClass) {
    final Supplier<Y> yamlFromEntity =
        gitPersistenceHelperServiceMap.get(entityClass.getCanonicalName()).getYamlFromEntity(objectToSave);
    final String yamlString = NGYamlUtils.getYamlString(yamlFromEntity.get(), objectMapper);
    return save(objectToSave, yamlString, changeType, entityClass);
  }

  private boolean isGitSyncEnabled(String projectIdentifier, String orgIdentifier, String accountIdentifier) {
    return gitSyncSdkService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private Criteria getCriteriaWhenGitSyncNotEnabled(Class entityClass) {
    final GitSdkEntityHandlerInterface gitSdkEntityHandlerInterface =
        gitPersistenceHelperServiceMap.get(entityClass.getCanonicalName());
    return new Criteria().andOperator(
        new Criteria().orOperator(Criteria.where(gitSdkEntityHandlerInterface.getIsFromDefaultBranchKey()).is(true),
            Criteria.where(gitSdkEntityHandlerInterface.getIsFromDefaultBranchKey()).exists(false)));
  }

  private Criteria getRepoAndBranchCriteria(
      String repo, String branch, Boolean isFindDefaultFromOtherBranches, Class entityClass) {
    final GitSdkEntityHandlerInterface gitSdkEntityHandlerInterface =
        gitPersistenceHelperServiceMap.get(entityClass.getCanonicalName());
    Criteria criteria = new Criteria()
                            .and(gitSdkEntityHandlerInterface.getBranchKey())
                            .is(branch)
                            .and(gitSdkEntityHandlerInterface.getYamlGitConfigRefKey())
                            .is(repo);
    if (isFindDefaultFromOtherBranches != null && isFindDefaultFromOtherBranches) {
      return new Criteria().orOperator(criteria,
          Criteria.where(gitSdkEntityHandlerInterface.getIsFromDefaultBranchKey())
              .is(true)
              .and(gitSdkEntityHandlerInterface.getYamlGitConfigRefKey())
              .ne(repo));
    }
    return criteria;
  }

  @Override
  public Criteria getCriteriaWithGitSync(
      String projectIdentifier, String orgIdentifier, String accountId, Class entityClass) {
    if (!"__GLOBAL_ACCOUNT_ID__".equals(accountId) && isGitSyncEnabled(projectIdentifier, orgIdentifier, accountId)) {
      final GitEntityInfo gitBranchInfo = getGitEntityInfo();
      if (gitBranchInfo == null) {
        return createGitSyncCriteriaForRepoAndBranch(null, null, null, entityClass);
      }
      return createGitSyncCriteriaForRepoAndBranch(gitBranchInfo.getYamlGitConfigId(), gitBranchInfo.getBranch(),
          gitBranchInfo.isFindDefaultFromOtherRepos(), entityClass);
    }
    return new Criteria();
  }

  @Override
  public Criteria makeCriteriaGitAware(
      String accountId, String orgIdentifier, String projectIdentifier, Class entityClass, Criteria criteria) {
    Criteria gitSyncCriteria = getCriteriaWithGitSync(projectIdentifier, orgIdentifier, accountId, entityClass);
    return criteria == null ? gitSyncCriteria : new Criteria().andOperator(criteria, gitSyncCriteria);
  }
}
