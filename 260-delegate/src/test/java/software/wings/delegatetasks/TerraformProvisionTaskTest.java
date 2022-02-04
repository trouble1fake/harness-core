/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.COMMIT_REFERENCE;
import static software.wings.utils.WingsTestConstants.ENTITY_ID;
import static software.wings.utils.WingsTestConstants.SOURCE_REPO_SETTINGS_ID;
import static software.wings.utils.WingsTestConstants.WORKSPACE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.terraform.TerraformBaseHelperImpl;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.filesystem.FileIo;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.terraform.TerraformClient;
import io.harness.terraform.beans.TerraformVersion;

import software.wings.WingsBaseTest;
import software.wings.api.TerraformExecutionData;
import software.wings.beans.AwsConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.KmsConfig;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.delegation.TerraformProvisionParameters.TerraformProvisionParametersBuilder;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;
import software.wings.utils.WingsTestConstants;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.zeroturnaround.exec.stream.LogOutputStream;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class TerraformProvisionTaskTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private GitClient gitClient;
  @Mock private DelegateLogService logService;
  @Mock private GitClientHelper gitClientHelper;
  @Mock private DelegateFileManager delegateFileManager;
  @Mock private EncryptDecryptHelper planEncryptDecryptHelper;
  @Mock private AwsHelperService awsHelperService;
  @Mock private TerraformClient terraformClient;
  @InjectMocks private TerraformBaseHelperImpl terraformBaseHelper;

  private static final String GIT_BRANCH = "test/git_branch";
  private static final String GIT_REPO_DIRECTORY = "repository/terraformTest";
  private static final String TASK_ID = "taskId";

  private GitConfig gitConfig;
  private Map<String, EncryptedDataDetail> encryptedBackendConfigs;
  private Map<String, EncryptedDataDetail> encryptedEnvironmentVariables;
  private EncryptedDataDetail encryptedDataDetail;
  private List<EncryptedDataDetail> sourceRepoEncryptionDetails;
  private final EncryptedRecordData encryptedPlanContent =
      EncryptedRecordData.builder().name("planName").encryptedValue("encryptedPlan".toCharArray()).build();
  private final byte[] planContent = "terraformPlanContent".getBytes();

  TerraformProvisionTask terraformProvisionTask =
      new TerraformProvisionTask(DelegateTaskPackage.builder()
                                     .delegateId(WingsTestConstants.DELEGATE_ID)
                                     .delegateTaskId(TASK_ID)
                                     .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                     .build(),
          null, delegateTaskResponse -> {}, () -> true);

  private TerraformProvisionTask terraformProvisionTaskSpy;
  private TerraformBaseHelperImpl spyTerraformBaseHelperImpl;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    spyTerraformBaseHelperImpl = spy(terraformBaseHelper);
    on(terraformProvisionTask).set("encryptionService", mockEncryptionService);
    on(terraformProvisionTask).set("gitClient", gitClient);
    on(terraformProvisionTask).set("logService", logService);
    on(terraformProvisionTask).set("gitClientHelper", gitClientHelper);
    on(terraformProvisionTask).set("delegateFileManager", delegateFileManager);
    on(terraformProvisionTask).set("planEncryptDecryptHelper", planEncryptDecryptHelper);
    on(terraformProvisionTask).set("awsHelperService", awsHelperService);
    on(terraformProvisionTask).set("terraformBaseHelper", spyTerraformBaseHelperImpl);
    on(terraformProvisionTask).set("terraformClient", terraformClient);

    gitConfig = GitConfig.builder().branch(GIT_BRANCH).build();
    gitConfig.setReference(COMMIT_REFERENCE);

    encryptedBackendConfigs = new HashMap<>();
    encryptedDataDetail = EncryptedDataDetail.builder()
                              .encryptedData(EncryptedRecordData.builder().uuid(WingsTestConstants.UUID).build())
                              .build();
    encryptedBackendConfigs.put("var2", encryptedDataDetail);

    encryptedEnvironmentVariables = new HashMap<>();
    encryptedEnvironmentVariables.put("key", encryptedDataDetail);

    sourceRepoEncryptionDetails = new ArrayList<>();
    sourceRepoEncryptionDetails.add(EncryptedDataDetail.builder().build());

    doReturn(GIT_REPO_DIRECTORY).when(gitClientHelper).getRepoDirectory(any(GitOperationContext.class));

    terraformProvisionTaskSpy = spy(terraformProvisionTask);

    doReturn(0)
        .when(terraformProvisionTaskSpy)
        .executeShellCommand(
            anyString(), anyString(), any(TerraformProvisionParameters.class), anyMap(), any(LogOutputStream.class));
    doReturn("latestCommit")
        .when(terraformProvisionTaskSpy)
        .getLatestCommitSHAFromLocalRepo(any(GitOperationContext.class));
    doReturn(new ArrayList<String>())
        .when(terraformProvisionTaskSpy)
        .getWorkspacesList(anyString(), any(), anyLong(), any(), any());
    doReturn(new char[] {'v', 'a', 'l', '2'}).when(mockEncryptionService).getDecryptedValue(encryptedDataDetail, false);
    doReturn(planContent).when(planEncryptDecryptHelper).getDecryptedContent(any(), any(), any());
    doReturn(true).when(planEncryptDecryptHelper).deleteEncryptedRecord(any(), any());

    when(delegateFileManager.upload(any(DelegateFile.class), any(InputStream.class))).thenReturn(new DelegateFile());
    doReturn(TerraformVersion.create(0, 12, 3)).when(terraformClient).version(anyLong(), anyString());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void getTargetsArgsTest() {
    assertThat(terraformProvisionTask.getTargetArgs(null)).isEqualTo("");
    assertThat(terraformProvisionTask.getTargetArgs(Collections.EMPTY_LIST)).isEqualTo("");

    List<String> targets = new ArrayList<>(Arrays.asList("target1", "target2"));

    assertThat(terraformProvisionTask.getTargetArgs(targets)).isEqualTo("-target=target1 -target=target2 ");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGenerateInlineCommandVars() throws Exception {
    doReturn(new char[] {'v', '2'}).when(mockEncryptionService).getDecryptedValue(any(), eq(false));
    TerraformProvisionParameters parameters =
        TerraformProvisionParameters.builder()
            .variables(ImmutableMap.of("k1", "v1"))
            .encryptedVariables(ImmutableMap.of("k2", EncryptedDataDetail.builder().build()))
            .build();
    StringBuilder inlineCommandBuffer = new StringBuilder();
    StringBuilder inlineUILogBuffer = new StringBuilder();
    terraformProvisionTask.getCommandLineVariableParams(parameters, null, inlineCommandBuffer, inlineUILogBuffer);
    String varParams = inlineCommandBuffer.toString();
    String uiLogs = inlineUILogBuffer.toString();
    assertThat(varParams).isEqualTo(" -var='k1=v1'  -var='k2=v2' ");
    assertThat(uiLogs).isEqualTo(" -var='k1=v1'  -var='k2=HarnessSecret:[k2]' ");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRunApply() throws IOException, TimeoutException, InterruptedException {
    setupForApply();

    // regular apply
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, null, TerraformCommandUnit.Apply, TerraformCommand.APPLY, false, false);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verify(terraformExecutionData, TerraformCommand.APPLY);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunApplyAwsCPAuthDetailsArnPresentFailedToAssumeRole()
      throws IOException, TimeoutException, InterruptedException {
    setupForApply();
    Map<String, String> backendConfigs = new HashMap<>();
    backendConfigs.put("var1", "value1");

    Map<String, String> variables = new HashMap<>();
    variables.put("var3", "val3");

    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("TF_LOG", "TRACE");

    List<String> tfVarFiles = Arrays.asList("tfVarFile");

    AwsConfig awsConfig = new AwsConfig();
    List<EncryptedDataDetail> awsConfigEncryptionDetails = new ArrayList<>();
    AWSSecurityTokenServiceClient awsSecurityTokenServiceClient = mock(AWSSecurityTokenServiceClient.class);

    doReturn(null).when(mockEncryptionService).decrypt(awsConfig, awsConfigEncryptionDetails, false);
    doReturn(awsSecurityTokenServiceClient).when(awsHelperService).getAmazonAWSSecurityTokenServiceClient(any(), any());
    doThrow(new RuntimeException("failed to assume role")).when(awsSecurityTokenServiceClient).assumeRole(any());

    // regular apply
    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        getTerraformProvisionParametersBuilder(false, false, null, TerraformCommandUnit.Apply, TerraformCommand.APPLY,
            false, false, backendConfigs, variables, environmentVariables, tfVarFiles, false)
            .awsConfigId("awsConfigId")
            .awsRoleArn("awsRoleArn")
            .awsConfig(awsConfig)
            .awsRegion("awsRegion")
            .awsConfigEncryptionDetails(awsConfigEncryptionDetails);
    TerraformProvisionParameters parameters = terraformProvisionParametersBuilder.build();
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(parameters);
    assertThat(terraformExecutionData.getErrorMessage()).isEqualTo("Invalid request: failed to assume role");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunApplyAwsCPAuthDetailsArnPresent() throws IOException, TimeoutException, InterruptedException {
    setupForApply();
    Map<String, String> backendConfigs = new HashMap<>();
    backendConfigs.put("var1", "value1");

    Map<String, String> variables = new HashMap<>();
    variables.put("var3", "val3");

    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("TF_LOG", "TRACE");

    List<String> tfVarFiles = Arrays.asList("tfVarFile");

    AwsConfig awsConfig = new AwsConfig();
    AssumeRoleResult assumeRoleResult = new AssumeRoleResult();
    List<EncryptedDataDetail> awsConfigEncryptionDetails = new ArrayList<>();
    AWSSecurityTokenServiceClient awsSecurityTokenServiceClient = mock(AWSSecurityTokenServiceClient.class);
    assumeRoleResult.setCredentials(new Credentials()
                                        .withAccessKeyId("accessKeyId")
                                        .withSecretAccessKey("secretAccessKey")
                                        .withSessionToken("sessionToken"));

    doReturn(null).when(mockEncryptionService).decrypt(awsConfig, awsConfigEncryptionDetails, false);
    doReturn(awsSecurityTokenServiceClient).when(awsHelperService).getAmazonAWSSecurityTokenServiceClient(any(), any());
    doReturn(assumeRoleResult).when(awsSecurityTokenServiceClient).assumeRole(any());

    // regular apply
    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        getTerraformProvisionParametersBuilder(false, false, null, TerraformCommandUnit.Apply, TerraformCommand.APPLY,
            false, false, backendConfigs, variables, environmentVariables, tfVarFiles, false)
            .awsConfigId("awsConfigId")
            .awsRoleArn("awsRoleArn")
            .awsConfig(awsConfig)
            .awsRegion("awsRegion")
            .awsConfigEncryptionDetails(awsConfigEncryptionDetails);
    TerraformProvisionParameters parameters = terraformProvisionParametersBuilder.build();
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(parameters);
    verify(terraformExecutionData, TerraformCommand.APPLY);
    ArgumentCaptor<Map> argument = ArgumentCaptor.forClass(Map.class);
    Mockito.verify(terraformProvisionTaskSpy, atLeastOnce())
        .executeShellCommand(any(), any(), any(), argument.capture(), any());
    Map<String, String> envVars = argument.getValue();
    assertThat(envVars.get("AWS_ACCESS_KEY_ID")).isEqualTo("accessKeyId");
    assertThat(envVars.get("AWS_SECRET_ACCESS_KEY")).isEqualTo("secretAccessKey");
    assertThat(envVars.get("AWS_SESSION_TOKEN")).isEqualTo("sessionToken");

    assertThat(terraformExecutionData.getAwsConfigId()).isEqualTo(parameters.getAwsConfigId());
    assertThat(terraformExecutionData.getAwsRoleArn()).isEqualTo(parameters.getAwsRoleArn());
    assertThat(terraformExecutionData.getAwsRegion()).isEqualTo(parameters.getAwsRegion());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunApplyAwsCPAuthDetailsNoArnPresent() throws IOException, TimeoutException, InterruptedException {
    setupForApply();
    Map<String, String> backendConfigs = new HashMap<>();
    backendConfigs.put("var1", "value1");

    Map<String, String> variables = new HashMap<>();
    variables.put("var3", "val3");

    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("TF_LOG", "TRACE");

    List<String> tfVarFiles = Arrays.asList("tfVarFile");

    AwsConfig awsConfig = new AwsConfig();
    List<EncryptedDataDetail> awsConfigEncryptionDetails = new ArrayList<>();
    AWSCredentialsProvider awsCredentialsProvider = mock(AWSCredentialsProvider.class);
    BasicAWSCredentials credentials = new BasicAWSCredentials("accessKeyId", "secretAccessKey");

    doReturn(null).when(mockEncryptionService).decrypt(awsConfig, awsConfigEncryptionDetails, false);
    doReturn(awsCredentialsProvider).when(awsHelperService).getAWSCredentialsProvider(any());
    doReturn(credentials).when(awsCredentialsProvider).getCredentials();

    // regular apply
    TerraformProvisionParametersBuilder terraformProvisionParametersBuilder =
        getTerraformProvisionParametersBuilder(false, false, null, TerraformCommandUnit.Apply, TerraformCommand.APPLY,
            false, false, backendConfigs, variables, environmentVariables, tfVarFiles, false)
            .awsConfigId("awsConfigId")
            .awsConfig(awsConfig)
            .awsRegion("awsRegion")
            .awsConfigEncryptionDetails(awsConfigEncryptionDetails);
    TerraformProvisionParameters parameters = terraformProvisionParametersBuilder.build();
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(parameters);
    verify(terraformExecutionData, TerraformCommand.APPLY);
    ArgumentCaptor<Map> argument = ArgumentCaptor.forClass(Map.class);
    Mockito.verify(terraformProvisionTaskSpy, atLeastOnce())
        .executeShellCommand(any(), any(), any(), argument.capture(), any());
    Map<String, String> envVars = argument.getValue();
    assertThat(envVars.get("AWS_ACCESS_KEY_ID")).isEqualTo("accessKeyId");
    assertThat(envVars.get("AWS_SECRET_ACCESS_KEY")).isEqualTo("secretAccessKey");
    assertThat(envVars.get("AWS_SESSION_TOKEN")).isEqualTo(null);

    assertThat(terraformExecutionData.getAwsConfigId()).isEqualTo(parameters.getAwsConfigId());
    assertThat(terraformExecutionData.getAwsRoleArn()).isEqualTo(parameters.getAwsRoleArn());
    assertThat(terraformExecutionData.getAwsRegion()).isEqualTo(parameters.getAwsRegion());
  }

  private void setupForApply() throws IOException {
    FileIo.createDirectoryIfDoesNotExist(GIT_REPO_DIRECTORY.concat("/scriptPath"));
    FileIo.writeFile(GIT_REPO_DIRECTORY.concat("/scriptPath/backend_configs-" + ENTITY_ID.hashCode()), new byte[] {});
    FileIo.writeFile(
        GIT_REPO_DIRECTORY.concat("/scriptPath/terraform-" + ENTITY_ID.hashCode() + ".tfvars"), new byte[] {});
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC1_testApplyUsingApprovedPlan() throws IOException, TimeoutException, InterruptedException {
    setupForApply();

    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, encryptedPlanContent, TerraformCommandUnit.Apply, TerraformCommand.APPLY, false, false);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verifyCommandExecuted(
        "terraform init", "terraform workspace", "terraform refresh", "terraform apply", "terraform output");
    verify(terraformExecutionData, TerraformCommand.APPLY);
    // verify that plan is getting deleted after getting applied
    Mockito.verify(planEncryptDecryptHelper, times(1)).deleteEncryptedRecord(any(), any());
  }

  /**
   * should skip refresh
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC2_testApplyUsingApprovedPlan() throws IOException, TimeoutException, InterruptedException {
    setupForApply();

    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, encryptedPlanContent, TerraformCommandUnit.Apply, TerraformCommand.APPLY, false, true);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verifyCommandExecuted("terraform init", "terraform workspace", "terraform apply", "terraform output");
    verify(terraformExecutionData, TerraformCommand.APPLY);
  }

  /**
   *  should not skip refresh since not using approved plan
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC1_testPlanAndExport() throws IOException, TimeoutException, InterruptedException {
    setupForApply();
    // run plan only and execute terraform show command
    byte[] terraformPlan = "terraformPlan".getBytes();
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        true, true, null, TerraformCommandUnit.Apply, TerraformCommand.APPLY, true, false);
    doReturn(terraformPlan)
        .when(terraformProvisionTaskSpy)
        .getTerraformPlanFile(anyString(), any(TerraformProvisionParameters.class));
    doReturn(encryptedPlanContent).when(planEncryptDecryptHelper).encryptContent(any(), any(), any());
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    Mockito.verify(planEncryptDecryptHelper, times(1)).encryptContent(any(), any(), any());
    verify(terraformExecutionData, TerraformCommand.APPLY);
    verifyCommandExecuted(
        "terraform init", "terraform workspace", "terraform refresh", "terraform plan", "terraform show");
    assertThat(terraformExecutionData.getEncryptedTfPlan()).isEqualTo(encryptedPlanContent);
  }

  /**
   *  should not skip refresh since not using approved plan
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC2_testApplyPlanAndExport() throws IOException, TimeoutException, InterruptedException {
    setupForApply();
    // run plan only and execute terraform show command
    byte[] terraformPlan = "terraformPlan".getBytes();
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        true, true, null, TerraformCommandUnit.Apply, TerraformCommand.APPLY, true, true);
    doReturn(terraformPlan)
        .when(terraformProvisionTaskSpy)
        .getTerraformPlanFile(anyString(), any(TerraformProvisionParameters.class));
    doReturn(encryptedPlanContent).when(planEncryptDecryptHelper).encryptContent(any(), any(), any());
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verify(terraformExecutionData, TerraformCommand.APPLY);
    verifyCommandExecuted(
        "terraform init", "terraform workspace", "terraform refresh", "terraform plan", "terraform show");
    assertThat(terraformExecutionData.getEncryptedTfPlan()).isEqualTo(encryptedPlanContent);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveAndGetTerraformPlanFile() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFile";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    byte[] planContent = "terraformPlanContent".getBytes();
    String workspacePath = "workspace";
    TerraformProvisionParameters terraformProvisionParameters = TerraformProvisionParameters.builder()
                                                                    .workspace(workspacePath)
                                                                    .encryptedTfPlan(encryptedPlanContent)
                                                                    .command(TerraformCommand.APPLY)
                                                                    .secretManagerConfig(KmsConfig.builder().build())
                                                                    .build();
    doReturn(planContent).when(planEncryptDecryptHelper).getDecryptedContent(any(), any(), any());

    terraformProvisionTask.saveTerraformPlanContentToFile(terraformProvisionParameters, scriptDirectory);
    List<FileData> fileDataList = FileIo.getFilesUnderPath(scriptDirectory);
    assertThat(fileDataList.size()).isEqualTo(1);
    assertThat(fileDataList.get(0).getFileBytes()).isEqualTo(planContent);

    byte[] retrievedTerraformPlanContent =
        terraformProvisionTask.getTerraformPlanFile(scriptDirectory, terraformProvisionParameters);
    Mockito.verify(planEncryptDecryptHelper).getDecryptedContent(any(), any(), any());
    assertThat(retrievedTerraformPlanContent).isEqualTo(planContent);

    FileIo.deleteDirectoryAndItsContentIfExists(scriptDirectory);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void TC1_testRunDestroy() throws IOException, TimeoutException, InterruptedException {
    setupForDestroyTests();

    // regular destroy with no plan exported
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, null, TerraformCommandUnit.Destroy, TerraformCommand.DESTROY, false, false);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verify(terraformExecutionData, TerraformCommand.DESTROY);
    verifyCommandExecuted("terraform init", "terraform workspace", "terraform refresh", "terraform destroy");

    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
    FileIo.deleteDirectoryAndItsContentIfExists("./terraform-working-dir");
  }

  /**
   * Should not skip refresh even because not using approved plan
   */
  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void TC2_testRunDestroy() throws IOException, TimeoutException, InterruptedException {
    setupForDestroyTests();

    // regular destroy with no plan exported
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, null, TerraformCommandUnit.Destroy, TerraformCommand.DESTROY, false, true);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verify(terraformExecutionData, TerraformCommand.DESTROY);
    verifyCommandExecuted("terraform init", "terraform workspace", "terraform refresh", "terraform destroy");

    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
    FileIo.deleteDirectoryAndItsContentIfExists("./terraform-working-dir");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunDestroyTf012() throws IOException, TimeoutException, InterruptedException {
    testRunDestroyAutoApprove(TerraformVersion.create(0, 12, 3), "terraform destroy -force");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunDestroyTf015() throws IOException, TimeoutException, InterruptedException {
    testRunDestroyAutoApprove(TerraformVersion.create(0, 15, 0), "terraform destroy -auto-approve");
  }

  private void testRunDestroyAutoApprove(TerraformVersion version, String expectedCommand)
      throws IOException, TimeoutException, InterruptedException {
    setupForDestroyTests();
    doReturn(version).when(terraformClient).version(anyLong(), anyString());

    // regular destroy with no plan exported
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, null, TerraformCommandUnit.Destroy, TerraformCommand.DESTROY, false, true);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verify(terraformExecutionData, TerraformCommand.DESTROY);
    verifyCommandExecuted("terraform init", "terraform workspace", "terraform refresh", "terraform destroy");

    Mockito.verify(terraformProvisionTaskSpy, atLeastOnce())
        .executeShellCommand(startsWith(expectedCommand), anyString(), eq(terraformProvisionParameters), anyMap(),
            any(LogOutputStream.class));

    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
    FileIo.deleteDirectoryAndItsContentIfExists("./terraform-working-dir");
  }

  private void setupForDestroyTests() throws IOException {
    setupForApply();

    doReturn(new ByteArrayInputStream(new byte[] {}))
        .when(delegateFileManager)
        .downloadByFileId(any(FileBucket.class), anyString(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC1_destroyUsingPlan() throws InterruptedException, TimeoutException, IOException {
    setupForDestroyTests();

    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, encryptedPlanContent, TerraformCommandUnit.Destroy, TerraformCommand.DESTROY, false, false);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verify(terraformExecutionData, TerraformCommand.DESTROY);
    verifyCommandExecuted("terraform init", "terraform workspace", "terraform refresh", "terraform apply");

    FileIo.deleteDirectoryAndItsContentIfExists(GIT_REPO_DIRECTORY);
    FileIo.deleteDirectoryAndItsContentIfExists("./terraform-working-dir");
  }

  /**
   * Skip Terraform Refresh when using approved plan
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC2_destroyUsingPlan() throws InterruptedException, TimeoutException, IOException {
    setupForDestroyTests();

    byte[] terraformDestroyPlan = "terraformDestroyPlan".getBytes();
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        false, false, encryptedPlanContent, TerraformCommandUnit.Destroy, TerraformCommand.DESTROY, false, true);
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    verify(terraformExecutionData, TerraformCommand.DESTROY);
    verifyCommandExecuted("terraform init", "terraform workspace", "terraform apply");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC1_destroyRunPlanOnly() throws InterruptedException, TimeoutException, IOException {
    setupForDestroyTests();

    byte[] terraformDestroyPlan = "terraformDestroyPlan".getBytes();
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        true, true, null, TerraformCommandUnit.Destroy, TerraformCommand.DESTROY, true, false);
    doReturn(terraformDestroyPlan)
        .when(terraformProvisionTaskSpy)
        .getTerraformPlanFile(anyString(), any(TerraformProvisionParameters.class));
    doReturn(encryptedPlanContent).when(planEncryptDecryptHelper).encryptContent(any(), any(), any());
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    Mockito.verify(planEncryptDecryptHelper, times(1)).encryptContent(any(), any(), any());
    verify(terraformExecutionData, TerraformCommand.DESTROY);
    assertThat(terraformExecutionData.getEncryptedTfPlan()).isEqualTo(encryptedPlanContent);
    verifyCommandExecuted(
        "terraform init", "terraform workspace", "terraform refresh", "terraform plan", "terraform show");
  }

  /**
   * Should not skip refresh even because not using approved plan
   */
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void TC2_destroyRunPlanOnly() throws InterruptedException, TimeoutException, IOException {
    setupForDestroyTests();

    byte[] terraformDestroyPlan = "terraformDestroyPlan".getBytes();
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        true, true, null, TerraformCommandUnit.Destroy, TerraformCommand.DESTROY, true, true);
    doReturn(terraformDestroyPlan)
        .when(terraformProvisionTaskSpy)
        .getTerraformPlanFile(anyString(), any(TerraformProvisionParameters.class));
    doReturn(encryptedPlanContent).when(planEncryptDecryptHelper).encryptContent(any(), any(), any());
    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);
    Mockito.verify(planEncryptDecryptHelper, times(1)).encryptContent(any(), any(), any());
    verify(terraformExecutionData, TerraformCommand.DESTROY);
    assertThat(terraformExecutionData.getEncryptedTfPlan()).isEqualTo(encryptedPlanContent);
    verifyCommandExecuted(
        "terraform init", "terraform workspace", "terraform refresh", "terraform plan", "terraform show");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSavePlanJsonFileToFileService() throws Exception {
    setupForApply();
    // run plan only and execute terraform show command
    final String delegatePlanJsonFileId = "fileId";
    final String delegatePlanFileId = "fileId";
    byte[] terraformPlan = "terraformPlan".getBytes();
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder().build();
    TerraformProvisionParameters terraformProvisionParameters = createTerraformProvisionParameters(
        true, true, null, TerraformCommandUnit.Apply, TerraformCommand.APPLY, true, false, true);

    doReturn(terraformPlan)
        .when(terraformProvisionTaskSpy)
        .getTerraformPlanFile(anyString(), any(TerraformProvisionParameters.class));
    doReturn(delegatePlanJsonFileId)
        .when(spyTerraformBaseHelperImpl)
        .uploadTfPlanJson(eq(ACCOUNT_ID), eq(WingsTestConstants.DELEGATE_ID), eq(TASK_ID), eq(ENTITY_ID),
            eq(TERRAFORM_PLAN_FILE_OUTPUT_NAME), anyString());
    doReturn(encryptedRecordData).when(planEncryptDecryptHelper).encryptFile(any(), any(), any(), any());

    TerraformExecutionData terraformExecutionData = terraformProvisionTaskSpy.run(terraformProvisionParameters);

    ArgumentCaptor<String> jsonPlanLocalFilePathCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(spyTerraformBaseHelperImpl, times(1))
        .uploadTfPlanJson(eq(ACCOUNT_ID), eq(WingsTestConstants.DELEGATE_ID), eq(TASK_ID), eq(ENTITY_ID),
            eq(TERRAFORM_PLAN_FILE_OUTPUT_NAME), jsonPlanLocalFilePathCaptor.capture());
    verify(terraformExecutionData, TerraformCommand.APPLY);
    verifyCommandExecuted(
        "terraform init", "terraform workspace", "terraform refresh", "terraform plan", "terraform show");
    assertThat(terraformExecutionData.getTfPlanJsonFiledId()).isEqualTo(delegatePlanJsonFileId);
    // check for file cleanup
    assertThat(new File(jsonPlanLocalFilePathCaptor.getValue())).doesNotExist();
  }

  private void verifyCommandExecuted(String... commands) throws IOException, InterruptedException, TimeoutException {
    ArgumentCaptor<String> listCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(terraformProvisionTaskSpy, atLeastOnce())
        .executeShellCommand(
            listCaptor.capture(), anyString(), any(TerraformProvisionParameters.class), any(Map.class), any());
    assertThat(listCaptor.getAllValues()
                   .stream()
                   .map(s -> "terraform " + s.split("terraform")[1])
                   .map(s -> s.split("\\s+"))
                   .map(s -> s[0] + " " + s[1])
                   .collect(Collectors.toList()))
        .containsExactly(commands);
  }

  private void verify(TerraformExecutionData terraformExecutionData, TerraformCommand command) {
    Mockito.verify(mockEncryptionService, times(1)).decrypt(gitConfig, sourceRepoEncryptionDetails, false);
    Mockito.verify(gitClient, times(1)).ensureRepoLocallyClonedAndUpdated(any(GitOperationContext.class));
    Mockito.verify(gitClientHelper, times(1)).getRepoDirectory(any(GitOperationContext.class));
    Mockito.verify(delegateFileManager, times(1)).upload(any(DelegateFile.class), any(InputStream.class));
    assertThat(terraformExecutionData.getWorkspace()).isEqualTo(WORKSPACE);
    assertThat(terraformExecutionData.getEntityId()).isEqualTo(ENTITY_ID);
    assertThat(terraformExecutionData.getCommandExecuted()).isEqualTo(command);
    assertThat(terraformExecutionData.getSourceRepoReference()).isEqualTo("latestCommit");
    assertThat(terraformExecutionData.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private TerraformProvisionParameters createTerraformProvisionParameters(boolean runPlanOnly,
      boolean exportPlanToApplyStep, EncryptedRecordData encryptedTfPlan, TerraformCommandUnit commandUnit,
      TerraformCommand command, boolean saveTerraformJson, boolean skipRefresh) {
    return createTerraformProvisionParameters(runPlanOnly, exportPlanToApplyStep, encryptedTfPlan, commandUnit, command,
        saveTerraformJson, skipRefresh, false);
  }

  private TerraformProvisionParameters createTerraformProvisionParameters(boolean runPlanOnly,
      boolean exportPlanToApplyStep, EncryptedRecordData encryptedTfPlan, TerraformCommandUnit commandUnit,
      TerraformCommand command, boolean saveTerraformJson, boolean skipRefresh, boolean useOptimizedTfPlan) {
    Map<String, String> backendConfigs = new HashMap<>();
    backendConfigs.put("var1", "value1");

    Map<String, String> variables = new HashMap<>();
    variables.put("var3", "val3");

    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("TF_LOG", "TRACE");

    List<String> tfVarFiles = Arrays.asList("tfVarFile");

    return getTerraformProvisionParametersBuilder(runPlanOnly, exportPlanToApplyStep, encryptedTfPlan, commandUnit,
        command, saveTerraformJson, skipRefresh, backendConfigs, variables, environmentVariables, tfVarFiles,
        useOptimizedTfPlan)
        .build();
  }

  private TerraformProvisionParametersBuilder getTerraformProvisionParametersBuilder(boolean runPlanOnly,
      boolean exportPlanToApplyStep, EncryptedRecordData encryptedTfPlan, TerraformCommandUnit commandUnit,
      TerraformCommand command, boolean saveTerraformJson, boolean skipRefresh, Map<String, String> backendConfigs,
      Map<String, String> variables, Map<String, String> environmentVariables, List<String> tfVarFiles,
      boolean useOptimizedTfPlan) {
    return TerraformProvisionParameters.builder()
        .sourceRepo(gitConfig)
        .sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID)
        .sourceRepoEncryptionDetails(sourceRepoEncryptionDetails)
        .scriptPath("scriptPath")
        .command(command)
        .commandUnit(commandUnit)
        .accountId(ACCOUNT_ID)
        .workspace(WORKSPACE)
        .entityId(ENTITY_ID)
        .backendConfigs(backendConfigs)
        .encryptedBackendConfigs(encryptedBackendConfigs)
        .encryptedTfPlan(encryptedTfPlan)
        .runPlanOnly(runPlanOnly)
        .exportPlanToApplyStep(exportPlanToApplyStep)
        .variables(variables)
        .environmentVariables(environmentVariables)
        .encryptedEnvironmentVariables(encryptedEnvironmentVariables)
        .tfVarFiles(tfVarFiles)
        .saveTerraformJson(saveTerraformJson)
        .useOptimizedTfPlanJson(useOptimizedTfPlan)
        .skipRefreshBeforeApplyingPlan(skipRefresh)
        .secretManagerConfig(KmsConfig.builder().name("config").uuid("uuid").build());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveAndGetTerraformPlanFileWorkspaceEmpty() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFileWorkspaceEmpty";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    byte[] planContent = "terraformPlanContent".getBytes();
    TerraformProvisionParameters terraformProvisionParameters =
        TerraformProvisionParameters.builder()
            .command(TerraformCommand.APPLY)
            .secretManagerConfig(KmsConfig.builder().build())
            .encryptedTfPlan(EncryptedRecordData.builder().build())
            .build();
    doReturn(planContent).when(planEncryptDecryptHelper).getDecryptedContent(any(), any(), any());

    terraformProvisionTask.saveTerraformPlanContentToFile(terraformProvisionParameters, scriptDirectory);
    List<FileData> fileDataList = FileIo.getFilesUnderPath(scriptDirectory);
    assertThat(fileDataList.size()).isEqualTo(1);
    assertThat(fileDataList.get(0).getFileBytes()).isEqualTo(planContent);

    TerraformProvisionParameters provisionParameters =
        TerraformProvisionParameters.builder().command(TerraformCommand.APPLY).build();
    byte[] retrievedTerraformPlanContent =
        terraformProvisionTask.getTerraformPlanFile(scriptDirectory, provisionParameters);
    assertThat(retrievedTerraformPlanContent).isEqualTo(planContent);

    FileIo.deleteDirectoryAndItsContentIfExists(scriptDirectory);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveAndGetTerraformPlanFileFromFileStorageWorkspaceEmpty() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFileWorkspaceEmpty";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    byte[] planContent = "terraformPlanContent".getBytes();
    TerraformProvisionParameters terraformProvisionParameters =
        TerraformProvisionParameters.builder()
            .command(TerraformCommand.APPLY)
            .secretManagerConfig(KmsConfig.builder().build())
            .encryptedTfPlan(EncryptedRecordData.builder().encryptedValue("fileId".toCharArray()).build())
            .build();
    doReturn(planContent).when(planEncryptDecryptHelper).getDecryptedContent(any(), any(), any());

    terraformProvisionTask.saveTerraformPlanContentToFile(terraformProvisionParameters, scriptDirectory);
    List<FileData> fileDataList = FileIo.getFilesUnderPath(scriptDirectory);
    assertThat(fileDataList.size()).isEqualTo(1);
    assertThat(fileDataList.get(0).getFileBytes()).isEqualTo(planContent);

    TerraformProvisionParameters provisionParameters =
        TerraformProvisionParameters.builder().command(TerraformCommand.APPLY).build();
    byte[] retrievedTerraformPlanContent =
        terraformProvisionTask.getTerraformPlanFile(scriptDirectory, provisionParameters);
    assertThat(retrievedTerraformPlanContent).isEqualTo(planContent);
    Mockito.verify(planEncryptDecryptHelper).getDecryptedContent(any(), any(), any());
    assertThat(terraformProvisionParameters.getEncryptedTfPlan().getEncryptedValue()).isEqualTo("fileId".toCharArray());
    FileIo.deleteDirectoryAndItsContentIfExists(scriptDirectory);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveAndGetTerraformPlanFileWorkspaceSet() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFileWorkspaceSet";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    byte[] planContent = "terraformPlanContent".getBytes();
    TerraformProvisionParameters terraformProvisionParameters =
        TerraformProvisionParameters.builder()
            .command(TerraformCommand.APPLY)
            .workspace("workspace")
            .encryptedTfPlan(EncryptedRecordData.builder().build())
            .secretManagerConfig(KmsConfig.builder().build())
            .build();
    doReturn(planContent).when(planEncryptDecryptHelper).getDecryptedContent(any(), any(), any());

    terraformProvisionTask.saveTerraformPlanContentToFile(terraformProvisionParameters, scriptDirectory);
    List<FileData> fileDataList = FileIo.getFilesUnderPath(scriptDirectory);
    assertThat(fileDataList.size()).isEqualTo(1);
    assertThat(fileDataList.get(0).getFileBytes()).isEqualTo(planContent);

    TerraformProvisionParameters provisionParameters =
        TerraformProvisionParameters.builder().command(TerraformCommand.APPLY).build();
    byte[] retrievedTerraformPlanContent =
        terraformProvisionTask.getTerraformPlanFile(scriptDirectory, provisionParameters);
    assertThat(retrievedTerraformPlanContent).isEqualTo(planContent);

    FileIo.deleteDirectoryAndItsContentIfExists(scriptDirectory);
  }
}
