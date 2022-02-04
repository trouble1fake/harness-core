/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance.testing;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.utils.RetryUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.validation.constraints.NotNull;
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
public class NoOpGitAwarePersistenceImpl implements GitAwarePersistence {
  @Inject MongoTemplate mongoTemplate;
  @Inject TransactionTemplate transactionTemplate;
  private final RetryPolicy<Object> transactionRetryPolicy = RetryUtils.getRetryPolicy("[Retrying] attempt: {}",
      "[Failed] attempt: {}", ImmutableList.of(TransactionException.class), Duration.ofSeconds(1), 3, log);

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, String yaml, ChangeType changeType, Class<B> entityClass, Supplier functor) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      final B mongoSaveObject = mongoTemplate.save(objectToSave);
      if (functor != null) {
        functor.get();
      }
      return mongoSaveObject;
    }));
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, String yaml, ChangeType changeType, Class<B> entityClass) {
    return mongoTemplate.save(objectToSave);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, ChangeType changeType, Class<B> entityClass) {
    return mongoTemplate.save(objectToSave);
  }

  @Override
  public Criteria getCriteriaWithGitSync(
      String projectIdentifier, String orgIdentifier, String accountId, Class entityClass) {
    return new Criteria();
  }

  @Override
  public Criteria makeCriteriaGitAware(
      String accountId, String orgIdentifier, String projectIdentifier, Class entityClass, Criteria criteria) {
    return criteria == null ? new Criteria() : criteria;
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> Long count(@NotNull Criteria criteria,
      String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    Query query = new Query(criteria).skip(-1).limit(-1);
    return mongoTemplate.count(query, entityClass);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> Optional<B> findOne(@NotNull Criteria criteria,
      String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    final B object = mongoTemplate.findOne(query(criteria), entityClass);
    return Optional.ofNullable(object);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> List<B> find(@NotNull Criteria criteria, Pageable pageable,
      String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    Query query = new Query(criteria).with(pageable);
    return mongoTemplate.find(query, entityClass);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> boolean exists(@NotNull Criteria criteria,
      String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass) {
    Query query = new Query(criteria);
    return mongoTemplate.exists(query, entityClass);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> Optional<B> findOne(
      Criteria criteria, String repo, String branch, Class<B> entityClass) {
    final B object = mongoTemplate.findOne(query(criteria), entityClass);
    return Optional.ofNullable(object);
  }

  @Override
  public <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, ChangeType changeType, Class<B> entityClass, Supplier functor) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      final B mongoSaveObject = mongoTemplate.save(objectToSave);
      if (functor != null) {
        functor.get();
      }
      return mongoSaveObject;
    }));
  }
}
