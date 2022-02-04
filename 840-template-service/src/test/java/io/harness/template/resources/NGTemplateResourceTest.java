/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.template.resources.NGTemplateResource.TEMPLATE;

import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.clients.Resource;
import io.harness.accesscontrol.clients.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateSummaryResponseDTO;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariableResponseMapValueProto;
import io.harness.pms.contracts.service.VariablesServiceGrpc;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceBlockingStub;
import io.harness.pms.contracts.service.VariablesServiceRequest;
import io.harness.rule.Owner;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.beans.TemplateDeleteListRequestDTO;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.beans.TemplateWrapperResponseDTO;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.helpers.TemplateMergeHelper;
import io.harness.template.helpers.TemplateReferenceHelper;
import io.harness.template.helpers.TemplateYamlConversionHelper;
import io.harness.template.services.NGTemplateService;
import io.harness.template.services.NGTemplateServiceHelper;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(CDC)
public class NGTemplateResourceTest extends CategoryTest {
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  NGTemplateResource templateResource;
  @Mock NGTemplateService templateService;
  @Mock NGTemplateServiceHelper templateServiceHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock TemplateMergeHelper templateMergeHelper;
  @Inject VariablesServiceBlockingStub variablesServiceBlockingStub;
  @Mock TemplateYamlConversionHelper templateYamlConversionHelper;
  @Mock TemplateReferenceHelper templateReferenceHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";
  private String yaml;

  TemplateEntity entity;
  TemplateEntity entityWithMongoVersion;

