/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SATYAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.GovernanceService;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineResourceV2Test extends CategoryTest {
  PipelineResourceV2 pipelineResource;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PMSYamlSchemaService pmsYamlSchemaService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock GovernanceService mockGovernanceService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private String yaml;

  PipelineEntity entity;
  PipelineEntity entityWithVersion;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    pipelineResource =
        new PipelineResourceV2(pmsPipelineService, pmsYamlSchemaService, pipelineTemplateHelper, mockGovernanceService);
    yaml = "pipeline:\n  identifier: myPipeline";
    entity = PipelineEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(PIPELINE_IDENTIFIER)
                 .name(PIPELINE_IDENTIFIER)
                 .yaml(yaml)
                 .allowStageExecutions(false)
                 .build();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreatePipelineV2() throws IOException {
    doReturn(entityWithVersion).when(pmsPipelineService).create(entity);
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(mockGovernanceService)
        .evaluateGovernancePolicies(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    ResponseDTO<PipelineSaveResponse> responseDTO =
        pipelineResource.createPipelineV2(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata()).isNotNull();
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdatePipelineV2() throws IOException {
    doReturn(entityWithVersion).when(pmsPipelineService).updatePipelineYaml(entity, ChangeType.MODIFY);
    doReturn(GovernanceMetadata.newBuilder().setDeny(true).build())
        .when(mockGovernanceService)
        .evaluateGovernancePolicies(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    ResponseDTO<PipelineSaveResponse> responseDTO = pipelineResource.updatePipelineV2(
        null, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, yaml);
    assertThat(responseDTO.getData().getGovernanceMetadata().getDeny()).isTrue();
  }
}
