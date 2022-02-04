/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.ROLE_ASSIGNMENT;
import static io.harness.audit.mapper.AuditEventMapper.fromDTO;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.AuditYamlService;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.Environment;
import io.harness.audit.beans.Principal;
import io.harness.audit.beans.Resource;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScope;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.beans.YamlDiffRecordDTO;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.audit.entities.YamlDiffRecord;
import io.harness.audit.mapper.ResourceMapper;
import io.harness.audit.mapper.ResourceScopeMapper;
import io.harness.audit.repositories.AuditRepository;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.common.beans.KeyValuePair;
import io.harness.ng.core.common.beans.KeyValuePair.KeyValuePairKeys;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
public class AuditServiceImpl implements AuditService {
  private static final long MAXIMUM_ALLOWED_YAML_SIZE = 512L * 512;
  private final TransactionTemplate transactionTemplate;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_TRANSACTION_RETRY_POLICY;

  private final AuditRepository auditRepository;
  private final AuditYamlService auditYamlService;
  private final AuditFilterPropertiesValidator auditFilterPropertiesValidator;

  @Inject
  public AuditServiceImpl(AuditRepository auditRepository, AuditYamlService auditYamlService,
      AuditFilterPropertiesValidator auditFilterPropertiesValidator, TransactionTemplate transactionTemplate) {
    this.auditRepository = auditRepository;
    this.auditYamlService = auditYamlService;
    this.auditFilterPropertiesValidator = auditFilterPropertiesValidator;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Boolean create(AuditEventDTO auditEventDTO) {
    validate(auditEventDTO);
    AuditEvent auditEvent = fromDTO(auditEventDTO);
    try {
      long startTime = System.currentTimeMillis();
      Boolean result = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        AuditEvent savedAuditEvent = auditRepository.save(auditEvent);
        saveYamlDiff(auditEventDTO, savedAuditEvent.getId());
        return true;
      }));
      log.info(String.format("Took %d milliseconds for create audit db operation for insertId %s.",
          System.currentTimeMillis() - startTime, auditEventDTO.getInsertId()));
      return result;
    } catch (DuplicateKeyException ex) {
      log.info("Audit for this entry already exists with id {} and account identifier {}", auditEvent.getInsertId(),
          auditEvent.getResourceScope().getAccountIdentifier());
      return true;
    } catch (Exception e) {
      log.error("Could not audit this event with id {} and account identifier {}", auditEvent.getInsertId(),
          auditEvent.getResourceScope().getAccountIdentifier(), e);
      return false;
    }
  }

  private void validate(AuditEventDTO auditEventDTO) {
    if (auditEventDTO.getYamlDiffRecord() != null) {
      YamlDiffRecordDTO yamlDiffRecordDTO = auditEventDTO.getYamlDiffRecord();
      if (isNotEmpty(yamlDiffRecordDTO.getNewYaml())
          && yamlDiffRecordDTO.getNewYaml().length() > MAXIMUM_ALLOWED_YAML_SIZE) {
        throw new InvalidRequestException("New Yaml size exceeds the maximum allowed limit.");
      }
      if (isNotEmpty(yamlDiffRecordDTO.getOldYaml())
          && yamlDiffRecordDTO.getOldYaml().length() > MAXIMUM_ALLOWED_YAML_SIZE) {
        throw new InvalidRequestException("Old Yaml size exceeds the maximum allowed limit.");
      }
    }
  }

  private void saveYamlDiff(AuditEventDTO auditEventDTO, String auditId) {
    if (auditEventDTO.getYamlDiffRecord() != null) {
      YamlDiffRecord yamlDiffRecord = YamlDiffRecord.builder()
                                          .auditId(auditId)
                                          .accountIdentifier(auditEventDTO.getResourceScope().getAccountIdentifier())
                                          .oldYaml(auditEventDTO.getYamlDiffRecord().getOldYaml())
                                          .newYaml(auditEventDTO.getYamlDiffRecord().getNewYaml())
                                          .timestamp(Instant.ofEpochMilli(auditEventDTO.getTimestamp()))
                                          .build();
      auditYamlService.save(yamlDiffRecord);
    }
  }

  @Override
  public Optional<AuditEvent> get(String accountIdentifier, String auditId) {
    Criteria criteria =
        Criteria.where(AuditEventKeys.id).is(auditId).and(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).is(accountIdentifier);
    return Optional.ofNullable(auditRepository.get(criteria));
  }

  @Override
  public Page<AuditEvent> list(
      String accountIdentifier, PageRequest pageRequest, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    long startTime = System.currentTimeMillis();
    auditFilterPropertiesValidator.validate(accountIdentifier, auditFilterPropertiesDTO);
    Criteria criteria = getFilterCriteria(accountIdentifier, auditFilterPropertiesDTO);
    Page<AuditEvent> result = auditRepository.findAll(criteria, getPageRequest(pageRequest));
    log.info(
        String.format("Took %d milliseconds for list audit db operation.", System.currentTimeMillis() - startTime));
    return result;
  }

  private Criteria getFilterCriteria(String accountIdentifier, AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    List<Criteria> criteriaList = new ArrayList<>();
    criteriaList.add(getBaseScopeCriteria(accountIdentifier));
    if (auditFilterPropertiesDTO == null) {
      return criteriaList.get(0);
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getScopes())) {
      criteriaList.add(getScopeCriteria(auditFilterPropertiesDTO.getScopes()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getResources())) {
      criteriaList.add(getResourceCriteria(auditFilterPropertiesDTO.getResources()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getModules())) {
      criteriaList.add(Criteria.where(AuditEventKeys.module).in(auditFilterPropertiesDTO.getModules()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getActions())) {
      criteriaList.add(Criteria.where(AuditEventKeys.action).in(auditFilterPropertiesDTO.getActions()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getEnvironments())) {
      criteriaList.add(getEnvironmentCriteria(auditFilterPropertiesDTO.getEnvironments()));
    }
    if (isNotEmpty(auditFilterPropertiesDTO.getPrincipals())) {
      criteriaList.add(getPrincipalCriteria(auditFilterPropertiesDTO.getPrincipals()));
    }
    // Temporary filter, to hide audits of resource type ROLE_ASSIGNMENT
    criteriaList.add(Criteria.where(AuditEventKeys.RESOURCE_TYPE_KEY).ne(ROLE_ASSIGNMENT));
    criteriaList.add(
        Criteria.where(AuditEventKeys.timestamp)
            .gte(Instant.ofEpochMilli(
                auditFilterPropertiesDTO.getStartTime() == null ? 0 : auditFilterPropertiesDTO.getStartTime())));
    criteriaList.add(Criteria.where(AuditEventKeys.timestamp)
                         .lte(Instant.ofEpochMilli(auditFilterPropertiesDTO.getEndTime() == null
                                 ? currentTimeMillis()
                                 : auditFilterPropertiesDTO.getEndTime())));
    return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
  }

  private Criteria getBaseScopeCriteria(String accountIdentifier) {
    return Criteria.where(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).is(accountIdentifier);
  }

  private Criteria getScopeCriteria(List<ResourceScopeDTO> resourceScopes) {
    List<Criteria> criteriaList = new ArrayList<>();
    resourceScopes.forEach(resourceScope -> {
      Criteria criteria =
          Criteria.where(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).is(resourceScope.getAccountIdentifier());
      if (isNotEmpty(resourceScope.getOrgIdentifier())) {
        criteria.and(AuditEventKeys.ORG_IDENTIFIER_KEY).is(resourceScope.getOrgIdentifier());
        if (isNotEmpty(resourceScope.getProjectIdentifier())) {
          criteria.and(AuditEventKeys.PROJECT_IDENTIFIER_KEY).is(resourceScope.getProjectIdentifier());
          ResourceScope dbo = ResourceScopeMapper.fromDTO(resourceScope);
          List<KeyValuePair> labels = dbo.getLabels();
          if (isNotEmpty(labels)) {
            List<Criteria> labelsCriteria = new ArrayList<>();
            labels.forEach(label
                -> labelsCriteria.add(Criteria.where(AuditEventKeys.RESOURCE_SCOPE_LABEL_KEY)
                                          .elemMatch(Criteria.where(KeyValuePairKeys.key)
                                                         .is(label.getKey())
                                                         .and(KeyValuePairKeys.value)
                                                         .is(label.getValue()))));
            criteria.andOperator(labelsCriteria.toArray(new Criteria[0]));
          }
        }
      }
      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  private Criteria getResourceCriteria(List<ResourceDTO> resources) {
    List<Criteria> criteriaList = new ArrayList<>();
    resources.forEach(resource -> {
      Criteria criteria = Criteria.where(AuditEventKeys.RESOURCE_TYPE_KEY).is(resource.getType());
      if (isNotEmpty(resource.getIdentifier())) {
        criteria.and(AuditEventKeys.RESOURCE_IDENTIFIER_KEY).is(resource.getIdentifier());
      }
      Resource dbo = ResourceMapper.fromDTO(resource);
      List<KeyValuePair> labels = dbo.getLabels();
      if (isNotEmpty(labels)) {
        List<Criteria> labelsCriteria = new ArrayList<>();
        labels.forEach(label
            -> labelsCriteria.add(Criteria.where(AuditEventKeys.RESOURCE_LABEL_KEY)
                                      .elemMatch(Criteria.where(KeyValuePairKeys.key)
                                                     .is(label.getKey())
                                                     .and(KeyValuePairKeys.value)
                                                     .is(label.getValue()))));
        criteria.andOperator(labelsCriteria.toArray(new Criteria[0]));
      }

      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  private Criteria getPrincipalCriteria(List<Principal> principals) {
    List<Criteria> criteriaList = new ArrayList<>();
    principals.forEach(principal -> {
      Criteria criteria = Criteria.where(AuditEventKeys.PRINCIPAL_TYPE_KEY)
                              .is(principal.getType())
                              .and(AuditEventKeys.PRINCIPAL_IDENTIFIER_KEY)
                              .is(principal.getIdentifier());
      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }

  @Override
  public void purgeAuditsOlderThanTimestamp(String accountIdentifier, Instant timestamp) {
    auditRepository.delete(Criteria.where(AuditEventKeys.timestamp)
                               .lte(timestamp)
                               .and(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                               .is(accountIdentifier));
  }

  @Override
  public Set<String> getUniqueAuditedAccounts() {
    return new HashSet<>(auditRepository.fetchDistinctAccountIdentifiers());
  }

  private Criteria getEnvironmentCriteria(List<Environment> environments) {
    List<Criteria> criteriaList = new ArrayList<>();
    environments.forEach(environment -> {
      Criteria criteria = new Criteria();
      if (environment.getType() != null) {
        criteria.and(AuditEventKeys.ENVIRONMENT_TYPE_KEY).is(environment.getType());
      }
      if (isNotEmpty(environment.getIdentifier())) {
        criteria.and(AuditEventKeys.ENVIRONMENT_IDENTIFIER_KEY).is(environment.getIdentifier());
      }
      criteriaList.add(criteria);
    });
    return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
  }
}
