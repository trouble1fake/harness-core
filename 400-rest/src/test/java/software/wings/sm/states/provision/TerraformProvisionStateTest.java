/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.FeatureName.TERRAFORM_AWS_CP_AUTHENTICATION;
import static io.harness.beans.SweepingOutputInstance.Scope;
import static io.harness.beans.SweepingOutputInstance.builder;
import static io.harness.context.ContextElementType.TERRAFORM_INHERIT_PLAN;
import static io.harness.provision.TerraformConstants.TF_NAME_PREFIX;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.REPO_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.WorkflowType;
import io.harness.beans.terraform.TerraformPlanParam;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.provision.TfVarScriptRepositorySource;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.TerraformExecutionData;
import software.wings.api.TerraformOutputInfoElement;
import software.wings.api.terraform.TerraformOutputVariables;
import software.wings.api.terraform.TerraformProvisionInheritPlanElement;
import software.wings.api.terraform.TfVarGitSource;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.KmsConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collector;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.mongodb.morphia.query.Query;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class TerraformProvisionStateTest extends WingsBaseTest {
  @Mock InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private DelegateService delegateService;
  @Mock private ActivityService activityService;
  @Mock private GitUtilsManager gitUtilsManager;
  @Mock private FileService fileService;
  @Mock private SecretManager secretManager;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private GitConfigHelperService gitConfigHelperService;
  @Mock private TerraformPlanHelper terraformPlanHelper;
  @Mock private SecretManagerConfigService secretManagerConfigService;
  @Spy private GitFileConfigHelperService gitFileConfigHelperService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ManagerExecutionLogCallback managerExecutionLogCallback;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private SettingsService settingsService;
  @InjectMocks private TerraformProvisionState state = new ApplyTerraformProvisionState("tf");
  @InjectMocks private TerraformProvisionState destroyProvisionState = new DestroyTerraformProvisionState("tf");

  private final Answer<String> answer = invocation -> invocation.getArgumentAt(0, String.class) + "-rendered";

  @Before
  public void setup() {
    BiFunction<String, Collector, Answer> extractVariablesOfType = (type, collector) -> {
      return invocation -> {
        List<NameValuePair> input = invocation.getArgumentAt(0, List.class);
        return input.stream()
            .filter(value -> value.getValue() != null)
            .filter(value -> type.equals(value.getValueType()))
            .collect(collector);
      };
    };
    Answer doExtractTextVariables =
        extractVariablesOfType.apply("TEXT", toMap(NameValuePair::getName, NameValuePair::getValue));
    Answer doExtractEncryptedVariables = extractVariablesOfType.apply("ENCRYPTED_TEXT",
        toMap(NameValuePair::getName, entry -> EncryptedDataDetail.builder().fieldName(entry.getName()).build()));
    Answer<String> doReturnSameValue = invocation -> invocation.getArgumentAt(0, String.class);

    doReturn(Activity.builder().uuid("uuid").build()).when(activityService).save(any(Activity.class));
    doAnswer(doExtractTextVariables)
        .when(infrastructureProvisionerService)
        .extractTextVariables(anyListOf(NameValuePair.class), any(ExecutionContext.class));
    doAnswer(doExtractTextVariables)
        .when(infrastructureProvisionerService)
        .extractUnresolvedTextVariables(anyListOf(NameValuePair.class));
    doAnswer(doExtractEncryptedVariables)
        .when(infrastructureProvisionerService)
        .extractEncryptedTextVariables(anyListOf(NameValuePair.class), anyString(), anyString());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString(), any(StateExecutionContext.class));
    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(Environment.Builder.anEnvironment().build()).when(executionContext).getEnv();
    doReturn(Application.Builder.anApplication().appId(APP_ID).build()).when(executionContext).getApp();
    doReturn(WorkflowStandardParams.Builder.aWorkflowStandardParams()
                 .withCurrentUser(EmbeddedUser.builder().name("name").build())
                 .build())
        .when(executionContext)
        .getContextElement(any(ContextElementType.class));

    doReturn(null).when(executionContext).getContextElement(ContextElementType.TERRAFORM_PROVISION);
    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(builder()).when(executionContext).prepareSweepingOutputBuilder(any(Scope.class));

    when(featureFlagService.isEnabled(eq(FeatureName.EXPORT_TF_PLAN), anyString())).thenReturn(true);
    doReturn(KmsConfig.builder().accountId(ACCOUNT_ID).name("name").build())
        .when(secretManagerConfigService)
        .getSecretManager(any(), any(), any());
    doReturn(KmsConfig.builder().accountId(ACCOUNT_ID).name("name").build())
        .when(secretManagerConfigService)
        .getDefaultSecretManager(any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldParseOutputs() throws IOException {
    assertThat(TerraformProvisionState.parseOutputs(null).size()).isEqualTo(0);
    assertThat(TerraformProvisionState.parseOutputs("").size()).isEqualTo(0);
    assertThat(TerraformProvisionState.parseOutputs("  ").size()).isEqualTo(0);

    File file = new File("400-rest/src/test/resources/software/wings/sm/states/provision/terraform_output.json");
    String json = FileUtils.readFileToString(file, Charset.defaultCharset());

    final Map<String, Object> stringObjectMap = TerraformProvisionState.parseOutputs(json);
    assertThat(stringObjectMap.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldUpdateProvisionerWorkspaces() {
    when(infrastructureProvisionerService.update(any())).thenReturn(null);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().build();
    state.updateProvisionerWorkspaces(provisioner, "w1");
    assertThat(provisioner.getWorkspaces().size() == 1 && provisioner.getWorkspaces().contains("w1")).isTrue();
    state.updateProvisionerWorkspaces(provisioner, "w2");
    assertThat(provisioner.getWorkspaces().size() == 2 && provisioner.getWorkspaces().equals(asList("w1", "w2")))
        .isTrue();
    state.updateProvisionerWorkspaces(provisioner, "w2");
    assertThat(provisioner.getWorkspaces().size() == 2 && provisioner.getWorkspaces().equals(asList("w1", "w2")))
        .isTrue();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldHandleDefaultWorkspace() {
    assertThat(state.handleDefaultWorkspace(null) == null).isTrue();
    assertThat(state.handleDefaultWorkspace("default") == null).isTrue();
    assertThat(state.handleDefaultWorkspace("abc").equals("abc")).isTrue();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidateAndFilterVariables() {
    NameValuePair prov_var_1 = NameValuePair.builder().name("access_key").valueType("TEXT").build();
    NameValuePair prov_var_2 = NameValuePair.builder().name("secret_key").valueType("TEXT").build();

    NameValuePair wf_var_1 = NameValuePair.builder().name("access_key").valueType("TEXT").value("value-1").build();
    NameValuePair wf_var_2 = NameValuePair.builder().name("secret_key").valueType("TEXT").value("value-2").build();
    NameValuePair wf_var_3 = NameValuePair.builder().name("region").valueType("TEXT").value("value-3").build();

    final List<NameValuePair> workflowVars = asList(wf_var_1, wf_var_2, wf_var_3);
    final List<NameValuePair> provVars = asList(prov_var_1, prov_var_2);

    List<NameValuePair> filteredVars_1 = TerraformProvisionState.validateAndFilterVariables(workflowVars, provVars);

    final List<NameValuePair> expected_1 = asList(wf_var_1, wf_var_2);
    assertThat(filteredVars_1).isEqualTo(expected_1);

    wf_var_1.setValueType("ENCRYPTED_TEXT");

    final List<NameValuePair> filteredVars_2 =
        TerraformProvisionState.validateAndFilterVariables(workflowVars, provVars);

    final List<NameValuePair> expected_2 = asList(wf_var_1, wf_var_2);
    assertThat(filteredVars_2).isEqualTo(expected_2);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateAndFilterVariablesEmpty() {
    final List<NameValuePair> workflowVars =
        Collections.singletonList(NameValuePair.builder().name("key").valueType("TEXT").value("value").build());
    final List<NameValuePair> provVars = Collections.emptyList();

    assertThat(TerraformProvisionState.validateAndFilterVariables(workflowVars, provVars)).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testStateTimeout() {
    testTimeoutInternal(new ApplyTerraformProvisionState("tf"));
    testTimeoutInternal(new AdjustTerraformProvisionState("tf"));
    testTimeoutInternal(new DestroyTerraformProvisionState("tf"));
    testTimeoutInternal(new TerraformRollbackState("tf"));
    testTimeoutInternal(new ApplyTerraformState("tf"));
  }

  private void testTimeoutInternal(TerraformProvisionState state) {
    state.setTimeoutMillis(null);
    assertThat(state.getTimeoutMillis()).isNull();

    state.setTimeoutMillis(500);
    assertThat(state.getTimeoutMillis()).isEqualTo(500);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTerraformDestroyStateWithConfiguration() {
    destroyProvisionState.setVariables(getTerraformVariables());
    destroyProvisionState.setBackendConfigs(getTerraformBackendConfigs());
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = destroyProvisionState.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getVariables()).isNotEmpty();
    assertThat(parameters.getBackendConfigs()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
    assertParametersVariables(parameters);
    assertParametersBackendConfigs(parameters);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTerraformDestroyWithConfigurationAndStateFileForNewEntityId() {
    destroyProvisionState.setVariables(getTerraformVariables());
    destroyProvisionState.setBackendConfigs(getTerraformBackendConfigs());
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .kmsId("kmsId")
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .metadata(ImmutableMap.of("variables", ImmutableMap.of("region", "us-west"), "backend_configs",
                ImmutableMap.of("bucket", "tf-remote-state", "key", "old_terraform.tfstate")))
            .build();

    doReturn("fileId").when(fileService).getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE));
    doReturn(fileMetadata).when(fileService).getFileMetadata("fileId", FileBucket.TERRAFORM_STATE);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = destroyProvisionState.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();

    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());
    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getVariables()).isNotEmpty();
    assertThat(parameters.getBackendConfigs()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isFalse();
    assertParametersVariables(parameters);
    assertParametersBackendConfigs(parameters);
    verify(fileService, times(1)).getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteTerraformDestroyWithConfigurationAndStateFileForOldEntityId() {
    destroyProvisionState.setVariables(getTerraformVariables());
    destroyProvisionState.setBackendConfigs(getTerraformBackendConfigs());
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .kmsId("kmsId")
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .metadata(ImmutableMap.of("variables", ImmutableMap.of("region", "us-west"), "backend_configs",
                ImmutableMap.of("bucket", "tf-remote-state", "key", "old_terraform.tfstate")))
            .build();

    when(fileService.getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE)))
        .thenReturn(null)
        .thenReturn("fileId");
    doReturn(fileMetadata).when(fileService).getFileMetadata("fileId", FileBucket.TERRAFORM_STATE);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = destroyProvisionState.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();

    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());
    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getVariables()).isNotEmpty();
    assertThat(parameters.getBackendConfigs()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isFalse();
    assertParametersVariables(parameters);
    assertParametersBackendConfigs(parameters);
    verify(fileService, times(2)).getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTerraformDestroyUsingFileMetaData() {
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .metadata(
                ImmutableMap.<String, Object>builder()
                    .put("variables", ImmutableMap.of("region", "us-east", "vpc_id", "vpc-id"))
                    .put("encrypted_variables", ImmutableMap.of("access_key", "access_key", "secret_key", "secret_key"))
                    .put("backend_configs", ImmutableMap.of("bucket", "tf-remote-state", "key", "terraform.tfstate"))
                    .put("encrypted_backend_configs", ImmutableMap.of("access_token", "access_token"))
                    .put("environment_variables", ImmutableMap.of("TF_LOG", "TRACE"))
                    .put("encrypted_environment_variables", ImmutableMap.of("access_token", "access_token"))
                    .put("targets", asList("target1", "target2"))
                    .put("tf_var_files", asList("file1", "file2"))
                    .build())
            .build();

    when(fileService.getLatestFileIdByQualifier(anyString(), eq(FileBucket.TERRAFORM_STATE), eq("apply")))
        .thenReturn(null)
        .thenReturn("fileId");
    doReturn(fileMetadata).when(fileService).getFileMetadata("fileId", FileBucket.TERRAFORM_STATE);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    doAnswer(invocation -> invocation.getArgumentAt(0, String.class) + "-rendered")
        .when(executionContext)
        .renderExpression(anyString());
    ExecutionResponse response = destroyProvisionState.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getVariables()).isNotEmpty();
    assertThat(parameters.getBackendConfigs()).isNotEmpty();
    assertThat(parameters.getEnvironmentVariables()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
    assertParametersVariables(parameters);
    assertParametersBackendConfigs(parameters);
    assertParametersEnvironmentVariables(parameters);
    assertThat(parameters.getTfVarFiles()).containsExactlyInAnyOrder("file1-rendered", "file2-rendered");
    assertThat(parameters.getTfVarSource()).isNotNull();
    TfVarScriptRepositorySource source = (TfVarScriptRepositorySource) parameters.getTfVarSource();
    assertThat(source.getTfVarFilePaths()).containsExactlyInAnyOrder("file1-rendered", "file2-rendered");
    assertThat(parameters.getTargets()).containsExactlyInAnyOrder("target1-rendered", "target2-rendered");
    verify(fileService, times(2)).getLatestFileIdByQualifier(anyString(), eq(FileBucket.TERRAFORM_STATE), eq("apply"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTerraformDestroyWithOnlyBackendConfigs() {
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    destroyProvisionState.setBackendConfigs(getTerraformBackendConfigs());
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .skipRefreshBeforeApplyingPlan(false)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = destroyProvisionState.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getBackendConfigs()).isNotEmpty();
    assertThat(parameters.getVariables()).isEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isFalse();
    assertParametersBackendConfigs(parameters);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInheritApprovedPlan() {
    testExecuteInheritApprovedPlan(false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteInheritApprovedPlan_optimizedTfPlan() {
    testExecuteInheritApprovedPlan(true);
  }

  private void testExecuteInheritApprovedPlan(boolean useOptimizedTfPlan) {
    state.setInheritApprovedPlan(true);
    EncryptedRecordData encryptedPlan =
        EncryptedRecordData.builder().name("terraformPlan").encryptedValue("terraformPlan".toCharArray()).build();
    List<ContextElement> terraformProvisionInheritPlanElements = new ArrayList<>();
    TfVarGitSource tfVarGitSource = TfVarGitSource.builder().gitFileConfig(new GitFileConfig()).build();
    TerraformProvisionInheritPlanElement terraformProvisionInheritPlanElement =
        TerraformProvisionInheritPlanElement.builder()
            .provisionerId(PROVISIONER_ID)
            .workspace("workspace")
            .sourceRepoReference("sourceRepoReference")
            .backendConfigs(getTerraformBackendConfigs())
            .environmentVariables(getTerraformEnvironmentVariables())
            .targets(Arrays.asList("target1"))
            .variables(getTerraformVariables())
            .tfVarSource(tfVarGitSource)
            .encryptedTfPlan(useOptimizedTfPlan ? null : encryptedPlan)
            .build();
    terraformProvisionInheritPlanElements.add(terraformProvisionInheritPlanElement);
    when(executionContext.getContextElementList(TERRAFORM_INHERIT_PLAN))
        .thenReturn(terraformProvisionInheritPlanElements);

    when(executionContext.getAppId()).thenReturn(APP_ID);
    doReturn(Environment.Builder.anEnvironment().build()).when(executionContext).getEnv();
    doReturn(useOptimizedTfPlan).when(featureFlagService).isEnabled(eq(FeatureName.OPTIMIZED_TF_PLAN), anyString());
    state.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .sourceRepoBranch("sourceRepoBranch")
                                                         .kmsId("kmsId")
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("fileId").when(fileService).getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE));
    doReturn(ACCOUNT_ID).when(executionContext).getAccountId();
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    when(secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, WORKFLOW_EXECUTION_ID))
        .thenReturn(encryptedDataDetails);
    if (useOptimizedTfPlan) {
      when(terraformPlanHelper.getEncryptedTfPlanFromSweepingOutput(
               executionContext, format(TF_NAME_PREFIX, executionContext.getWorkflowExecutionId())))
          .thenReturn(encryptedPlan);
    }

    ExecutionResponse executionResponse = state.execute(executionContext);
    ArgumentCaptor<DelegateTask> delegateTaskCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    verify(gitConfigHelperService, times(2)).convertToRepoGitConfig(any(GitConfig.class), anyString());
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(fileService, times(1)).getLatestFileId(anyString(), any(FileBucket.class));
    verify(gitUtilsManager, times(2)).getGitConfig(anyString());
    verify(infrastructureProvisionerService, times(1)).extractTextVariables(anyList(), any(ExecutionContext.class));
    // once for environment variables, once for variables, once for backend configs
    verify(infrastructureProvisionerService, times(3))
        .extractEncryptedTextVariables(anyList(), eq(APP_ID), anyString());
    // once for environment variables, once for variables
    verify(infrastructureProvisionerService, times(2)).extractUnresolvedTextVariables(anyList());
    verify(secretManager, times(2)).getEncryptionDetails(any(GitConfig.class), anyString(), anyString());
    verify(secretManagerConfigService, times(1)).getSecretManager(anyString(), anyString(), anyBoolean());
    verify(delegateService, times(1)).queueTask(delegateTaskCaptor.capture());
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo("uuid");
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo("uuid");
    assertThat(delegateTaskCaptor.getValue().getData()).isNotNull();
    assertThat(delegateTaskCaptor.getValue().getData().getParameters().length).isEqualTo(1);
    TerraformProvisionParameters parameters =
        (TerraformProvisionParameters) delegateTaskCaptor.getValue().getData().getParameters()[0];
    assertThat(parameters.getEncryptedTfPlan()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritApprovedPlanWithStateFileForOldEntityId() {
    state.setInheritApprovedPlan(true);
    EncryptedRecordData encryptedPlan =
        EncryptedRecordData.builder().name("terraformPlan").encryptedValue("terraformPlan".toCharArray()).build();
    List<ContextElement> terraformProvisionInheritPlanElements = new ArrayList<>();
    TerraformProvisionInheritPlanElement terraformProvisionInheritPlanElement =
        TerraformProvisionInheritPlanElement.builder()
            .provisionerId(PROVISIONER_ID)
            .workspace("workspace")
            .sourceRepoReference("sourceRepoReference")
            .backendConfigs(getTerraformBackendConfigs())
            .environmentVariables(getTerraformEnvironmentVariables())
            .targets(Arrays.asList("target1"))
            .variables(getTerraformVariables())
            .encryptedTfPlan(encryptedPlan)
            .build();
    terraformProvisionInheritPlanElements.add(terraformProvisionInheritPlanElement);
    when(executionContext.getContextElementList(TERRAFORM_INHERIT_PLAN))
        .thenReturn(terraformProvisionInheritPlanElements);

    when(executionContext.getAppId()).thenReturn(APP_ID);
    doReturn(Environment.Builder.anEnvironment().build()).when(executionContext).getEnv();
    state.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .sourceRepoBranch("sourceRepoBranch")
                                                         .kmsId("kmsId")
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    when(fileService.getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE)))
        .thenReturn(null)
        .thenReturn("fileId");
    doReturn(ACCOUNT_ID).when(executionContext).getAccountId();
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    when(secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, WORKFLOW_EXECUTION_ID))
        .thenReturn(encryptedDataDetails);
    ExecutionResponse executionResponse = state.execute(executionContext);

    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(gitUtilsManager, times(1)).getGitConfig(anyString());
    verify(infrastructureProvisionerService, times(1)).extractTextVariables(anyList(), any(ExecutionContext.class));
    // once for environment variables, once for variables, once for backend configs
    verify(infrastructureProvisionerService, times(3))
        .extractEncryptedTextVariables(anyList(), eq(APP_ID), anyString());
    // once for environment variables, once for variables
    verify(infrastructureProvisionerService, times(2)).extractUnresolvedTextVariables(anyList());
    verify(secretManager, times(1)).getEncryptionDetails(any(GitConfig.class), anyString(), anyString());
    verify(secretManagerConfigService, times(1)).getSecretManager(anyString(), anyString(), anyBoolean());
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo("uuid");
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo("uuid");
    verify(fileService, times(2)).getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteInternalInheritedInvalid() {
    state.setInheritApprovedPlan(true);
    state.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    // No previous terraform plan executed
    doReturn(Collections.emptyList()).when(executionContext).getContextElementList(TERRAFORM_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No previous Terraform plan execution found");

    // No previous terraform plan executed for PROVISIONER_ID
    doReturn(
        Collections.singletonList(TerraformProvisionInheritPlanElement.builder().provisionerId("NOT_THIS_ID").build()))
        .when(executionContext)
        .getContextElementList(TERRAFORM_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No Terraform provision command found with current provisioner");

    // Invalid provisioner path
    doReturn(null).when(executionContext).renderExpression("current/working/directory");
    doReturn(
        Collections.singletonList(TerraformProvisionInheritPlanElement.builder().provisionerId(PROVISIONER_ID).build()))
        .when(executionContext)
        .getContextElementList(TERRAFORM_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid Terraform script path");

    // Empty source repo reference
    doReturn("current/working/directory").when(executionContext).renderExpression("current/working/directory");
    doReturn(Collections.singletonList(TerraformProvisionInheritPlanElement.builder()
                                           .sourceRepoReference(null)
                                           .provisionerId(PROVISIONER_ID)
                                           .build()))
        .when(executionContext)
        .getContextElementList(TERRAFORM_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No commit id found in context inherit tf plan element");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRunPlanOnly() {
    state.setRunPlanOnly(true);
    state.setProvisionerId(PROVISIONER_ID);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData =
        TerraformExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).tfPlanJson("").build();
    response.put("activityId", terraformExecutionData);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));

    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(terraformExecutionData);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(
        ((TerraformProvisionInheritPlanElement) executionResponse.getContextElements().get(0)).getProvisionerId())
        .isEqualTo(PROVISIONER_ID);
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    // once for saving the tf plan json variable
    verify(sweepingOutputService, times(1)).save(any(SweepingOutputInstance.class));
    verify(executionContext, times(1)).prepareSweepingOutputBuilder(eq(Scope.PIPELINE));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSavePlan() {
    state.setRunPlanOnly(true);
    state.setExportPlanToApplyStep(true);
    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId",
        TerraformExecutionData.builder()
            .encryptedTfPlan(EncryptedRecordData.builder().build())
            .tfPlanJson("{}")
            .build());
    doReturn("workflowExecutionId").when(executionContext).getWorkflowExecutionId();
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(TerraformInfrastructureProvisioner.builder().build())
        .when(infrastructureProvisionerService)
        .get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));
    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);
    assertThat(
        ((TerraformProvisionInheritPlanElement) executionResponse.getContextElements().get(0)).getProvisionerId())
        .isEqualTo(PROVISIONER_ID);
    // for saving encrypted in sweepingOutput
    verify(terraformPlanHelper, times(1)).saveEncryptedTfPlanToSweepingOutput(any(), any(), any());
    // for saving the tfplan json variable
    verify(sweepingOutputService, times(1)).save(any(SweepingOutputInstance.class));
    verify(executionContext, times(1)).prepareSweepingOutputBuilder(eq(Scope.PIPELINE));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRegularFail() {
    state.setProvisionerId(PROVISIONER_ID);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData =
        TerraformExecutionData.builder().executionStatus(ExecutionStatus.FAILED).build();
    response.put("activityId", terraformExecutionData);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);

    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);

    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(terraformExecutionData);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteRegularWithEnvironmentVariables() {
    state.setProvisionerId(PROVISIONER_ID);
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
            NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType(null).build());
    state.setEnvironmentVariables(nameValuePairList);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .environmentVariables(getTerraformEnvironmentVariables())
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = state.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getEnvironmentVariables()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
    assertParametersEnvironmentVariables(parameters);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteRegularWithEnvironmentVariablesAwsCPAuthEnabled() {
    state.setProvisionerId(PROVISIONER_ID);
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
            NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType(null).build());
    state.setEnvironmentVariables(nameValuePairList);
    state.setAwsConfigId("UUID");
    state.setAwsRoleArn("arn");
    state.setAwsRegion("region");
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .environmentVariables(getTerraformEnvironmentVariables())
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList();
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setUuid("UUID");
    settingAttribute.setValue(new AwsConfig());

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    doReturn(true).when(featureFlagService).isEnabled(eq(TERRAFORM_AWS_CP_AUTHENTICATION), any());
    doReturn(settingAttribute).when(settingsService).get(any());
    doReturn(encryptionDetails).when(secretManager).getEncryptionDetails(any(), any(), any());
    ExecutionResponse response = state.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getEnvironmentVariables()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
    assertParametersEnvironmentVariables(parameters);
    assertThat(parameters.getAwsConfigId()).isEqualTo("UUID");
    assertThat(parameters.getAwsConfig()).isEqualTo(settingAttribute.getValue());
    assertThat(parameters.getAwsRoleArn()).isEqualTo("arn");
    assertThat(parameters.getAwsRegion()).isEqualTo("region");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteRegularWithEnvironmentVariablesAwsCPAuthEnabledTemplate() {
    state.setProvisionerId(PROVISIONER_ID);
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
            NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType(null).build());
    state.setEnvironmentVariables(nameValuePairList);
    List<TemplateExpression> templateExpressions = Collections.singletonList(new TemplateExpression());
    state.setTemplateExpressions(templateExpressions);
    state.setAwsRegion("region");
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .environmentVariables(getTerraformEnvironmentVariables())
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList();
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setUuid("UUID");
    settingAttribute.setValue(new AwsConfig());

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    doReturn(true).when(featureFlagService).isEnabled(eq(TERRAFORM_AWS_CP_AUTHENTICATION), any());
    doReturn(encryptionDetails).when(secretManager).getEncryptionDetails(any(), any(), any());
    doReturn(new TemplateExpression())
        .when(templateExpressionProcessor)
        .getTemplateExpression(any(), eq("awsConfigId"));
    doReturn(settingAttribute).when(templateExpressionProcessor).resolveSettingAttributeByNameOrId(any(), any(), any());
    doReturn("arn").when(executionContext).renderExpression(any());

    ExecutionResponse response = state.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getEnvironmentVariables()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
    assertParametersEnvironmentVariables(parameters);
    assertThat(parameters.getAwsConfigId()).isEqualTo("UUID");
    assertThat(parameters.getAwsConfig()).isEqualTo(settingAttribute.getValue());
    assertThat(parameters.getAwsRoleArn()).isEqualTo("arn");
    assertThat(parameters.getAwsRegion()).isEqualTo("region");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritApprovedPlanAwsCPAuthEnabled() {
    state.setInheritApprovedPlan(true);
    EncryptedRecordData encryptedPlan =
        EncryptedRecordData.builder().name("terraformPlan").encryptedValue("terraformPlan".toCharArray()).build();
    List<ContextElement> terraformProvisionInheritPlanElements = new ArrayList<>();
    TfVarGitSource tfVarGitSource = TfVarGitSource.builder().gitFileConfig(new GitFileConfig()).build();
    TerraformProvisionInheritPlanElement terraformProvisionInheritPlanElement =
        TerraformProvisionInheritPlanElement.builder()
            .provisionerId(PROVISIONER_ID)
            .workspace("workspace")
            .sourceRepoReference("sourceRepoReference")
            .backendConfigs(getTerraformBackendConfigs())
            .environmentVariables(getTerraformEnvironmentVariables())
            .targets(Arrays.asList("target1"))
            .variables(getTerraformVariables())
            .tfVarSource(tfVarGitSource)
            .encryptedTfPlan(encryptedPlan)
            .build();
    state.setAwsConfigId("UUID");
    state.setAwsRoleArn("arn");
    state.setAwsRegion("region");
    List<EncryptedDataDetail> encryptionDetails = new ArrayList();
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setUuid("UUID");
    settingAttribute.setValue(new AwsConfig());
    terraformProvisionInheritPlanElements.add(terraformProvisionInheritPlanElement);
    when(executionContext.getContextElementList(TERRAFORM_INHERIT_PLAN))
        .thenReturn(terraformProvisionInheritPlanElements);

    when(executionContext.getAppId()).thenReturn(APP_ID);
    doReturn(Environment.Builder.anEnvironment().build()).when(executionContext).getEnv();
    state.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .sourceRepoBranch("sourceRepoBranch")
                                                         .kmsId("kmsId")
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("fileId").when(fileService).getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE));
    doReturn(ACCOUNT_ID).when(executionContext).getAccountId();
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    when(secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, WORKFLOW_EXECUTION_ID))
        .thenReturn(encryptedDataDetails);
    doReturn(true).when(featureFlagService).isEnabled(eq(TERRAFORM_AWS_CP_AUTHENTICATION), any());
    doReturn(settingAttribute).when(settingsService).get(any());
    doReturn(encryptionDetails).when(secretManager).getEncryptionDetails(any(), any(), any());
    ExecutionResponse executionResponse = state.execute(executionContext);
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    verify(gitConfigHelperService, times(2)).convertToRepoGitConfig(any(GitConfig.class), anyString());
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(fileService, times(1)).getLatestFileId(anyString(), any(FileBucket.class));
    verify(gitUtilsManager, times(2)).getGitConfig(anyString());
    verify(infrastructureProvisionerService, times(1)).extractTextVariables(anyList(), any(ExecutionContext.class));
    // once for environment variables, once for variables, once for backend configs
    verify(infrastructureProvisionerService, times(3))
        .extractEncryptedTextVariables(anyList(), eq(APP_ID), anyString());
    // once for environment variables, once for variables
    verify(infrastructureProvisionerService, times(2)).extractUnresolvedTextVariables(anyList());
    verify(secretManager, times(3)).getEncryptionDetails(any(GitConfig.class), anyString(), anyString());
    verify(secretManagerConfigService, times(1)).getSecretManager(anyString(), anyString(), anyBoolean());
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo("uuid");
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo("uuid");
    assertThat(parameters.getAwsConfigId()).isEqualTo("UUID");
    assertThat(parameters.getAwsConfig()).isEqualTo(settingAttribute.getValue());
    assertThat(parameters.getAwsRoleArn()).isEqualTo("arn");
    assertThat(parameters.getAwsRegion()).isEqualTo("region");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRegularDestroy() {
    TerraformProvisionState destroyProvisionStateSpy = spy(destroyProvisionState);
    destroyProvisionStateSpy.setProvisionerId(PROVISIONER_ID);
    destroyProvisionStateSpy.setTargets(Arrays.asList("target1"));
    when(executionContext.getAppId()).thenReturn(APP_ID);
    String outputs = "{\n"
        + "\"key\": {\n"
        + "\"value\": \"value1\"\n"
        + "}\n"
        + "}";
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData = TerraformExecutionData.builder()
                                                        .workspace("workspace")
                                                        .executionStatus(ExecutionStatus.SUCCESS)
                                                        .activityId(ACTIVITY_ID)
                                                        .outputs(outputs)
                                                        .build();
    response.put("activityId", terraformExecutionData);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    when(executionContext.getContextElement(ContextElementType.TERRAFORM_PROVISION))
        .thenReturn(TerraformOutputInfoElement.builder().build());
    when(infrastructureProvisionerService.getManagerExecutionCallback(anyString(), anyString(), anyString()))
        .thenReturn(mock(ManagerExecutionLogCallback.class));
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());

    ExecutionResponse executionResponse = destroyProvisionStateSpy.handleAsyncResponse(executionContext, response);

    verify(infrastructureProvisionerService, times(1))
        .regenerateInfrastructureMappings(
            anyString(), any(ExecutionContext.class), anyMap(), any(Optional.class), any(Optional.class));
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(terraformExecutionData);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    TerraformOutputInfoElement terraformOutputInfoElement =
        (TerraformOutputInfoElement) executionResponse.getContextElements().get(0);
    assertThat(terraformOutputInfoElement.paramMap(executionContext)).containsKeys("terraform");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void handleAsyncResponseInternalRegularDeleteJsonPlanFile() {
    TerraformProvisionState applyStateSpy = spy(state);
    applyStateSpy.setProvisionerId(PROVISIONER_ID);
    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(ACCOUNT_ID).when(executionContext).getAccountId();

    doReturn(TerraformOutputInfoElement.builder().build())
        .when(executionContext)
        .getContextElement(ContextElementType.TERRAFORM_PROVISION);
    doReturn(
        Arrays.asList(
            TerraformProvisionInheritPlanElement.builder().provisionerId(PROVISIONER_ID).tfPlanJsonFileId(null).build(),
            TerraformProvisionInheritPlanElement.builder().provisionerId("random1245").build(),
            TerraformProvisionInheritPlanElement.builder()
                .tfPlanJsonFileId("tfPlanJsonFileId")
                .provisionerId(PROVISIONER_ID)
                .build()))
        .when(executionContext)
        .getContextElementList(TERRAFORM_INHERIT_PLAN);

    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(true).when(featureFlagService).isEnabled(FeatureName.OPTIMIZED_TF_PLAN, ACCOUNT_ID);

    TerraformExecutionData terraformExecutionData = TerraformExecutionData.builder()
                                                        .workspace("workspace")
                                                        .executionStatus(ExecutionStatus.SUCCESS)
                                                        .activityId(ACTIVITY_ID)
                                                        .build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", terraformExecutionData);

    applyStateSpy.handleAsyncResponse(executionContext, response);
    verify(fileService, times(1)).deleteFile("tfPlanJsonFileId", FileBucket.TERRAFORM_PLAN_JSON);
  }

  private void assertParametersVariables(TerraformProvisionParameters parameters) {
    assertThat(parameters.getVariables().keySet()).containsExactlyInAnyOrder("region", "vpc_id");
    assertThat(parameters.getVariables().values()).containsExactlyInAnyOrder("us-east", "vpc-id");
    assertThat(parameters.getEncryptedVariables().keySet()).containsExactlyInAnyOrder("access_key", "secret_key");
  }

  private void assertParametersBackendConfigs(TerraformProvisionParameters parameters) {
    assertThat(parameters.getBackendConfigs().keySet()).containsExactlyInAnyOrder("key", "bucket");
    assertThat(parameters.getBackendConfigs().values())
        .containsExactlyInAnyOrder("terraform.tfstate", "tf-remote-state");
    assertThat(parameters.getEncryptedBackendConfigs().keySet()).containsExactlyInAnyOrder("access_token");
  }

  private void assertParametersEnvironmentVariables(TerraformProvisionParameters parameters) {
    assertThat(parameters.getEnvironmentVariables().keySet()).containsOnly("TF_LOG");
    assertThat(parameters.getEnvironmentVariables().values()).containsOnly("TRACE");
    assertThat(parameters.getEncryptedEnvironmentVariables().keySet()).containsExactlyInAnyOrder("access_token");
  }

  private List<NameValuePair> getTerraformVariables() {
    return asList(NameValuePair.builder().name("region").value("us-east").valueType("TEXT").build(),
        NameValuePair.builder().name("vpc_id").value("vpc-id").valueType("TEXT").build(),
        NameValuePair.builder().name("access_key").value("access_key").valueType("ENCRYPTED_TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerraformBackendConfigs() {
    return asList(NameValuePair.builder().name("key").value("terraform.tfstate").valueType("TEXT").build(),
        NameValuePair.builder().name("bucket").value("tf-remote-state").valueType("TEXT").build(),
        NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerraformEnvironmentVariables() {
    return asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
        NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSaveUserInputs() {
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build(),
            NameValuePair.builder().name("password").value("password").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType(null).build());

    TerraformExecutionData responseData = TerraformExecutionData.builder()
                                              .executionStatus(ExecutionStatus.SUCCESS)
                                              .workspace("workspace")
                                              .targets(Collections.emptyList())
                                              .backendConfigs(nameValuePairList)
                                              .variables(nameValuePairList)
                                              .tfVarFiles(asList("file-1", "file-2"))
                                              .targets(asList("target1", "target2"))
                                              .build();

    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .build();
    Map<String, ResponseData> responseMap = ImmutableMap.of(ACTIVITY_ID, responseData);
    state.setRunPlanOnly(false);
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    state.handleAsyncResponse(executionContext, responseMap);
    ArgumentCaptor<Map> othersArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence, times(1)).save(any(TerraformConfig.class));
    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(eq(PhaseStep.class), isNull(String.class), isNull(Integer.class),
            isNull(String.class), othersArgumentCaptor.capture(), eq(FileBucket.TERRAFORM_STATE));
    Map<String, Object> storeOthersMap = (Map<String, Object>) othersArgumentCaptor.getValue();

    Map<String, String> expectedEncryptedNameValuePair = ImmutableMap.of("password", "password");
    assertThat(storeOthersMap.get("variables")).isEqualTo(ImmutableMap.of("key", "value", "noValueType", "value"));
    assertThat(storeOthersMap.get("encrypted_variables")).isEqualTo(expectedEncryptedNameValuePair);
    assertThat(storeOthersMap.get("backend_configs")).isEqualTo(ImmutableMap.of("key", "value"));
    assertThat(storeOthersMap.get("encrypted_backend_configs")).isEqualTo(expectedEncryptedNameValuePair);
    assertThat(storeOthersMap.get("targets")).isEqualTo(responseData.getTargets());
    assertThat(storeOthersMap.get("tf_var_files")).isEqualTo(responseData.getTfVarFiles());
    assertThat(storeOthersMap.get("tf_workspace")).isEqualTo(responseData.getWorkspace());

    // Don't save terraform config if status is not SUCCESS
    reset(wingsPersistence);
    responseData.setExecutionStatus(ExecutionStatus.FAILED);
    state.handleAsyncResponse(executionContext, responseMap);
    verify(wingsPersistence, never()).save(any(TerraformConfig.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSaveEnvironmentVariables() {
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build(),
            NameValuePair.builder().name("password").value("password").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType(null).build());

    TerraformExecutionData responseData = TerraformExecutionData.builder()
                                              .executionStatus(ExecutionStatus.SUCCESS)
                                              .environmentVariables(nameValuePairList)
                                              .build();

    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    Map<String, ResponseData> responseMap = ImmutableMap.of(ACTIVITY_ID, responseData);
    state.setRunPlanOnly(false);
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    state.handleAsyncResponse(executionContext, responseMap);
    ArgumentCaptor<Map> othersArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence, times(1)).save(any(TerraformConfig.class));
    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(eq(PhaseStep.class), isNull(String.class), isNull(Integer.class),
            isNull(String.class), othersArgumentCaptor.capture(), eq(FileBucket.TERRAFORM_STATE));
    Map<String, Object> storeOthersMap = (Map<String, Object>) othersArgumentCaptor.getValue();

    Map<String, String> expectedEncryptedNameValuePair = ImmutableMap.of("password", "password");
    assertThat(storeOthersMap.get("environment_variables")).isEqualTo(ImmutableMap.of("key", "value"));
    assertThat(storeOthersMap.get("encrypted_environment_variables")).isEqualTo(expectedEncryptedNameValuePair);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSaveDestroyStepUserInputs() {
    TerraformExecutionData responseData = TerraformExecutionData.builder()
                                              .executionStatus(ExecutionStatus.SUCCESS)
                                              .workspace("workspace")
                                              .targets(asList("target1", "target2"))
                                              .build();

    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(getTerraformVariables())
                                                         .build();
    Map<String, ResponseData> responseMap = ImmutableMap.of(ACTIVITY_ID, responseData);
    destroyProvisionState.setRunPlanOnly(false);
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    // Save terraform config if there any targets
    destroyProvisionState.setTargets(asList("target1", "target2"));
    destroyProvisionState.handleAsyncResponse(executionContext, responseMap);
    verify(wingsPersistence, times(1)).save(any(TerraformConfig.class));
    verify(wingsPersistence, never()).delete(any(Query.class));

    // Delete terraform config if no any targets, try to delete with old entityId
    reset(wingsPersistence);
    doReturn(mock(Query.class)).when(wingsPersistence).createQuery(any());
    when(wingsPersistence.delete(any(Query.class))).thenReturn(false);
    destroyProvisionState.setTargets(Collections.emptyList());
    destroyProvisionState.handleAsyncResponse(executionContext, responseMap);
    verify(wingsPersistence, never()).save(any(TerraformConfig.class));
    verify(wingsPersistence, times(2)).delete(any(Query.class));

    // Delete terraform config if no any targets
    reset(wingsPersistence);
    doReturn(mock(Query.class)).when(wingsPersistence).createQuery(any());
    when(wingsPersistence.delete(any(Query.class))).thenReturn(true);
    destroyProvisionState.setTargets(Collections.emptyList());
    destroyProvisionState.handleAsyncResponse(executionContext, responseMap);
    verify(wingsPersistence, never()).save(any(TerraformConfig.class));
    verify(wingsPersistence, times(2)).delete(any(Query.class));

    // Don't do anything if the status is not SUCCESS
    reset(wingsPersistence);
    responseData.setExecutionStatus(ExecutionStatus.FAILED);
    destroyProvisionState.handleAsyncResponse(executionContext, responseMap);
    verify(wingsPersistence, never()).save(any(TerraformConfig.class));
    verify(wingsPersistence, never()).delete(any(Query.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetTerraformInfrastructureProvisioner() {
    // TerraformInfrastructureProvisioner doesn't exists
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(null).when(infrastructureProvisionerService).get(anyString(), eq(PROVISIONER_ID));
    assertThatThrownBy(() -> state.getTerraformInfrastructureProvisioner(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Infrastructure Provisioner does not exist");

    // Invalid type of  InfrastructureProvisioner
    doReturn(mock(InfrastructureProvisioner.class))
        .when(infrastructureProvisionerService)
        .get(anyString(), eq(PROVISIONER_ID));
    assertThatThrownBy(() -> state.getTerraformInfrastructureProvisioner(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("should be of Terraform type.");

    // Valid InfrastructureProvisioner
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(anyString(), eq(PROVISIONER_ID));
    assertThat(state.getTerraformInfrastructureProvisioner(executionContext)).isEqualTo(provisioner);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateActivityOnExecute() {
    TerraformProvisionState spyState = spy(state);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build()).when(activityService).save(any(Activity.class));
    doReturn(null).when(spyState).executeInternal(executionContext, ACTIVITY_ID); // ignore execution

    // Missing STANDARD context element
    doReturn(null).when(executionContext).getContextElement(ContextElementType.STANDARD);
    assertThatThrownBy(() -> spyState.execute(executionContext)).hasMessageContaining("workflowStandardParams");

    // Missing current user in context
    WorkflowStandardParams standardParams = WorkflowStandardParams.Builder.aWorkflowStandardParams().build();
    doReturn(standardParams).when(executionContext).getContextElement(ContextElementType.STANDARD);
    assertThatThrownBy(() -> spyState.execute(executionContext)).hasMessageContaining("currentUser");

    standardParams.setCurrentUser(EmbeddedUser.builder().name(USER_NAME).email(USER_EMAIL).build());
    doReturn(WorkflowType.ORCHESTRATION).when(executionContext).getWorkflowType();
    doReturn(WORKFLOW_EXECUTION_ID).when(executionContext).getWorkflowExecutionId();
    doReturn(WORKFLOW_NAME).when(executionContext).getWorkflowExecutionName();
    ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);

    // Valid with BUILD orchestration type
    doReturn(OrchestrationWorkflowType.BUILD).when(executionContext).getOrchestrationWorkflowType();
    spyState.execute(executionContext);
    verify(activityService, times(1)).save(activityCaptor.capture());
    assertCreatedActivity(activityCaptor.getValue(), GLOBAL_ENV_ID, GLOBAL_ENV_ID, ALL);

    // Valid with not BUILD orchestration type
    Environment env = Environment.Builder.anEnvironment()
                          .environmentType(EnvironmentType.NON_PROD)
                          .uuid(ENV_ID)
                          .name(ENV_NAME)
                          .build();
    doReturn(OrchestrationWorkflowType.BASIC).when(executionContext).getOrchestrationWorkflowType();
    doReturn(env).when(executionContext).getEnv();
    spyState.execute(executionContext);
    // 1 time from previous invocation
    verify(activityService, times(2)).save(activityCaptor.capture());
    assertCreatedActivity(activityCaptor.getValue(), ENV_NAME, ENV_ID, EnvironmentType.NON_PROD);
  }

  private void assertCreatedActivity(Activity activity, String envName, String envId, EnvironmentType envType) {
    assertThat(activity.getWorkflowType()).isEqualTo(WorkflowType.ORCHESTRATION);
    assertThat(activity.getWorkflowExecutionId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(activity.getWorkflowExecutionName()).isEqualTo(WORKFLOW_NAME);
    assertThat(activity.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(activity.getTriggeredBy().getName()).isEqualTo(USER_NAME);
    assertThat(activity.getTriggeredBy().getEmail()).isEqualTo(USER_EMAIL);
    assertThat(activity.getEnvironmentName()).isEqualTo(envName);
    assertThat(activity.getEnvironmentId()).isEqualTo(envId);
    assertThat(activity.getEnvironmentType()).isEqualTo(envType);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetTfVarScriptRepositorySource() {
    ExecutionContext context = Mockito.mock(ExecutionContext.class);
    when(context.renderExpression(anyString())).thenAnswer(answer);
    TerraformProvisionState spyState = spy(state);
    List<String> tfVarFiles = Arrays.asList("path/to/file1.tfvar", "path/to/file2.tfvar");
    spyState.setTfVarFiles(tfVarFiles);
    TfVarScriptRepositorySource source = spyState.fetchTfVarScriptRepositorySource(context);
    assertThat(source).isNotNull();
    assertThat(source.getTfVarFilePaths())
        .containsExactlyInAnyOrder("path/to/file1.tfvar-rendered", "path/to/file2.tfvar-rendered");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetTfVarGitSource() {
    ExecutionContext context = Mockito.mock(ExecutionContext.class);
    when(context.renderExpression(anyString())).thenAnswer(answer);
    TerraformProvisionState spyState = spy(state);
    GitFileConfig gitFileConfig = GitFileConfig.builder().connectorId(SETTING_ID).repoName(REPO_NAME).build();
    spyState.setTfVarGitFileConfig(gitFileConfig);

    GitConfig gitConfig = GitConfig.builder().build();
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(gitFileConfig.getConnectorId());
    doNothing().when(gitConfigHelperService).renderGitConfig(context, gitConfig);
    doNothing().when(gitConfigHelperService).convertToRepoGitConfig(gitConfig, gitFileConfig.getRepoName());
    doReturn(new ArrayList<>()).when(secretManager).getEncryptionDetails(eq(gitConfig), eq(GLOBAL_APP_ID), any());

    List<String> expectedPaths = Arrays.asList("/path/to/file1.tfvar", "/path/to/file2.tfvar-rendered");

    validateGitSource(
        "/path/to/file1.tfvar,/path/to/file2.tfvar", expectedPaths, spyState, gitFileConfig, context, gitConfig);
    validateGitSource(
        "/path/to/file1.tfvar, /path/to/file2.tfvar", expectedPaths, spyState, gitFileConfig, context, gitConfig);
    validateGitSource(
        "/path/to/file1.tfvar , /path/to/file2.tfvar", expectedPaths, spyState, gitFileConfig, context, gitConfig);
    validateGitSource(
        " /path/to/file1.tfvar , /path/to/file2.tfvar", expectedPaths, spyState, gitFileConfig, context, gitConfig);
    validateGitSource(
        ", /path/to/file1.tfvar , /path/to/file2.tfvar", expectedPaths, spyState, gitFileConfig, context, gitConfig);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveProvisionerOutputsOnResponse() {
    when(featureFlagService.isEnabled(eq(FeatureName.SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT), anyString()))
        .thenReturn(true);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    TerraformExecutionData terraformExecutionData =
        TerraformExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .outputs(
                "{\"outputVar\": { \"value\" :\"outputVarValue\"}, \"complex\": { \"value\": { \"output\": \"value\"}}}")
            .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", terraformExecutionData);
    state.setProvisionerId(PROVISIONER_ID);

    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(SweepingOutputInstance.builder().build())
        .when(sweepingOutputService)
        .find(argThat(hasProperty("name", is(state.getMarkerName()))));
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(Scope.WORKFLOW);
    doReturn(managerExecutionLogCallback)
        .when(infrastructureProvisionerService)
        .getManagerExecutionCallback(APP_ID, "activityId", state.commandUnit().name());

    state.handleAsyncResponse(executionContext, response);

    ArgumentCaptor<SweepingOutputInstance> sweepingOutputCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(sweepingOutputCaptor.capture());
    verify(sweepingOutputService, never()).deleteById(anyString(), anyString());

    SweepingOutputInstance storedSweepingOutputInstance = sweepingOutputCaptor.getValue();
    assertThat(storedSweepingOutputInstance.getName()).isEqualTo(TerraformOutputVariables.SWEEPING_OUTPUT_NAME);
    assertThat(storedSweepingOutputInstance.getValue()).isInstanceOf(TerraformOutputVariables.class);
    TerraformOutputVariables storedOutputVariables = (TerraformOutputVariables) storedSweepingOutputInstance.getValue();
    assertThat(storedOutputVariables.get("outputVar")).isEqualTo("outputVarValue");
    assertThat(storedOutputVariables.get("complex")).isInstanceOf(Map.class);
    Map<String, String> complexVarValue = (Map<String, String>) storedOutputVariables.get("complex");
    assertThat(complexVarValue.get("output")).isEqualTo("value");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveProvisionerOutputsOnResponseWithExistingOutputs() {
    when(featureFlagService.isEnabled(eq(FeatureName.SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT), anyString()))
        .thenReturn(true);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    TerraformExecutionData terraformExecutionData = TerraformExecutionData.builder()
                                                        .executionStatus(ExecutionStatus.SUCCESS)
                                                        .outputs("{\"outputVar\": { \"value\" :\"outputVarValue\"}}")
                                                        .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", terraformExecutionData);
    TerraformOutputVariables existingOutputVariables = new TerraformOutputVariables();
    existingOutputVariables.put("existing", "value");
    SweepingOutputInstance existingVariablesOutputs = SweepingOutputInstance.builder()
                                                          .name(TerraformOutputVariables.SWEEPING_OUTPUT_NAME)
                                                          .value(existingOutputVariables)
                                                          .uuid(UUID)
                                                          .build();
    state.setProvisionerId(PROVISIONER_ID);

    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(SweepingOutputInstance.builder().build())
        .when(sweepingOutputService)
        .find(argThat(hasProperty("name", is(state.getMarkerName()))));
    doReturn(existingVariablesOutputs)
        .when(sweepingOutputService)
        .find(argThat(hasProperty("name", is(TerraformOutputVariables.SWEEPING_OUTPUT_NAME))));
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(Scope.WORKFLOW);
    doReturn(managerExecutionLogCallback)
        .when(infrastructureProvisionerService)
        .getManagerExecutionCallback(APP_ID, "activityId", state.commandUnit().name());

    state.handleAsyncResponse(executionContext, response);

    ArgumentCaptor<SweepingOutputInstance> sweepingOutputCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(sweepingOutputCaptor.capture());
    verify(sweepingOutputService, times(1)).deleteById(APP_ID, UUID);

    SweepingOutputInstance storedSweepingOutputInstance = sweepingOutputCaptor.getValue();
    assertThat(storedSweepingOutputInstance.getName()).isEqualTo(TerraformOutputVariables.SWEEPING_OUTPUT_NAME);
    assertThat(storedSweepingOutputInstance.getValue()).isInstanceOf(TerraformOutputVariables.class);
    TerraformOutputVariables storedOutputVariables = (TerraformOutputVariables) storedSweepingOutputInstance.getValue();
    assertThat(storedOutputVariables.get("outputVar")).isEqualTo("outputVarValue");
    assertThat(storedOutputVariables.get("existing")).isEqualTo("value");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void saveTerraformOutputsInContextMap() {
    when(featureFlagService.isEnabled(eq(FeatureName.SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT), anyString()))
        .thenReturn(false);
    Map<String, Object> outputVariables = new HashMap<>();
    outputVariables.put("outputVariableFromContext", "value");
    when(executionContext.getContextElement(ContextElementType.TERRAFORM_PROVISION))
        .thenReturn(TerraformOutputInfoElement.builder().outputVariables(outputVariables).build());
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();
    TerraformExecutionData terraformExecutionData =
        TerraformExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .outputs(
                "{\"outputVar\": { \"value\" :\"outputVarValue\"}, \"complex\": { \"value\": { \"output\": \"value\"}}}")
            .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", terraformExecutionData);
    state.setProvisionerId(PROVISIONER_ID);

    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(SweepingOutputInstance.builder().build())
        .when(sweepingOutputService)
        .find(argThat(hasProperty("name", is(state.getMarkerName()))));
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(Scope.WORKFLOW);
    doReturn(managerExecutionLogCallback)
        .when(infrastructureProvisionerService)
        .getManagerExecutionCallback(APP_ID, "activityId", state.commandUnit().name());

    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);
    TerraformOutputInfoElement terraformOutputInfoElement =
        (TerraformOutputInfoElement) executionResponse.getContextElements().get(0);
    assertThat(terraformOutputInfoElement.paramMap(executionContext)).containsKeys("terraform");
    assertThat(terraformOutputInfoElement.getOutputVariables().keySet())
        .containsOnly("outputVar", "complex", "outputVariableFromContext");
  }

  private void validateGitSource(String input, List<String> expectedList, TerraformProvisionState spyState,
      GitFileConfig gitFileConfig, ExecutionContext context, GitConfig gitConfig) {
    gitFileConfig.setFilePath(input);
    gitFileConfig.setFilePathList(null);

    TfVarGitSource source = spyState.fetchTfVarGitSource(context);
    assertThat(source).isNotNull();
    assertThat(source.getGitConfig()).isEqualTo(gitConfig);
    assertThat(source.getGitFileConfig().getFilePathList())
        .containsExactlyInAnyOrder(expectedList.get(0), expectedList.get(1));
    assertThat(source.getEncryptedDataDetails()).hasSize(0);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteInternalForSecretMangerConfig() {
    when(executionContext.getAppId()).thenReturn(APP_ID);
    doReturn(Environment.Builder.anEnvironment().build()).when(executionContext).getEnv();
    state.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .sourceRepoBranch("sourceRepoBranch")
                                                         .kmsId("kmsId")
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(ACCOUNT_ID).when(executionContext).getAccountId();
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);

    state.setRunPlanOnly(true);
    ExecutionResponse executionResponse = state.execute(executionContext);
    verify(secretManagerConfigService, times(0)).getSecretManager(anyString(), anyString(), anyBoolean());
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo("uuid");

    state.setRunPlanOnly(true);
    state.setExportPlanToApplyStep(true);
    ExecutionResponse executionResponse1 = state.execute(executionContext);
    verify(secretManagerConfigService, times(1)).getSecretManager(anyString(), anyString(), anyBoolean());
    assertThat(((ScriptStateExecutionData) executionResponse1.getStateExecutionData()).getActivityId())
        .isEqualTo("uuid");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteRegularWithNoProvisionerVariables() {
    state.setProvisionerId(PROVISIONER_ID);
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
            NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").value("hjjh").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType("TEXT").build());
    state.setVariables(nameValuePairList);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .variables(asList())
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = state.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getEnvironmentVariables()).isNull();
    assertThat(parameters.getVariables()).isNotEmpty();
    assertThat(parameters.getEncryptedVariables()).isNotEmpty();
    assertThat(parameters.getVariables().size()).isEqualTo(3);
    assertThat(parameters.getEncryptedVariables().size()).isEqualTo(1);
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteRegularWithSomeProvisionerVariables() {
    state.setProvisionerId(PROVISIONER_ID);
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
            NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").value("hjjh").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType("TEXT").build());
    state.setVariables(nameValuePairList);
    TerraformInfrastructureProvisioner provisioner =
        TerraformInfrastructureProvisioner.builder()
            .appId(APP_ID)
            .path("current/working/directory")
            .variables(asList(NameValuePair.builder().name("TF_LOG").valueType("TEXT").build(),
                NameValuePair.builder().name("access_token").valueType("ENCRYPTED_TEXT").build(),
                NameValuePair.builder().name("nil").valueType("TEXT").build(),
                NameValuePair.builder().name("PROVISIONER_VAR").valueType("TEXT").build()))
            .skipRefreshBeforeApplyingPlan(true)
            .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = state.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getEnvironmentVariables()).isNull();
    assertThat(parameters.getVariables()).isNotEmpty();
    assertThat(parameters.getEncryptedVariables()).isNotEmpty();
    assertThat(parameters.getVariables().size()).isEqualTo(3);
    assertThat(parameters.getEncryptedVariables().size()).isEqualTo(1);
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteRegularWithNoProvisionerBackendConfigs() {
    state.setProvisionerId(PROVISIONER_ID);
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
            NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").value("hjjh").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType("TEXT").build());
    state.setBackendConfigs(nameValuePairList);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .backendConfigs(null)
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = state.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getEnvironmentVariables()).isNull();
    assertThat(parameters.getVariables()).isEmpty();
    assertThat(parameters.getBackendConfigs().size()).isEqualTo(3);
    assertThat(parameters.getEncryptedBackendConfigs().size()).isEqualTo(1);
    assertThat(parameters.getEncryptedVariables()).isEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteRegularWithNoProvisionerEnvironmentVariables() {
    state.setProvisionerId(PROVISIONER_ID);
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
            NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").value("hjjh").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType("TEXT").build());
    state.setEnvironmentVariables(nameValuePairList);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .path("current/working/directory")
                                                         .environmentVariables(null)
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    GitConfig gitConfig = GitConfig.builder().branch("master").build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTask(any(DelegateTask.class));
    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    ExecutionResponse response = state.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getBackendConfigs()).isNull();
    assertThat(parameters.getVariables()).isEmpty();
    assertThat(parameters.getEnvironmentVariables().size()).isEqualTo(3);
    assertThat(parameters.getEncryptedEnvironmentVariables().size()).isEqualTo(1);
    assertThat(parameters.getEncryptedVariables()).isEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGenerateEntityId() {
    TerraformInfrastructureProvisioner infrastructureProvisioner;
    String entityId;
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(Environment.Builder.anEnvironment().uuid("envId").build()).when(executionContext).getEnv();
    when(executionContext.renderExpression("branch")).thenReturn("renderedBranch");
    when(executionContext.renderExpression("branch/branch")).thenReturn("renderedBranch/renderedBranch");

    when(executionContext.renderExpression("path/path")).thenReturn("renderedPath/renderedPath");

    infrastructureProvisioner = TerraformInfrastructureProvisioner.builder()
                                    .appId(APP_ID)
                                    .path("path/path")
                                    .sourceRepoBranch("branch/branch")
                                    .build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, true);
    assertThat(entityId).isEqualTo(format("%s-%s-%s-%s", PROVISIONER_ID, "envId",
        "renderedBranch/renderedBranch/renderedPath/renderedPath".hashCode(), "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("").sourceRepoBranch("branch").build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, true);
    assertThat(entityId).isEqualTo(
        format("%s-%s-%s-%s", PROVISIONER_ID, "envId", "renderedBranch".hashCode(), "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("path/path").sourceRepoBranch("").build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, true);
    assertThat(entityId).isEqualTo(
        format("%s-%s-%s-%s", PROVISIONER_ID, "envId", "/renderedPath/renderedPath".hashCode(), "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("").sourceRepoBranch("").build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, true);
    assertThat(entityId).isEqualTo(format("%s-%s-%s", PROVISIONER_ID, "envId", "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("").sourceRepoBranch("").build();
    entityId = state.generateEntityId(executionContext, "", infrastructureProvisioner, true);
    assertThat(entityId).isEqualTo(format("%s-%s", PROVISIONER_ID, "envId"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("path/path").sourceRepoBranch("branch").build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, false);
    assertThat(entityId).isEqualTo(format("%s-%s-%s", PROVISIONER_ID, "envId", "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("").sourceRepoBranch("branch").build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, false);
    assertThat(entityId).isEqualTo(format("%s-%s-%s", PROVISIONER_ID, "envId", "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("path/path").sourceRepoBranch("").build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, false);
    assertThat(entityId).isEqualTo(format("%s-%s-%s", PROVISIONER_ID, "envId", "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("").sourceRepoBranch("").build();
    entityId = state.generateEntityId(executionContext, "workspace", infrastructureProvisioner, false);
    assertThat(entityId).isEqualTo(format("%s-%s-%s", PROVISIONER_ID, "envId", "workspace"));

    infrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().appId(APP_ID).path("").sourceRepoBranch("").build();
    entityId = state.generateEntityId(executionContext, "", infrastructureProvisioner, false);
    assertThat(entityId).isEqualTo(format("%s-%s", PROVISIONER_ID, "envId"));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidation() {
    assertThat(state.validateFields().size()).isNotEqualTo(0);
    state.setProvisionerId("test provisioner");
    assertThat(state.validateFields().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void saveTerraformPlanJsonFileId() {
    testSaveTfPlanJsonUseOptimizedTfPlan(false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void saveTerraformPlanJsonFileIdReplaceExistingInstance() {
    testSaveTfPlanJsonUseOptimizedTfPlan(true);
  }

  private void testSaveTfPlanJsonUseOptimizedTfPlan(boolean replaceExistingInstance) {
    state.setRunPlanOnly(true);
    state.setExportPlanToApplyStep(true);
    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId",
        TerraformExecutionData.builder()
            .encryptedTfPlan(EncryptedRecordData.builder().build())
            .tfPlanJsonFiledId("fileId")
            .build());
    doReturn("workflowExecutionId").when(executionContext).getWorkflowExecutionId();
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(TerraformInfrastructureProvisioner.builder().build())
        .when(infrastructureProvisionerService)
        .get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));
    doReturn(true).when(featureFlagService).isEnabled(eq(FeatureName.OPTIMIZED_TF_PLAN), anyString());

    if (replaceExistingInstance) {
      doReturn(SweepingOutputInstance.builder()
                   .value(TerraformPlanParam.builder().tfPlanJsonFileId("existingFileId").build())
                   .build())
          .when(sweepingOutputService)
          .find(any(SweepingOutputInquiry.class));
    }

    state.handleAsyncResponse(executionContext, response);

    // for saving the tfplan json variable
    ArgumentCaptor<SweepingOutputInstance> instanceCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(instanceCaptor.capture());
    verify(executionContext, times(1)).prepareSweepingOutputBuilder(eq(Scope.PIPELINE));
    if (replaceExistingInstance) {
      verify(fileService, times(1)).deleteFile("existingFileId", FileBucket.TERRAFORM_PLAN_JSON);
    }

    SweepingOutputInstance savedInstance = instanceCaptor.getValue();
    assertThat(savedInstance.getValue()).isInstanceOf(TerraformPlanParam.class);
    TerraformPlanParam savedPlanParam = (TerraformPlanParam) savedInstance.getValue();
    assertThat(savedPlanParam.getTfplan()).isNull();
    assertThat(savedPlanParam.getTfPlanJsonFileId()).isEqualTo("fileId");
  }
}
