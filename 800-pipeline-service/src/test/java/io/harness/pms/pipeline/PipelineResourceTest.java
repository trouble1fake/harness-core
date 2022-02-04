/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationAdjacencyListDTO;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.GovernanceService;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JsonSchemaValidationException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.mappers.NodeExecutionToExecutioNodeMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(PIPELINE)
public class PipelineResourceTest extends CategoryTest {
  PipelineResource pipelineResource;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSYamlSchemaService pmsYamlSchemaService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock NodeExecutionToExecutioNodeMapper nodeExecutionToExecutioNodeMapper;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock GovernanceService mockGovernanceService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String PLAN_EXECUTION_ID = "planId";
  private final String STAGE_NODE_ID = "stageNodeId";
  private String yaml;

  PipelineEntity entity;
  PipelineEntity entityWithVersion;
  PipelineExecutionSummaryEntity executionSummaryEntity;
  OrchestrationGraphDTO orchestrationGraph;
  EntityGitDetails entityGitDetails;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    pipelineResource = new PipelineResource(pmsPipelineService, pmsYamlSchemaService, nodeExecutionService,
        nodeExecutionToExecutioNodeMapper, pipelineTemplateHelper, mockGovernanceService, null);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "failure-strategy.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .yaml(yaml)
                 .allowStageExecutions(false)
                 .build();

    entityGitDetails = EntityGitDetails.builder()
                           .branch("branch")
                           .repoIdentifier("repo")
                           .filePath("file.yaml")
                           .rootFolder("root/.harness/")
                           .build();

    entityWithVersion = PipelineEntity.builder()
                            .accountId(ACCOUNT_ID)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .identifier(PIPELINE_IDENTIFIER)
                            .name(PIPELINE_IDENTIFIER)
                            .yaml(yaml)
                            .stageCount(1)
                            .stageName("qaStage")
                            .version(1L)
                            .allowStageExecutions(false)
                            .build();

    executionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                 .planExecutionId(PLAN_EXECUTION_ID)
                                 .name(PLAN_EXECUTION_ID)
                                 .runSequence(0)
                                 .entityGitDetails(entityGitDetails)
                                 .build();

