/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.StepCategory;
import io.harness.pms.pipeline.StepPalleteFilterWrapper;
import io.harness.pms.variables.VariableMergeServiceResponse;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public interface PMSPipelineService {
  PipelineEntity create(PipelineEntity pipelineEntity);

  Optional<PipelineEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier, boolean deleted);

  Optional<PipelineEntity> getWithoutIsDeleted(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier);

  PipelineEntity updatePipelineYaml(PipelineEntity pipelineEntity, ChangeType changeType);

  PipelineEntity syncPipelineEntityWithGit(EntityDetailProtoDTO entityDetail);

  PipelineEntity updatePipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, Criteria criteria, Update updateOperations);

  void saveExecutionInfo(
      String accountId, String orgId, String projectId, String pipelineId, ExecutionSummaryInfo executionSummaryInfo);

  int incrementRunSequence(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, boolean b);

  int incrementRunSequence(PipelineEntity entity);

  boolean markEntityInvalid(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String invalidYaml);

  boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier, Long version);

  Page<PipelineEntity> list(Criteria criteria, Pageable pageable, String accountId, String orgIdentifier,
      String projectIdentifier, Boolean getDistinctFromBranches);

  PipelineEntity findFirstPipeline(Criteria criteria);

  Long countAllPipelines(Criteria criteria);

  StepCategory getSteps(String module, String category, String accountId);

  StepCategory getStepsV2(String accountId, StepPalleteFilterWrapper stepPalleteFilterWrapper);

  VariableMergeServiceResponse createVariablesResponse(String yaml);

  Criteria formCriteria(String accountId, String orgId, String projectId, String filterIdentifier,
      PipelineFilterPropertiesDto filterProperties, boolean deleted, String module, String searchTerm);

  boolean deleteAllPipelinesInAProject(String accountId, String orgId, String projectId);

  String fetchExpandedPipelineJSON(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  String fetchExpandedPipelineJSONFromYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml);

  PipelineEntity updateGitFilePath(PipelineEntity pipelineEntity, String newFilePath);
}