  private final VariablesServiceGrpc.VariablesServiceImplBase serviceImpl =
      mock(VariablesServiceGrpc.VariablesServiceImplBase.class,
          AdditionalAnswers.delegatesTo(new VariablesServiceGrpc.VariablesServiceImplBase() {
            @Override
            public void getVariables(
                VariablesServiceRequest request, StreamObserver<VariableMergeResponseProto> responseObserver) {
              Map<String, VariableResponseMapValueProto> metadataMap = new HashMap<>();
              metadataMap.put("v1",
                  VariableResponseMapValueProto.newBuilder()
                      .setYamlOutputProperties(YamlOutputProperties.newBuilder().build())
                      .build());
              VariableMergeResponseProto variableMergeResponseProto =
                  VariableMergeResponseProto.newBuilder().setYaml("temp1").putAllMetadataMap(metadataMap).build();
              responseObserver.onNext(variableMergeResponseProto);
              responseObserver.onCompleted();
            }
          }));

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    // Generate a unique in-process server name.
    String serverName = InProcessServerBuilder.generateName();
    // Create a server, add service, start, and register for automatic graceful shutdown.
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName).directExecutor().addService(serviceImpl).build().start());
    // Create a client channel and register for automatic graceful shutdown.
    ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    // Create a VariablesStub using the in-process channel;
    variablesServiceBlockingStub = VariablesServiceGrpc.newBlockingStub(channel);

    templateResource = new NGTemplateResource(templateService, templateServiceHelper, accessControlClient,
        templateMergeHelper, variablesServiceBlockingStub, templateYamlConversionHelper, templateReferenceHelper);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String filename = "template.yaml";
    yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);

    entity = TemplateEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(TEMPLATE_IDENTIFIER)
                 .name(TEMPLATE_IDENTIFIER)
                 .versionLabel(TEMPLATE_VERSION_LABEL)
                 .yaml(yaml)
                 .description("")
                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                 .childType(TEMPLATE_CHILD_TYPE)
                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                 .templateScope(Scope.PROJECT)
                 .build();

    entityWithMongoVersion = TemplateEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .identifier(TEMPLATE_IDENTIFIER)
                                 .name(TEMPLATE_IDENTIFIER)
                                 .versionLabel(TEMPLATE_VERSION_LABEL)
                                 .yaml(yaml)
                                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                 .childType(TEMPLATE_CHILD_TYPE)
                                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                 .templateScope(Scope.PROJECT)
                                 .version(1L)
                                 .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testCreateTemplate() throws IOException {
    doReturn(entityWithMongoVersion).when(templateService).create(entity, false, "");
    ResponseDTO<TemplateWrapperResponseDTO> responseDTO =
        templateResource.create(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, yaml, false, "");
    assertThat(responseDTO.getData()).isNotNull();
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    assertThat(responseDTO.getData().isValid()).isTrue();
    assertThat(responseDTO.getData().getTemplateResponseDTO().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getTemplateResponseDTO().getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetTemplate() {
    doReturn(Optional.of(entityWithMongoVersion))
        .when(templateService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false);
    ResponseDTO<TemplateResponseDTO> responseDTO = templateResource.get(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, false, null);
    assertThat(responseDTO.getData()).isNotNull();
    assertThat(responseDTO.getData().getVersion()).isEqualTo(1L);
    assertThat(responseDTO.getData().getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetTemplateWithInvalidTemplateIdentifier() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";
    doReturn(Optional.empty())
        .when(templateService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, incorrectPipelineIdentifier, TEMPLATE_VERSION_LABEL, false);
    assertThatThrownBy(()
                           -> templateResource.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               incorrectPipelineIdentifier, TEMPLATE_VERSION_LABEL, false, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTemplate() {
    doReturn(entityWithMongoVersion).when(templateService).updateTemplateEntity(entity, ChangeType.MODIFY, false, "");
    ResponseDTO<TemplateWrapperResponseDTO> responseDTO = templateResource.updateExistingTemplateLabel("", ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, yaml, false, "");
    assertThat(responseDTO.getData()).isNotNull();
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    assertThat(responseDTO.getData().isValid()).isTrue();
    assertThat(responseDTO.getData().getTemplateResponseDTO().getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateStableTemplate() {
    doReturn(entityWithMongoVersion)
        .when(templateService)
        .updateStableTemplateVersion(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, "");
    ResponseDTO<String> responseDTO = templateResource.updateStableTemplate(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "");
    assertThat(responseDTO.getData()).isNotNull();
    assertThat(responseDTO.getData()).isEqualTo(TEMPLATE_VERSION_LABEL);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTemplateWithWrongIdentifier() {
    String incorrectPipelineIdentifier = "notTheIdentifierWeNeed";
    assertThatThrownBy(
        ()
            -> templateResource.updateExistingTemplateLabel("", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                incorrectPipelineIdentifier, TEMPLATE_VERSION_LABEL, null, yaml, false, ""))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteTemplate() {
    doReturn(true)
        .when(templateService)
        .delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "");
    ResponseDTO<Boolean> responseDTO = templateResource.deleteTemplate(
        "", ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, null, "");
    assertThat(responseDTO.getData()).isEqualTo(true);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteTemplateList() {
    Set<String> templateVersions = new HashSet<>();
    templateVersions.add("v1");
    templateVersions.add("v2");
    doReturn(true)
        .when(templateService)
        .deleteTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, templateVersions, "");
    ResponseDTO<Boolean> responseDTO = templateResource.deleteTemplateVersionsOfParticularIdentifier(ACCOUNT_ID,
        ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER,
        TemplateDeleteListRequestDTO.builder().templateVersionLabels(new ArrayList<>(templateVersions)).build(), null,
        "");
    assertThat(responseDTO.getData()).isEqualTo(true);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTemplateSettings() {
    doReturn(true)
        .when(templateService)
        .updateTemplateSettings(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, Scope.PROJECT,
            Scope.ORG, TEMPLATE_VERSION_LABEL, false);
    ResponseDTO<Boolean> responseDTO = templateResource.updateTemplateSettings(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL, Scope.PROJECT, Scope.ORG, null, false);
    assertThat(responseDTO.getData()).isEqualTo(true);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, null),
            Resource.of(TEMPLATE, TEMPLATE_IDENTIFIER), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetListOfTemplates() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, TemplateEntityKeys.createdAt));
    PageImpl<TemplateEntity> templateEntities =
        new PageImpl<>(Collections.singletonList(entityWithMongoVersion), pageable, 1);
    doReturn(templateEntities).when(templateService).list(any(), any(), any(), any(), any(), anyBoolean());
    List<TemplateSummaryResponseDTO> content =
        templateResource
            .listTemplates(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, 0, 25, null, null, null,
                TemplateListType.ALL_TEMPLATE_TYPE, false, null, null, null)
            .getData()
            .getContent();
    assertThat(content).isNotEmpty();
    assertThat(content.size()).isEqualTo(1);

    TemplateSummaryResponseDTO responseDTO = content.get(0);
    assertThat(responseDTO.getIdentifier()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(responseDTO.getName()).isEqualTo(TEMPLATE_IDENTIFIER);
    assertThat(responseDTO.getVersion()).isEqualTo(1L);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateIdentifierIsUnique() {
    doReturn(true)
        .when(templateService)
        .validateIdentifierIsUnique(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL);
    ResponseDTO<Boolean> responseDTO = templateResource.validateTheIdentifierIsUnique(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL);

    assertThat(responseDTO.getData()).isEqualTo(TRUE);
    verify(templateService)
        .validateIdentifierIsUnique(
            ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, TEMPLATE_IDENTIFIER, TEMPLATE_VERSION_LABEL);
  }
}
