/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO.EntityReferredByPipelineDetailProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntityDetailWithSetupUsageDetailProtoDTO.PipelineDetailType;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.dto.SetupUsageDetailType;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.rbac.InternalReferredEntityExtractor;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(PowerMockRunner.class)
@Slf4j
@PrepareForTest({NGRestUtils.class})
@OwnedBy(PIPELINE)
public class PipelineSetupUsageHelperTest extends PipelineServiceTestBase {
  @Mock private IdentifierRefProtoDTOHelper identifierRefProtoDTOHelper;
  @Mock private EntitySetupUsageClient entitySetupUsageClient;
  @Mock private Producer eventProducer;
  @Mock private InternalReferredEntityExtractor internalReferredEntityExtractor;
  @InjectMocks private PipelineSetupUsageHelper pipelineSetupUsageHelper;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
    when(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO("accountId", null, null, null))
        .thenReturn(IdentifierRefProtoDTO.newBuilder().build());
    when(internalReferredEntityExtractor.extractInternalEntities(any(), anyListOf(EntityDetail.class)))
        .thenReturn(new ArrayList<>());
  }

  @After
  public void verifyMocks() {
    verifyNoMoreInteractions(eventProducer);
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read file " + filename);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeleteExistingSetupUsages() {
    String account = "account";
    String org = "org";
    String project = "proj";
    String id = "id";
    IdentifierRefProtoDTO pipeIdRef = IdentifierRefProtoDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.newBuilder().setValue(account).build())
                                          .build();
    doReturn(pipeIdRef).when(identifierRefProtoDTOHelper).createIdentifierRefProtoDTO(account, org, project, id);
    pipelineSetupUsageHelper.deleteExistingSetupUsages(account, org, project, id);

    EntitySetupUsageCreateV2DTO entityReferenceDTO =
        EntitySetupUsageCreateV2DTO.newBuilder()
            .setAccountIdentifier(account)
            .setReferredByEntity(EntityDetailProtoDTO.newBuilder()
                                     .setIdentifierRef(pipeIdRef)
                                     .setType(EntityTypeProtoEnum.PIPELINES)
                                     .build())
            .setDeleteOldReferredByRecords(true)
            .build();

    verify(eventProducer)
        .send(Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", account, EventsFrameworkMetadataConstants.ACTION,
                      EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                  .setData(entityReferenceDTO.toByteString())
                  .build());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetReferencesOfPipeline() {
    String filename = "empty-object-and-list.yaml";
    String accountIdentifier = "kmpySmUISimoRrJL6NL73w";
    String orgIdentifier = "default";
    String projectIdentifier = "test";
    String pipelineIdentifier = "pipelinevars1";
    String pipelineYaml = readFile(filename);
    EntityDetail referredByEntity = EntityDetail.builder()
                                        .type(EntityType.PIPELINES)
                                        .entityRef(IdentifierRef.builder()
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .identifier(pipelineIdentifier)
                                                       .scope(Scope.PROJECT)
                                                       .build())
                                        .build();

    Call<ResponseDTO<List<EntitySetupUsageDTO>>> request = mock(Call.class);
    when(entitySetupUsageClient.listAllReferredUsages(anyInt(), anyInt(), anyString(), anyString(), any(), any()))
        .thenReturn(request);
    try {
      List<EntitySetupUsageDTO> list = new ArrayList<>();
      list.add(
          EntitySetupUsageDTO.builder()
              .accountIdentifier(accountIdentifier)
              .referredByEntity(referredByEntity)
              .referredEntity(
                  EntityDetail.builder()
                      .type(EntityType.CONNECTORS)
                      .entityRef(
                          IdentifierRef.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .identifier("DOCKER_NEW_TEST")
                              .scope(Scope.PROJECT)
                              .metadata(Collections.singletonMap(PreFlightCheckMetadata.FQN,
                                  "pipeline.stages.deploy.spec.serviceConfig.serviceDefinition.spec.artifacts.primary.spec.connectorRef"))
                              .build())
                      .build())
              .build());
      Map<String, String> metadata = new HashMap<>();
      metadata.put(PreFlightCheckMetadata.FQN,
          "pipeline.stages.deploy.spec.infrastructure.infrastructureDefinition.spec.connectorRef");
      metadata.put(PreFlightCheckMetadata.EXPRESSION, "<+input>");
      list.add(EntitySetupUsageDTO.builder()
                   .accountIdentifier(accountIdentifier)
                   .referredByEntity(referredByEntity)
                   .referredEntity(EntityDetail.builder()
                                       .type(EntityType.CONNECTORS)
                                       .entityRef(IdentifierRef.builder()
                                                      .accountIdentifier(accountIdentifier)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .identifier("<+input>")
                                                      .scope(Scope.UNKNOWN)
                                                      .metadata(metadata)
                                                      .build())
                                       .build())
                   .build());
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(list)));
      PowerMockito.mockStatic(NGRestUtils.class);
      when(NGRestUtils.getResponseWithRetry(any(), any())).thenReturn(list);
    } catch (IOException ex) {
      log.info("Encountered exception ", ex);
    }
    List<EntityDetail> referencesOfPipeline = pipelineSetupUsageHelper.getReferencesOfPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineYaml, null);
    assertThat(referencesOfPipeline.size()).isEqualTo(2);

    IdentifierRef zeroth = (IdentifierRef) referencesOfPipeline.get(0).getEntityRef();
    IdentifierRef first = (IdentifierRef) referencesOfPipeline.get(1).getEntityRef();
    if (zeroth.getScope().equals(Scope.PROJECT)) {
      assertThat(zeroth.getIdentifier()).isEqualTo("DOCKER_NEW_TEST");
      assertThat(first.getScope()).isEqualTo(Scope.ACCOUNT);
      assertThat(first.getIdentifier()).isEqualTo("conn");
    } else {
      assertThat(zeroth.getIdentifier()).isEqualTo("conn");
      assertThat(first.getScope()).isEqualTo(Scope.PROJECT);
      assertThat(first.getIdentifier()).isEqualTo("DOCKER_NEW_TEST");
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetReferencesOfPipelineForInputSetValidators() {
    String filename = "pipeline-with-input-set-validators.yaml";
    String accountIdentifier = "kmpySmUISimoRrJL6NL73w";
    String orgIdentifier = "default";
    String projectIdentifier = "test";
    String pipelineIdentifier = "Test_Pipline11";
    String pipelineYaml = readFile(filename);

    String inputSetCorrectFile = "input-set-for-validators.yaml";
    String inputSetCorrect = readFile(inputSetCorrectFile);

    pipelineYaml = InputSetMergeHelper.mergeInputSetIntoPipeline(
        pipelineYaml, InputSetYamlHelper.getPipelineComponent(inputSetCorrect), true);
    EntityDetail referredByEntity = EntityDetail.builder()
                                        .type(EntityType.PIPELINES)
                                        .entityRef(IdentifierRef.builder()
                                                       .accountIdentifier(accountIdentifier)
                                                       .orgIdentifier(orgIdentifier)
                                                       .projectIdentifier(projectIdentifier)
                                                       .identifier(pipelineIdentifier)
                                                       .scope(Scope.PROJECT)
                                                       .build())
                                        .build();
    Call<ResponseDTO<List<EntitySetupUsageDTO>>> request = mock(Call.class);
    when(entitySetupUsageClient.listAllReferredUsages(anyInt(), anyInt(), anyString(), anyString(), any(), any()))
        .thenReturn(request);
    List<EntitySetupUsageDTO> list = new ArrayList<>();

    Map<String, String> metadata0 = new HashMap<>();
    metadata0.put(PreFlightCheckMetadata.FQN,
        "pipeline.stages.qaStage.spec.infrastructure.infrastructureDefinition.spec.connectorRef");
    metadata0.put(PreFlightCheckMetadata.EXPRESSION, "<+input>");
    list.add(EntitySetupUsageDTO.builder()
                 .accountIdentifier(accountIdentifier)
                 .referredByEntity(referredByEntity)
                 .referredEntity(EntityDetail.builder()
                                     .type(EntityType.CONNECTORS)
                                     .entityRef(IdentifierRef.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .identifier("<+input>")
                                                    .scope(Scope.UNKNOWN)
                                                    .metadata(metadata0)
                                                    .build())
                                     .build())
                 .build());

    Map<String, String> metadata1 = new HashMap<>();
    metadata1.put(PreFlightCheckMetadata.FQN,
        "pipeline.stages.qaStage.spec.service.serviceDefinition.spec.manifests.baseValues.spec.store.spec.connectorRef");
    metadata1.put(PreFlightCheckMetadata.EXPRESSION, "<+input>");
    list.add(EntitySetupUsageDTO.builder()
                 .accountIdentifier(accountIdentifier)
                 .referredByEntity(referredByEntity)
                 .referredEntity(EntityDetail.builder()
                                     .type(EntityType.CONNECTORS)
                                     .entityRef(IdentifierRef.builder()
                                                    .accountIdentifier(accountIdentifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .identifier("<+input>")
                                                    .scope(Scope.UNKNOWN)
                                                    .metadata(metadata1)
                                                    .build())
                                     .build())
                 .build());
    try {
      when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(list)));
    } catch (IOException e) {
      log.info("Encountered exception ", e);
    }

    PowerMockito.mockStatic(NGRestUtils.class);
    when(NGRestUtils.getResponseWithRetry(any(), any())).thenReturn(list);
    List<EntityDetail> referencesOfPipeline = pipelineSetupUsageHelper.getReferencesOfPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineYaml, null);
    assertThat(referencesOfPipeline.size()).isEqualTo(1);
    IdentifierRef identifierRef = (IdentifierRef) referencesOfPipeline.get(0).getEntityRef();
    assertThat(identifierRef.getIdentifier()).isEqualTo("gitConnDev");
    assertThat(identifierRef.getScope()).isEqualTo(Scope.ACCOUNT);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testPublishSetupUsageEvent() {
    List<EntityDetailProtoDTO> referredEntities = new ArrayList<>();
    EntityDetailProtoDTO secretManagerDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .putMetadata(PreFlightCheckMetadata.FQN, "pipeline.variables.var1")
                                  .build())
            .setType(EntityTypeProtoEnum.SECRETS)
            .build();
    EntityDetailProtoDTO connectorManagerDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTO.newBuilder()
                                  .putMetadata(PreFlightCheckMetadata.FQN, "pipelines.stages.s1")
                                  .build())
            .setType(EntityTypeProtoEnum.CONNECTORS)
            .build();
    referredEntities.add(secretManagerDetails);
    referredEntities.add(connectorManagerDetails);

    EntityDetailWithSetupUsageDetailProtoDTO connectorWithDetails =
        EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
            .setType(SetupUsageDetailType.CONNECTOR_REFERRED_BY_PIPELINE.name())
            .setReferredEntity(connectorManagerDetails)
            .setEntityInPipelineDetail(EntityReferredByPipelineDetailProtoDTO.newBuilder()
                                           .setIdentifier("s1")
                                           .setType(PipelineDetailType.STAGE_IDENTIFIER)
                                           .build())
            .build();

    EntityDetailWithSetupUsageDetailProtoDTO secretWithDetails =
        EntityDetailWithSetupUsageDetailProtoDTO.newBuilder()
            .setType(SetupUsageDetailType.SECRET_REFERRED_BY_PIPELINE.name())
            .setReferredEntity(secretManagerDetails)
            .setEntityInPipelineDetail(EntityReferredByPipelineDetailProtoDTO.newBuilder()
                                           .setIdentifier("var1")
                                           .setType(PipelineDetailType.VARIABLE_NAME)
                                           .build())
            .build();

    PipelineEntity pipelineEntity = PipelineEntity.builder().name("test").accountId("accountId").build();
    EntityDetailProtoDTO pipelineDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(identifierRefProtoDTOHelper.createIdentifierRefProtoDTO(pipelineEntity.getAccountId(),
                pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(),
                pipelineEntity.getIdentifier()))
            .setType(EntityTypeProtoEnum.PIPELINES)
            .setName(pipelineEntity.getName())
            .build();
    EntitySetupUsageCreateV2DTO secretEntityReferenceDTO =
        EntitySetupUsageCreateV2DTO.newBuilder()
            .setAccountIdentifier(pipelineEntity.getAccountId())
            .setReferredByEntity(pipelineDetails)
            .addAllReferredEntityWithSetupUsageDetail(Collections.singletonList(secretWithDetails))
            .setDeleteOldReferredByRecords(true)
            .build();
    EntitySetupUsageCreateV2DTO connectorEntityReferenceDTO =
        EntitySetupUsageCreateV2DTO.newBuilder()
            .setAccountIdentifier(pipelineEntity.getAccountId())
            .setReferredByEntity(pipelineDetails)
            .addAllReferredEntityWithSetupUsageDetail(Collections.singletonList(connectorWithDetails))
            .setDeleteOldReferredByRecords(true)
            .build();

    pipelineSetupUsageHelper.publishSetupUsageEvent(pipelineEntity, referredEntities);

    verify(eventProducer)
        .send(Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                      EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.SECRETS.name(),
                      EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                  .setData(secretEntityReferenceDTO.toByteString())
                  .build());
    verify(eventProducer)
        .send(Message.newBuilder()
                  .putAllMetadata(ImmutableMap.of("accountId", pipelineEntity.getAccountId(),
                      EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE, EntityTypeProtoEnum.CONNECTORS.name(),
                      EventsFrameworkMetadataConstants.ACTION, EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION))
                  .setData(connectorEntityReferenceDTO.toByteString())
                  .build());
  }
}