    orchestrationGraph = OrchestrationGraphDTO.builder()
                             .planExecutionId(PLAN_EXECUTION_ID)
                             .rootNodeIds(Collections.singletonList(STAGE_NODE_ID))
                             .adjacencyList(OrchestrationAdjacencyListDTO.builder()
                                                .graphVertexMap(Collections.emptyMap())
                                                .adjacencyMap(Collections.emptyMap())
                                                .build())
                             .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePipeline() throws IOException {
    doReturn(entityWithVersion).when(pmsPipelineService).create(entity);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(entity);
    ResponseDTO<String> identifier =
        pipelineResource.createPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, yaml);
    assertThat(identifier.getData()).isNotEmpty();
    assertThat(identifier.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreatePipelineV2() throws IOException {
    doReturn(entityWithVersion).when(pmsPipelineService).create(entity);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(entity);
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(mockGovernanceService)
        .evaluateGovernancePolicies(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    ResponseDTO<PipelineSaveResponse> responseDTO =
        pipelineResource.createPipelineV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineTemplateHelper, times(1)).resolveTemplateRefsInPipeline(entity);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testCreatePipelineWithSchemaErrors() {
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO).when(pipelineTemplateHelper).resolveTemplateRefsInPipeline(entity);
    doThrow(JsonSchemaValidationException.class)
        .when(pmsYamlSchemaService)
        .validateYamlSchema(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    assertThatThrownBy(() -> pipelineResource.createPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, yaml))
        .isInstanceOf(JsonSchemaValidationException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipeline() {
    doReturn(Optional.of(entityWithVersion))
        .when(pmsPipelineService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    ResponseDTO<PMSPipelineResponseDTO> responseDTO = pipelineResource.getPipelineByIdentifier(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getYamlPipeline()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineWithInvalidPipelineId() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineByIdentifier(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Pipeline with the given ID: %s does not exist or has been deleted", incorrectPipelineIdentifier));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineWithWrongIdentifier() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, null, yaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline identifier in URL does not match pipeline identifier in yaml");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipeline() throws IOException {
    doReturn(entityWithVersion).when(pmsPipelineService).updatePipelineYaml(entity, ChangeType.MODIFY);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    ResponseDTO<String> responseDTO = pipelineResource.updatePipeline(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, yaml);
    assertThat(responseDTO.getData()).isEqualTo(PIPELINE_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdatePipelineV2() throws IOException {
    doReturn(entityWithVersion).when(pmsPipelineService).updatePipelineYaml(entity, ChangeType.MODIFY);
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(mockGovernanceService)
        .evaluateGovernancePolicies(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
    verify(pipelineTemplateHelper, times(1))
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  @Ignore("Ignored till Schema validation is behind FF")
  public void testUpdatePipelineWithSchemaErrors() {
    TemplateMergeResponseDTO templateMergeResponseDTO =
        TemplateMergeResponseDTO.builder().mergedPipelineYaml(yaml).build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    doThrow(JsonSchemaValidationException.class)
        .when(pmsYamlSchemaService)
        .validateYamlSchema(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, yaml);
    assertThatThrownBy(()
                           -> pipelineResource.updatePipeline(
                               null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, yaml))
        .isInstanceOf(JsonSchemaValidationException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeletePipeline() {
    doReturn(true)
        .when(pmsPipelineService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    ResponseDTO<Boolean> deleteResponse =
        pipelineResource.deletePipeline(null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(deleteResponse.getData()).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineSummary() {
    doReturn(Optional.of(entityWithVersion))
        .when(pmsPipelineService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, false);
    ResponseDTO<PMSPipelineSummaryResponseDTO> pipelineSummary =
        pipelineResource.getPipelineSummary(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(pipelineSummary.getData().getName()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(pipelineSummary.getData().getDescription()).isNull();
    assertThat(pipelineSummary.getData().getTags()).isEmpty();
    assertThat(pipelineSummary.getData().getVersion()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getNumOfStages()).isEqualTo(1L);
    assertThat(pipelineSummary.getData().getStageNames().get(0)).isEqualTo("qaStage");
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineSummaryInvalidPipelineId() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";

    doReturn(Optional.empty())
        .when(pmsPipelineService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, false);

    assertThatThrownBy(()
                           -> pipelineResource.getPipelineSummary(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Pipeline with the given ID: %s does not exist or has been deleted", incorrectPipelineIdentifier));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetListOfPipelines() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, PipelineEntityKeys.createdAt));
    Page<PipelineEntity> pipelineEntities = new PageImpl<>(Collections.singletonList(entityWithVersion), pageable, 1);
    doReturn(pipelineEntities).when(pmsPipelineService).list(any(), any(), any(), any(), any(), anyBoolean());
    List<PMSPipelineSummaryResponseDTO> content = pipelineResource
                                                      .getListOfPipelines(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                                                          0, 25, null, null, null, null, null, null, null)
                                                      .getData()
                                                      .getContent();
    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);

    PMSPipelineSummaryResponseDTO responseDTO = content.get(0);
    assertThat(responseDTO.getIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getName()).isEqualTo(PIPELINE_IDENTIFIER);
    assertThat(responseDTO.getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getNumOfStages()).isEqualTo(1L);
    assertThat(responseDTO.getStageNames().get(0)).isEqualTo("qaStage");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetExpandedPipelineJson() {
    doReturn("look, a JSON")
        .when(pmsPipelineService)
        .fetchExpandedPipelineJSON(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER);
    ResponseDTO<ExpandedPipelineJsonDTO> expandedPipelineJson = pipelineResource.getExpandedPipelineJson(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null);
    assertThat(expandedPipelineJson.getData().getExpandedJson()).isEqualTo("look, a JSON");
  }
}
