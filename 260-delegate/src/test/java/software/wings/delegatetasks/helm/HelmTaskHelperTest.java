/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.helm.CustomManifestFetchTaskHelper.zipManifestDirectory;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.RESOURCE_DIR_BASE;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.delegatetasks.helm.HelmTestConstants.ACCOUNT_ID;
import static software.wings.delegatetasks.helm.HelmTestConstants.APP_ID;
import static software.wings.delegatetasks.helm.HelmTestConstants.LONG_TIMEOUT_INTERVAL;
import static software.wings.delegatetasks.helm.HelmTestConstants.MANIFEST_ID;
import static software.wings.delegatetasks.helm.HelmTestConstants.SERVICE_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.chartmuseum.ChartMuseumServer;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.exception.HelmClientException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.helm.HelmCliCommandType;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.chartmuseum.ChartMuseumClient;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.settings.SettingValue;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmTaskHelperTest extends WingsBaseTest {
  public static final String V_3_HELM_SEARCH_REPO_COMMAND =
      "v3/helm search repo repoName/chartName -l --devel --max-col-width 300";
  @Mock private ProcessExecutor processExecutor;
  @Mock K8sGlobalConfigService k8sGlobalConfigService;
  @Mock EncryptionService encryptionService;
  @Mock ChartMuseumClient chartMuseumClient;
  @Mock DelegateFileManagerBase delegateFileManagerBase;
  @Spy @InjectMocks private HelmTaskHelper helmTaskHelper;
  @Spy @InjectMocks private HelmTaskHelperBase helmTaskHelperBase;

  @Before
  public void setup() {
    doReturn(processExecutor)
        .when(helmTaskHelperBase)
        .createProcessExecutor(anyString(), anyString(), anyLong(), anyMap());
    doReturn("v3/helm").when(k8sGlobalConfigService).getHelmPath(V3);
    doReturn("v2/helm").when(k8sGlobalConfigService).getHelmPath(V2);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAddRepoForwardHelmTaskHelperBase() {
    doNothing()
        .when(helmTaskHelperBase)
        .addRepo("vault", "vault", "https://helm-server", "admin", "secret-text".toCharArray(), "/home", V3,
            LONG_TIMEOUT_INTERVAL, false);
    helmTaskHelper.addRepo("vault", "vault", "https://helm-server", "admin", "secret-text".toCharArray(), "/home", V3,
        LONG_TIMEOUT_INTERVAL);

    verify(helmTaskHelperBase)
        .addRepo("vault", "vault", "https://helm-server", "admin", "secret-text".toCharArray(), "/home", V3,
            LONG_TIMEOUT_INTERVAL, false);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAddRepo() throws Exception {
    testAddRepoSuccess();
    testAddRepoIfProcessExecException();
    testAddRepoIfHelmCommandFailed();
  }

  private void testAddRepoIfHelmCommandFailed() {
    doReturn(new ProcessResult(1, new ProcessOutput(new byte[1])))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), any(HelmCliCommandType.class));
    assertThatExceptionOfType(HelmClientException.class)
        .isThrownBy(()
                        -> helmTaskHelper.addRepo("vault", "vault", "https://helm-server", "admin",
                            "secret-text".toCharArray(), "/home", V3, LONG_TIMEOUT_INTERVAL, StringUtils.EMPTY))
        .withMessageContaining(
            "Failed to add helm repo. Executed command v3/helm repo add vault https://helm-server --username admin --password *******");
  }

  private void testAddRepoIfProcessExecException() {
    doThrow(new HelmClientException("ex", HelmCliCommandType.REPO_ADD))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), any(HelmCliCommandType.class));

    assertThatExceptionOfType(HelmClientException.class)
        .isThrownBy(()
                        -> helmTaskHelper.addRepo("vault", "vault", "https://helm-server", "admin",
                            "secret-text".toCharArray(), "/home", V3, LONG_TIMEOUT_INTERVAL, StringUtils.EMPTY));
  }

  private void testAddRepoSuccess() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

    doReturn(new ProcessResult(0, new ProcessOutput(new byte[1])))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), any(HelmCliCommandType.class));
    helmTaskHelper.addRepo("vault", "vault", "https://helm-server", "admin", "secret-text".toCharArray(), "/home", V3,
        LONG_TIMEOUT_INTERVAL);

    verify(helmTaskHelperBase, times(1))
        .executeCommand(anyMap(), captor.capture(), captor.capture(), captor.capture(), eq(LONG_TIMEOUT_INTERVAL),
            eq(HelmCliCommandType.REPO_ADD));

    assertThat(captor.getAllValues().get(0))
        .isEqualTo("v3/helm repo add vault https://helm-server --username admin --password secret-text");
    assertThat(captor.getAllValues().get(1)).isEqualTo("/home");
    assertThat(captor.getAllValues().get(2))
        .isEqualTo(
            "add helm repo. Executed commandv3/helm repo add vault https://helm-server --username admin --password *******");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoFromChartsYamlFile() throws Exception {
    String chartYaml = "apiVersion: v1\n"
        + "appVersion: \"1.0\"\n"
        + "description: A Helm chart for Kubernetes\n"
        + "name: my-chart\n"
        + "version: 0.1.0";
    File file = File.createTempFile("Chart", ".yaml");
    try (OutputStreamWriter outputStreamWriter = new FileWriter(file)) {
      IOUtils.write(chartYaml, outputStreamWriter);
    }

    HelmChartInfo helmChartInfo = helmTaskHelper.getHelmChartInfoFromChartsYamlFile(file.getPath());
    assertThat(helmChartInfo).isNotNull();
    assertThat(helmChartInfo.getVersion()).isEqualTo("0.1.0");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoFromChartsYamlFileFromInstallCommandRequest() throws Exception {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().workingDir("working/dir").build();
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().build();

    doReturn(helmChartInfo).when(helmTaskHelper).getHelmChartInfoFromChartsYamlFile("working/dir/Chart.yaml");

    assertThat(helmTaskHelper.getHelmChartInfoFromChartsYamlFile(request)).isSameAs(helmChartInfo);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoFromChartDirectory() throws Exception {
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().build();

    doReturn(helmChartInfo).when(helmTaskHelper).getHelmChartInfoFromChartsYamlFile("chart/directory/Chart.yaml");

    assertThat(helmTaskHelper.getHelmChartInfoFromChartDirectory("chart/directory")).isSameAs(helmChartInfo);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesForGCSHelmRepo() throws Exception {
    ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(1234).build();
    GCSHelmRepoConfig gcsHelmRepoConfig =
        GCSHelmRepoConfig.builder().accountId("accountId").bucketName("bucketName").build();

    HelmChartConfigParams gcsConfigParams = getHelmChartConfigParams(gcsHelmRepoConfig);

    Path outputTemporaryDir = Files.createTempDirectory("chartFile");
    ProcessResult successfulResult = new ProcessResult(0, null);

    doReturn(chartMuseumServer)
        .when(chartMuseumClient)
        .startChartMuseumServer(eq(gcsHelmRepoConfig), any(SettingValue.class), anyString(), anyString(), eq(false));
    doReturn(successfulResult).when(processExecutor).execute();
    helmTaskHelper.downloadChartFiles(gcsConfigParams, outputTemporaryDir.toString(), LONG_TIMEOUT_INTERVAL, null);
    verifyFetchChartFilesProcessExecutor(outputTemporaryDir.toString());
    deleteDirectoryAndItsContentIfExists(outputTemporaryDir.toString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesForAwsS3HelmRepo() throws Exception {
    ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(1234).build();
    AmazonS3HelmRepoConfig s3HelmRepoConfig =
        AmazonS3HelmRepoConfig.builder().accountId("accountId").bucketName("bucketName").build();

    HelmChartConfigParams awsConfigParams = getHelmChartConfigParams(s3HelmRepoConfig);

    Path outputTemporaryDir = Files.createTempDirectory("chartFile");
    ProcessResult successfulResult = new ProcessResult(0, null);

    doReturn(chartMuseumServer)
        .when(chartMuseumClient)
        .startChartMuseumServer(eq(s3HelmRepoConfig), any(SettingValue.class), anyString(), anyString(), eq(false));
    doReturn(successfulResult).when(processExecutor).execute();
    helmTaskHelper.downloadChartFiles(awsConfigParams, outputTemporaryDir.toString(), LONG_TIMEOUT_INTERVAL, null);
    verifyFetchChartFilesProcessExecutor(outputTemporaryDir.toString());
    deleteDirectoryAndItsContentIfExists(outputTemporaryDir.toString());
  }

  private void verifyFetchChartFilesProcessExecutor(String outputDirectory) throws Exception {
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm repo add repoName http://127.0.0.1:1234", outputDirectory,
            LONG_TIMEOUT_INTERVAL, Collections.emptyMap());
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm pull repoName/chartName  --untar ", outputDirectory, LONG_TIMEOUT_INTERVAL,
            Collections.emptyMap());
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm repo remove repoName", null, LONG_TIMEOUT_INTERVAL, Collections.emptyMap());
    verify(processExecutor, times(3)).execute();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesForHttpHelmRepo() throws Exception {
    ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(1234).build();
    HttpHelmRepoConfig httpHelmRepoConfig =
        HttpHelmRepoConfig.builder().accountId("accountId").chartRepoUrl("http://127.0.0.1:1234").build();

    HelmChartConfigParams httpHelmChartConfig = getHelmChartConfigParams(httpHelmRepoConfig);

    Path outputTemporaryDir = Files.createTempDirectory("chartFile");
    ProcessResult successfulResult = new ProcessResult(0, null);

    doReturn(chartMuseumServer)
        .when(chartMuseumClient)
        .startChartMuseumServer(eq(httpHelmRepoConfig), any(SettingValue.class), anyString(), anyString(), eq(false));
    doReturn(successfulResult).when(processExecutor).execute();
    helmTaskHelper.downloadChartFiles(httpHelmChartConfig, outputTemporaryDir.toString(), LONG_TIMEOUT_INTERVAL, null);
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm pull repoName/chartName  --untar ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, Collections.emptyMap());
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm repo add repoName http://127.0.0.1:1234  ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, Collections.emptyMap());
    verify(processExecutor, times(2)).execute();
    deleteDirectoryAndItsContentIfExists(outputTemporaryDir.toString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesForEmptyHelmRepo() throws Exception {
    HelmChartConfigParams emptyHelmRepoConfig = getHelmChartConfigParams(null);
    Path outputTemporaryDir = Files.createTempDirectory("chartFile");

    ProcessResult successfulResult = new ProcessResult(0, null);
    doReturn(successfulResult).when(processExecutor).execute();

    // With empty chart Url
    helmTaskHelper.downloadChartFiles(emptyHelmRepoConfig, outputTemporaryDir.toString(), LONG_TIMEOUT_INTERVAL, null);
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm pull repoName/chartName  --untar ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, emptyMap());

    // With not empty chart Url
    emptyHelmRepoConfig.setChartUrl("http://127.0.0.1:1234/chart");
    helmTaskHelper.downloadChartFiles(emptyHelmRepoConfig, outputTemporaryDir.toString(), LONG_TIMEOUT_INTERVAL, null);
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm repo add repoName http://127.0.0.1:1234/chart  ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, emptyMap());
    verify(helmTaskHelperBase, times(2))
        .createProcessExecutor("v3/helm pull repoName/chartName  --untar ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, emptyMap());
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm repo remove repoName", null, LONG_TIMEOUT_INTERVAL, emptyMap());
    deleteDirectoryAndItsContentIfExists(outputTemporaryDir.toString());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDownloadChartFilesForEmptyHelmRepoBySpec() throws Exception {
    HelmChartSpecification helmChartSpecification = getHelmChartSpecification(null);
    helmChartSpecification.setChartName("stable/chartName1");
    Path outputTemporaryDir = Files.createTempDirectory("chartFile");

    ProcessResult successfulResult = new ProcessResult(0, null);
    doReturn(successfulResult).when(processExecutor).execute();

    HelmCommandRequest helmCommandRequest =
        HelmInstallCommandRequest.builder().helmVersion(V3).repoName("repoName").build();

    // With empty chart Url
    helmTaskHelper.downloadChartFiles(
        helmChartSpecification, outputTemporaryDir.toString(), helmCommandRequest, LONG_TIMEOUT_INTERVAL, null);
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm pull stable/chartName1  --untar ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, emptyMap());

    helmChartSpecification.setChartName("chartName2");
    helmTaskHelper.downloadChartFiles(
        helmChartSpecification, outputTemporaryDir.toString(), helmCommandRequest, LONG_TIMEOUT_INTERVAL, null);
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor(
            "v3/helm pull chartName2  --untar ", outputTemporaryDir.toString(), LONG_TIMEOUT_INTERVAL, emptyMap());

    // With not empty chart Url
    helmChartSpecification.setChartUrl("http://127.0.0.1:1234/chart");
    helmChartSpecification.setChartName("stable/chartName3");
    helmTaskHelper.downloadChartFiles(
        helmChartSpecification, outputTemporaryDir.toString(), helmCommandRequest, LONG_TIMEOUT_INTERVAL, null);
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm repo add repoName http://127.0.0.1:1234/chart  ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, emptyMap());
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm pull repoName/stable/chartName3  --untar ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, emptyMap());
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm repo remove repoName", null, LONG_TIMEOUT_INTERVAL, emptyMap());

    helmChartSpecification.setChartName("chartName4");
    helmTaskHelper.downloadChartFiles(
        helmChartSpecification, outputTemporaryDir.toString(), helmCommandRequest, LONG_TIMEOUT_INTERVAL, null);
    verify(helmTaskHelperBase, times(1))
        .createProcessExecutor("v3/helm pull repoName/chartName4  --untar ", outputTemporaryDir.toString(),
            LONG_TIMEOUT_INTERVAL, emptyMap());

    deleteDirectoryAndItsContentIfExists(outputTemporaryDir.toString());
  }

  @Test(expected = HelmClientException.class)
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFileFailed() throws Exception {
    HelmChartConfigParams emptyHelmRepoConfig = getHelmChartConfigParams(null);
    Path outputTemporaryDir = Files.createTempDirectory("chartFile");

    ProcessResult failedResult = new ProcessResult(1, null);
    doReturn(failedResult).when(processExecutor).execute();

    helmTaskHelper.downloadChartFiles(emptyHelmRepoConfig, outputTemporaryDir.toString(), LONG_TIMEOUT_INTERVAL, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrintHelmChartInfoInExecutionLogs() {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    HelmChartConfigParams withAmazonS3ConfigRepo = getHelmChartConfigParams(
        AmazonS3HelmRepoConfig.builder().accountId("accountId").bucketName("bucketName").build());
    HelmChartConfigParams withHttpConfigRepo = getHelmChartConfigParams(
        HttpHelmRepoConfig.builder().accountId("accountId").chartRepoUrl("http://127.0.0.1:1234").build());
    withHttpConfigRepo.setChartName(null);
    HelmChartConfigParams emptyHelmChartConfigParams = getHelmChartConfigParams(null);
    emptyHelmChartConfigParams.setHelmVersion(null);

    helmTaskHelper.printHelmChartInfoInExecutionLogs(withAmazonS3ConfigRepo, executionLogCallback);
    verify(executionLogCallback).saveExecutionLog(format("Chart bucket: %s", "bucketName"));
    helmTaskHelper.printHelmChartInfoInExecutionLogs(withHttpConfigRepo, executionLogCallback);
    verify(executionLogCallback).saveExecutionLog(format("Repo url: %s", "http://127.0.0.1:1234"));

    // with chart name
    emptyHelmChartConfigParams.setChartName("chartName");
    helmTaskHelper.printHelmChartInfoInExecutionLogs(emptyHelmChartConfigParams, executionLogCallback);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(format("Chart name: %s", "chartName"));

    // with repo display name
    emptyHelmChartConfigParams.setRepoDisplayName("repoDisplayName");
    helmTaskHelper.printHelmChartInfoInExecutionLogs(emptyHelmChartConfigParams, executionLogCallback);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(format("Helm repository: %s", "repoDisplayName"));

    // with base path
    emptyHelmChartConfigParams.setBasePath("basePath");
    helmTaskHelper.printHelmChartInfoInExecutionLogs(emptyHelmChartConfigParams, executionLogCallback);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(format("Base Path: %s", "basePath"));

    // with chart version
    emptyHelmChartConfigParams.setChartVersion("1.0.0");
    helmTaskHelper.printHelmChartInfoInExecutionLogs(emptyHelmChartConfigParams, executionLogCallback);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(format("Chart version: %s", "1.0.0"));

    // with chart url
    emptyHelmChartConfigParams.setChartUrl("http://127.0.0.1");
    helmTaskHelper.printHelmChartInfoInExecutionLogs(emptyHelmChartConfigParams, executionLogCallback);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(format("Chart url: %s", "http://127.0.0.1"));

    // with helm version
    emptyHelmChartConfigParams.setHelmVersion(V2);
    helmTaskHelper.printHelmChartInfoInExecutionLogs(emptyHelmChartConfigParams, executionLogCallback);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(format("Helm version: %s", V2));
  }

  private HelmChartConfigParams getHelmChartConfigParams(HelmRepoConfig repoConfig) {
    SettingValue connectorConfig = null;
    if (repoConfig instanceof GCSHelmRepoConfig) {
      connectorConfig = GcpConfig.builder().accountId("accountId").build();
    }

    return HelmChartConfigParams.builder()
        .chartName("chartName")
        .helmRepoConfig(repoConfig)
        .connectorConfig(connectorConfig)
        .connectorEncryptedDataDetails(
            ImmutableList.of(EncryptedDataDetail.builder().fieldName("aws-accessKey").build()))
        .encryptedDataDetails(ImmutableList.of(EncryptedDataDetail.builder().fieldName("aws-secretKey").build()))
        .helmVersion(V3)
        .repoName("repoName")
        .build();
  }

  private HelmChartSpecification getHelmChartSpecification(String url) {
    return HelmChartSpecification.builder().chartName("chartName").chartVersion("").chartUrl(url).build();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateProcessExecutor() {
    long timeoutMillis = 9000;
    String command = "v3/helm repo remove repoName";
    String workingDir = "/pwd/wd";
    String emptyWorkingDir = "";
    doCallRealMethod().when(helmTaskHelperBase).createProcessExecutor(anyString(), anyString(), anyLong(), anyMap());

    ProcessExecutor executor = helmTaskHelperBase.createProcessExecutor(command, workingDir, timeoutMillis, emptyMap());
    assertThat(executor.getDirectory()).isEqualTo(new File(workingDir));
    assertThat(String.join(" ", executor.getCommand())).isEqualTo(command);

    executor = helmTaskHelperBase.createProcessExecutor(command, emptyWorkingDir, timeoutMillis, emptyMap());
    assertThat(executor.getDirectory()).isNull();
    assertThat(String.join(" ", executor.getCommand())).isEqualTo(command);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testAddRepoWithEmptyPasswordBase() {
    char[] emptyPassword = new char[0];
    char[] passwordWithWhitespaces = new char[] {' ', ' '};
    doReturn(new ProcessResult(0, new ProcessOutput(new byte[1])))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));

    helmTaskHelperBase.addRepo(
        "repo", "repo", "http://null-password-url", "username", null, "chart", V3, LONG_TIMEOUT_INTERVAL, false);
    verify(helmTaskHelperBase)
        .executeCommand(anyMap(), eq("v3/helm repo add repo http://null-password-url --username username "),
            anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));

    helmTaskHelperBase.addRepo(
        "repo", "repo", "http://repo-url", "username", emptyPassword, "chart", V3, LONG_TIMEOUT_INTERVAL, false);
    verify(helmTaskHelperBase)
        .executeCommand(anyMap(), eq("v3/helm repo add repo http://repo-url --username username "), anyString(),
            anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));

    helmTaskHelperBase.addRepo(
        "repo", "repo", "http://repo-url", " ", passwordWithWhitespaces, "chart", V3, LONG_TIMEOUT_INTERVAL, false);
    verify(helmTaskHelperBase)
        .executeCommand(anyMap(), eq("v3/helm repo add repo http://repo-url  "), anyString(), anyString(), anyLong(),
            eq(HelmCliCommandType.REPO_ADD));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAddRepoWithEmptyPassword() {
    char[] emptyPassword = new char[0];
    char[] passwordWithWhitespaces = new char[] {' ', ' '};
    doReturn(new ProcessResult(0, new ProcessOutput(new byte[1])))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));

    helmTaskHelper.addRepo(
        "repo", "repo", "http://null-password-url", "username", null, "chart", V3, LONG_TIMEOUT_INTERVAL);
    verify(helmTaskHelperBase)
        .addRepo(eq("repo"), eq("repo"), eq("http://null-password-url"), eq("username"), any(), eq("chart"), any(),
            anyLong(), eq(false));

    helmTaskHelper.addRepo(
        "repo", "repo", "http://repo-url", "username", emptyPassword, "chart", V3, LONG_TIMEOUT_INTERVAL);
    verify(helmTaskHelperBase)
        .addRepo(eq("repo"), eq("repo"), eq("http://repo-url"), eq("username"), eq(emptyPassword), eq("chart"), any(),
            anyLong(), eq(false));

    helmTaskHelper.addRepo(
        "repo", "repo", "http://repo-url", " ", passwordWithWhitespaces, "chart", V3, LONG_TIMEOUT_INTERVAL);
    verify(helmTaskHelperBase)
        .addRepo(eq("repo"), eq("repo"), eq("http://repo-url"), eq(" "), eq(passwordWithWhitespaces), eq("chart"),
            any(), anyLong(), eq(false));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesYamlFromChart() throws Exception {
    String valuesFileContent = "var: value";
    String chartName = "chartName";

    K8sValuesLocation k8sValuesLocation = K8sValuesLocation.ServiceOverride;
    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.ServiceOverride.name(), singletonList("values1.yaml"));

    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder().chartName(chartName).chartVersion("0.1.0").build();
    String workingDirectory = prepareChartDirectoryWithValuesFileForTest(chartName, valuesFileContent);

    doNothing().when(helmTaskHelper).initHelm(anyString(), any(HelmVersion.class), anyLong());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), contains("helm/path fetch chartName --untar"), anyString(),
            eq("fetch chart chartName"), anyLong(), any());

    try {
      Map<String, List<String>> result = helmTaskHelper.getValuesYamlFromChart(
          helmChartConfigParams, LONG_TIMEOUT_INTERVAL, null, mapK8sValuesLocationToFilePaths);
      assertThat(result.get(k8sValuesLocation.name()).get(0)).isEqualTo(valuesFileContent);
      assertThat(result.get(k8sValuesLocation.name()).size()).isEqualTo(1);
    } finally {
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    }
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testGetValuesYamlFromChartMissingYamlForEnvOverride() throws Exception {
    String valuesFileContent = "var: value";
    String chartName = "chartName";

    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.ServiceOverride.name(), singletonList("values1.yaml"));
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.Environment.name(), singletonList("values2.yaml"));

    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder().chartName(chartName).chartVersion("0.1.0").build();
    String workingDirectory = prepareChartDirectoryWithValuesFileForTest(chartName, valuesFileContent);

    doNothing().when(helmTaskHelper).initHelm(anyString(), any(HelmVersion.class), anyLong());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), contains("helm/path fetch chartName --untar"), anyString(),
            eq("fetch chart chartName"), anyLong(), eq(HelmCliCommandType.FETCH));

    try {
      Map<String, List<String>> result = helmTaskHelper.getValuesYamlFromChart(
          helmChartConfigParams, LONG_TIMEOUT_INTERVAL, null, mapK8sValuesLocationToFilePaths);
      assertThat(result).isNullOrEmpty();
    } catch (Exception ex) {
      assertThat(ex.getMessage()).isEqualTo("Required values yaml file with path values2.yaml not found");
      assertThat(ex).isInstanceOf(InvalidArgumentsException.class);
    } finally {
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    }
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testGetValuesYamlFromChartMultipleCommaSeparatedFiles() throws Exception {
    String valuesFileContent = "var: value";
    String valuesFileContent1EnvOverride = "var: valueEnv1";
    String valuesFileContent2EnvOverride = "var: valueEnv2";
    String chartName = "chartName";

    K8sValuesLocation k8sValuesLocation = K8sValuesLocation.ServiceOverride;
    K8sValuesLocation k8sValuesLocationEnvOverride = K8sValuesLocation.Environment;
    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.ServiceOverride.name(), singletonList("values1.yaml"));
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.Environment.name(), asList("values2.yaml", "values3.yaml"));

    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder().chartName(chartName).chartVersion("0.1.0").build();

    Map<String, String> mapValuesFileContent = new HashMap<>();
    mapValuesFileContent.put("values1.yaml", valuesFileContent);
    mapValuesFileContent.put("values2.yaml", valuesFileContent1EnvOverride);
    mapValuesFileContent.put("values3.yaml", valuesFileContent2EnvOverride);
    String workingDirectory = prepareChartDirectoryWithValuesFileForTest(chartName, mapValuesFileContent);

    doNothing().when(helmTaskHelper).initHelm(anyString(), any(HelmVersion.class), anyLong());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), contains("helm/path fetch chartName --untar"), anyString(),
            eq("fetch chart chartName"), anyLong(), any());

    try {
      Map<String, List<String>> result = helmTaskHelper.getValuesYamlFromChart(
          helmChartConfigParams, LONG_TIMEOUT_INTERVAL, null, mapK8sValuesLocationToFilePaths);
      assertThat(result.get(k8sValuesLocation.name()).size()).isEqualTo(1);
      assertThat(result.get(k8sValuesLocationEnvOverride.name()).size()).isEqualTo(2);
      assertThat(result.get(k8sValuesLocation.name()).get(0)).isEqualTo(valuesFileContent);
      assertThat(result.get(k8sValuesLocationEnvOverride.name()).get(0)).isEqualTo(valuesFileContent1EnvOverride);
      assertThat(result.get(k8sValuesLocationEnvOverride.name()).get(1)).isEqualTo(valuesFileContent2EnvOverride);
    } finally {
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    }
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testGetValuesYamlFromChartMultipleFiles() throws Exception {
    String valuesFileContent = "var: value";
    String valuesFileContentEnvOverride = "var: valueEnv";
    String chartName = "chartName";

    K8sValuesLocation k8sValuesLocation = K8sValuesLocation.ServiceOverride;
    K8sValuesLocation k8sValuesLocationEnvOverride = K8sValuesLocation.Environment;
    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.ServiceOverride.name(), singletonList("values1.yaml"));
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.Environment.name(), singletonList("values2.yaml"));

    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder().chartName(chartName).chartVersion("0.1.0").build();

    Map<String, String> mapValuesFileContent = new HashMap<>();
    mapValuesFileContent.put("values1.yaml", valuesFileContent);
    mapValuesFileContent.put("values2.yaml", valuesFileContentEnvOverride);
    String workingDirectory = prepareChartDirectoryWithValuesFileForTest(chartName, mapValuesFileContent);

    doNothing().when(helmTaskHelper).initHelm(anyString(), any(HelmVersion.class), anyLong());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), contains("helm/path fetch chartName --untar"), anyString(),
            eq("fetch chart chartName"), anyLong(), any());

    try {
      Map<String, List<String>> result = helmTaskHelper.getValuesYamlFromChart(
          helmChartConfigParams, LONG_TIMEOUT_INTERVAL, null, mapK8sValuesLocationToFilePaths);
      assertThat(result.get(k8sValuesLocation.name()).size()).isEqualTo(1);
      assertThat(result.get(k8sValuesLocationEnvOverride.name()).size()).isEqualTo(1);
      assertThat(result.get(k8sValuesLocation.name()).get(0)).isEqualTo(valuesFileContent);
      assertThat(result.get(k8sValuesLocationEnvOverride.name()).get(0)).isEqualTo(valuesFileContentEnvOverride);
    } finally {
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesYamlFromChartNoValuesYaml() throws Exception {
    K8sValuesLocation k8sValuesLocation = K8sValuesLocation.ServiceOverride;
    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.ServiceOverride.name(), singletonList("values1.yaml"));

    String chartName = "chartName";
    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder().chartName(chartName).chartVersion("0.1.0").build();
    String workingDirectory = prepareChartDirectoryWithValuesFileForTest(chartName, "");

    doNothing().when(helmTaskHelper).initHelm(anyString(), any(HelmVersion.class), anyLong());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), contains("helm/path fetch"), anyString(), eq("fetch chart chartName"), anyLong(),
            eq(HelmCliCommandType.FETCH));

    try {
      Map<String, List<String>> result = helmTaskHelper.getValuesYamlFromChart(
          helmChartConfigParams, LONG_TIMEOUT_INTERVAL, null, mapK8sValuesLocationToFilePaths);
      assertThat(result).isNullOrEmpty();
    } catch (Exception ex) {
      assertThat(ex.getMessage()).isEqualTo("Required values yaml file with path values1.yaml not found");
      assertThat(ex).isInstanceOf(InvalidArgumentsException.class);
    } finally {
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesYamlFromChartPopulateChartVersion() throws Exception {
    String chartName = "chartName";
    String chartVersion = "1.0.0";
    HelmChartConfigParams helmChartConfigParams = HelmChartConfigParams.builder().chartName(chartName).build();

    String workingDirectory = prepareChartDirectoryWithValuesFileForTest(chartName, "content");
    K8sValuesLocation k8sValuesLocation = K8sValuesLocation.ServiceOverride;
    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.ServiceOverride.name(), singletonList("values1.yaml"));

    doNothing().when(helmTaskHelper).initHelm(anyString(), any(HelmVersion.class), anyLong());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), contains("helm/path fetch"), anyString(), eq("fetch chart chartName"), anyLong(),
            eq(HelmCliCommandType.FETCH));
    doReturn(HelmChartInfo.builder().version(chartVersion).build())
        .when(helmTaskHelper)
        .getHelmChartInfoFromChartsYamlFile(anyString());

    helmTaskHelper.getValuesYamlFromChart(
        helmChartConfigParams, LONG_TIMEOUT_INTERVAL, null, mapK8sValuesLocationToFilePaths);
    assertThat(helmChartConfigParams.getChartVersion()).isEqualTo(chartVersion);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesYamlFromChartUnableToPopulateChartVersion() throws Exception {
    String chartName = "chartName";
    HelmChartConfigParams helmChartConfigParams = HelmChartConfigParams.builder().chartName("chartName").build();

    K8sValuesLocation k8sValuesLocation = K8sValuesLocation.ServiceOverride;
    Map<String, List<String>> mapK8sValuesLocationToFilePaths = new HashMap<>();
    mapK8sValuesLocationToFilePaths.put(K8sValuesLocation.ServiceOverride.name(), singletonList("values1.yaml"));

    String workingDirectory = prepareChartDirectoryWithValuesFileForTest(chartName, "fileContent");
    doNothing().when(helmTaskHelper).initHelm(anyString(), any(HelmVersion.class), anyLong());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), contains("helm/path fetch"), anyString(), eq("fetch chart chartName"), anyLong(),
            eq(HelmCliCommandType.FETCH));
    doThrow(new RuntimeException("Unable to fetch version"))
        .when(helmTaskHelper)
        .getHelmChartInfoFromChartsYamlFile(anyString());

    assertThatCode(()
                       -> helmTaskHelper.getValuesYamlFromChart(
                           helmChartConfigParams, LONG_TIMEOUT_INTERVAL, null, mapK8sValuesLocationToFilePaths))
        .doesNotThrowAnyException();
  }

  private String prepareChartDirectoryWithValuesFileForTest(String chartName, String valuesFileContent)
      throws IOException {
    String workingDirectory = Files.createTempDirectory("get-values-yaml-chart").toString();
    Files.createDirectory(Paths.get(workingDirectory, chartName));
    doReturn(workingDirectory).when(helmTaskHelper).createNewDirectoryAtPath(anyString());
    doReturn("helm/path").when(k8sGlobalConfigService).getHelmPath(any(HelmVersion.class));
    if (!isEmpty(valuesFileContent)) {
      Files.write(Paths.get(workingDirectory, chartName, "values.yaml"), valuesFileContent.getBytes());
      Files.write(Paths.get(workingDirectory, chartName, "values1.yaml"), valuesFileContent.getBytes());
    }

    return workingDirectory;
  }

  private String prepareChartDirectoryWithValuesFileForTest(String chartName, Map<String, String> mapValuesFileContent)
      throws IOException {
    String workingDirectory = Files.createTempDirectory("get-values-yaml-chart").toString();
    Files.createDirectory(Paths.get(workingDirectory, chartName));
    doReturn(workingDirectory).when(helmTaskHelper).createNewDirectoryAtPath(anyString());
    doReturn("helm/path").when(k8sGlobalConfigService).getHelmPath(any(HelmVersion.class));
    if (mapValuesFileContent != null) {
      mapValuesFileContent.forEach((key, value) -> {
        try {
          Files.write(Paths.get(workingDirectory, chartName, key), value.getBytes());
        } catch (IOException e) {
        }
      });
    }

    return workingDirectory;
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAddHelmRepo() {
    StartedProcess chartMuseumProcess = mock(StartedProcess.class);
    ProcessResult success = new ProcessResult(0, new ProcessOutput("output".getBytes()));
    ProcessResult failure = new ProcessResult(1, new ProcessOutput("error".getBytes()));

    assertThatCode(() -> testAddHelmRepo(chartMuseumProcess, success)).doesNotThrowAnyException();
    assertThatThrownBy(() -> testAddHelmRepo(chartMuseumProcess, failure))
        .isInstanceOf(HelmClientException.class)
        .hasMessageContaining("Failed to add helm repo. Executed command");
    assertThatThrownBy(() -> testAddHelmRepo(null, null)).isInstanceOf(InvalidRequestException.class);
  }

  private void testAddHelmRepo(StartedProcess chartMuseumProcess, ProcessResult addRepoResult) throws Exception {
    HelmRepoConfig helmRepoConfig = AmazonS3HelmRepoConfig.builder().bucketName("bucket").build();
    SettingValue connector = AwsConfig.builder().encryptedSecretKey("secretKey").build();
    String workingDir = "workingDir";
    String basePath = "base/path";
    String repo = "repo";
    ChartMuseumServer museumServer = null;
    if (chartMuseumProcess != null) {
      museumServer = ChartMuseumServer.builder().port(8888).startedProcess(chartMuseumProcess).build();
    }

    doReturn(workingDir).when(helmTaskHelper).createNewDirectoryAtPath(anyString());
    doReturn(addRepoResult)
        .when(helmTaskHelperBase)
        .executeCommand(
            anyMap(), contains("helm repo add"), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));
    if (museumServer != null) {
      doReturn(museumServer)
          .when(chartMuseumClient)
          .startChartMuseumServer(helmRepoConfig, connector, workingDir, basePath, false);
    } else {
      doThrow(new InvalidRequestException("Something went wrong"))
          .when(chartMuseumClient)
          .startChartMuseumServer(helmRepoConfig, connector, workingDir, basePath, false);
    }

    helmTaskHelper.addHelmRepo(helmRepoConfig, connector, repo, repo, workingDir, basePath, V2, false);
    if (museumServer != null) {
      verify(chartMuseumClient, times(1)).stopChartMuseumServer(chartMuseumProcess);
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFilteredFiles() {
    FileData file1 = FileData.builder().filePath("dir/file1").build();
    FileData file2 = FileData.builder().filePath("dir/file2").build();
    FileData file3 = FileData.builder().filePath("dir/file3").build();
    List<FileData> emptyFiles = Collections.emptyList();
    List<FileData> singleFile = singletonList(file1);
    List<FileData> multipleFiles = asList(file1, file2, file3);

    assertThat(helmTaskHelper.getFilteredFiles(emptyFiles, Collections.emptyList())).isEmpty();
    assertThat(helmTaskHelper.getFilteredFiles(emptyFiles, asList("file1", "file2"))).isEmpty();
    assertThat(helmTaskHelper.getFilteredFiles(singleFile, Collections.emptyList())).isEmpty();
    assertThat(helmTaskHelper.getFilteredFiles(singleFile, asList("missing1", "missing2"))).isEmpty();
    assertThat(helmTaskHelper.getFilteredFiles(singleFile, singletonList("dir/file1")))
        .containsExactlyInAnyOrder(file1);
    assertThat(helmTaskHelper.getFilteredFiles(singleFile, asList("dir/file1", "missing")))
        .containsExactlyInAnyOrder(file1);
    assertThat(helmTaskHelper.getFilteredFiles(multipleFiles, asList("missing1", "missing2"))).isEmpty();
    assertThat(helmTaskHelper.getFilteredFiles(multipleFiles, singletonList("dir/file1")))
        .containsExactlyInAnyOrder(file1);
    assertThat(helmTaskHelper.getFilteredFiles(multipleFiles, asList("dir/file1", "dir/file2", "dir/file3")))
        .containsExactlyInAnyOrder(file1, file2, file3);
    assertThat(helmTaskHelper.getFilteredFiles(multipleFiles, asList("dir/file1", "dir/file2", "misssing")))
        .containsExactlyInAnyOrder(file1, file2);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testReturnEmptyHelmChartsForEmptyResponse() throws Exception {
    HttpHelmRepoConfig httpHelmRepoConfig =
        HttpHelmRepoConfig.builder().accountId(ACCOUNT_ID).chartRepoUrl("http://127.0.0.1:1234").build();
    HelmChartCollectionParams helmChartCollectionParams =
        HelmChartCollectionParams.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .appManifestId(MANIFEST_ID)
            .serviceId(SERVICE_ID)
            .helmChartConfigParams(getHelmChartConfigParams(httpHelmRepoConfig))
            .build();

    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelper)
        .createDirectory("dir");
    doReturn(processExecutor)
        .when(helmTaskHelper)
        .createProcessExecutorWithRedirectOutput(eq(Collections.emptyMap()),
            eq("v3/helm search repo repoName/chartName -l --devel --max-col-width 300"), eq("dir"), any());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> helmTaskHelper.fetchChartVersions(helmChartCollectionParams, "dir", 10000))
        .withMessageContaining("No chart with the given name found. Chart might be deleted at source");
    verify(processExecutor, times(3)).execute();
    verify(helmTaskHelper, times(1)).initHelm("dir", V3, 10000);

    doReturn("No results Found")
        .when(helmTaskHelper)
        .executeCommandWithLogOutput(anyMap(), eq("v3/helm search repo repoName/chartName -l"), eq("dir"), anyString(),
            any(HelmCliCommandType.class));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> helmTaskHelper.fetchChartVersions(helmChartCollectionParams, "dir", 10000))
        .withMessageContaining("No chart with the given name found. Chart might be deleted at source");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchVersionsFromHttp() throws Exception {
    HttpHelmRepoConfig httpHelmRepoConfig =
        HttpHelmRepoConfig.builder().accountId(ACCOUNT_ID).chartRepoUrl("http://127.0.0.1:1234").build();
    HelmChartCollectionParams helmChartCollectionParams =
        HelmChartCollectionParams.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .appManifestId(MANIFEST_ID)
            .serviceId(SERVICE_ID)
            .helmChartConfigParams(getHelmChartConfigParams(httpHelmRepoConfig))
            .build();

    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelper)
        .createDirectory("dir");
    doReturn(getHelmCollectionResult(""))
        .when(helmTaskHelper)
        .executeCommandWithLogOutput(
            anyMap(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq("dir"), anyString(), any(HelmCliCommandType.class));

    List<HelmChart> helmCharts = helmTaskHelper.fetchChartVersions(helmChartCollectionParams, "dir", 10000);
    assertThat(helmCharts.size()).isEqualTo(2);
    assertThat(helmCharts.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(helmCharts.get(0).getAppId()).isEqualTo(APP_ID);
    assertThat(helmCharts.get(0).getApplicationManifestId()).isEqualTo(MANIFEST_ID);
    assertThat(helmCharts.get(0).getVersion()).isEqualTo("1.0.2");
    assertThat(helmCharts.get(1).getVersion()).isEqualTo("1.0.1");
    verify(processExecutor, times(2)).execute();

    doReturn(getHelmCollectionResult("11.0.1"))
        .when(helmTaskHelper)
        .executeCommandWithLogOutput(
            anyMap(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq("dir"), anyString(), any(HelmCliCommandType.class));
    helmCharts = helmTaskHelper.fetchChartVersions(helmChartCollectionParams, "dir", 10000);
    assertThat(helmCharts).hasSize(2);
    assertThat(helmCharts.get(0).getDescription()).isEqualTo("Deploys harness delegate");
    assertThat(helmCharts.get(0).getAppVersion()).isEqualTo("11.0.1");
    assertThat(helmCharts.get(0).getVersion()).isEqualTo("1.0.2");
    assertThat(helmCharts.get(1).getVersion()).isEqualTo("1.0.1");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchVersionsFromGcs() throws Exception {
    GCSHelmRepoConfig gcsHelmRepoConfig =
        GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).bucketName("bucketName").build();
    HelmChartConfigParams helmChartConfigParams = getHelmChartConfigParams(gcsHelmRepoConfig);
    HelmChartCollectionParams helmChartCollectionParams = HelmChartCollectionParams.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .appManifestId(MANIFEST_ID)
                                                              .serviceId(SERVICE_ID)
                                                              .helmChartConfigParams(helmChartConfigParams)
                                                              .build();
    ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(1234).build();

    doReturn(chartMuseumServer)
        .when(chartMuseumClient)
        .startChartMuseumServer(gcsHelmRepoConfig, helmChartConfigParams.getConnectorConfig(), RESOURCE_DIR_BASE,
            helmChartConfigParams.getBasePath(), false);
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelper)
        .createNewDirectoryAtPath(RESOURCE_DIR_BASE);
    doReturn(getHelmCollectionResult(""))
        .when(helmTaskHelper)
        .executeCommandWithLogOutput(
            anyMap(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq("dir"), anyString(), any(HelmCliCommandType.class));

    List<HelmChart> helmCharts = helmTaskHelper.fetchChartVersions(helmChartCollectionParams, "dir", 10000);
    assertThat(helmCharts.size()).isEqualTo(2);
    assertThat(helmCharts.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(helmCharts.get(0).getAppId()).isEqualTo(APP_ID);
    assertThat(helmCharts.get(0).getApplicationManifestId()).isEqualTo(MANIFEST_ID);
    assertThat(helmCharts.get(0).getVersion()).isEqualTo("1.0.2");
    assertThat(helmCharts.get(1).getVersion()).isEqualTo("1.0.1");
    verify(processExecutor, times(1)).execute();
    verify(chartMuseumClient, times(1))
        .startChartMuseumServer(gcsHelmRepoConfig, helmChartConfigParams.getConnectorConfig(), RESOURCE_DIR_BASE,
            helmChartConfigParams.getBasePath(), false);
  }

  @NotNull
  private String getHelmCollectionResult(String appVersion) {
    return "NAME\tCHART VERSION\tAPP VERSION\tDESCRIPTION\n"
        + "repoName/chartName\t1.0.2\t" + appVersion + "\tDeploys harness delegate\n"
        + "repoName/chartName\t1.0.1\t0\tDeploys harness delegate";
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testFetchHelmVersionsForV2() throws Exception {
    HttpHelmRepoConfig httpHelmRepoConfig =
        HttpHelmRepoConfig.builder().accountId(ACCOUNT_ID).chartRepoUrl("http://127.0.0.1:1234").build();
    HelmChartConfigParams helmChartConfigParams = getHelmChartConfigParams(httpHelmRepoConfig);
    helmChartConfigParams.setHelmVersion(V2);
    HelmChartCollectionParams helmChartCollectionParams = HelmChartCollectionParams.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .appManifestId(MANIFEST_ID)
                                                              .serviceId(SERVICE_ID)
                                                              .helmChartConfigParams(helmChartConfigParams)
                                                              .build();

    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelper)
        .createDirectory("dir");
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .applyHelmHomePath("v2/helm search repoName/chartName -l ${HELM_HOME_PATH_FLAG} --col-width 300", "dir");
    doReturn(getHelmCollectionResult(""))
        .when(helmTaskHelper)
        .executeCommandWithLogOutput(anyMap(),
            eq("v2/helm search repoName/chartName -l ${HELM_HOME_PATH_FLAG} --col-width 300"), eq("dir"), anyString(),
            any(HelmCliCommandType.class));

    List<HelmChart> helmCharts = helmTaskHelper.fetchChartVersions(helmChartCollectionParams, "dir", 10000);
    assertThat(helmCharts.size()).isEqualTo(2);
    assertThat(helmCharts.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(helmCharts.get(0).getAppId()).isEqualTo(APP_ID);
    assertThat(helmCharts.get(0).getApplicationManifestId()).isEqualTo(MANIFEST_ID);
    assertThat(helmCharts.get(0).getVersion()).isEqualTo("1.0.2");
    assertThat(helmCharts.get(1).getVersion()).isEqualTo("1.0.1");
    // For helm version 2, we execute another command for helm init apart from add repo
    verify(processExecutor, times(3)).execute();
    verify(helmTaskHelper, times(1)).initHelm("dir", V2, 10000);
  }

  @Test
  @Owner(developers = PRABU)
  @Category({UnitTests.class})
  public void testFetchVersionsTimesOut() throws Exception {
    GCSHelmRepoConfig gcsHelmRepoConfig =
        GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).bucketName("bucketName").build();
    HelmChartConfigParams helmChartConfigParams = getHelmChartConfigParams(gcsHelmRepoConfig);
    HelmChartCollectionParams helmChartCollectionParams = HelmChartCollectionParams.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .appManifestId(MANIFEST_ID)
                                                              .serviceId(SERVICE_ID)
                                                              .helmChartConfigParams(helmChartConfigParams)
                                                              .build();
    ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(1234).build();

    doReturn(chartMuseumServer)
        .when(chartMuseumClient)
        .startChartMuseumServer(gcsHelmRepoConfig, helmChartConfigParams.getConnectorConfig(), RESOURCE_DIR_BASE,
            helmChartConfigParams.getBasePath(), false);
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelper)
        .createNewDirectoryAtPath(RESOURCE_DIR_BASE);
    doAnswer(new Answer() {
      private int count = 0;

      public Object answer(InvocationOnMock invocation) throws TimeoutException {
        if (count++ == 0) {
          return new ProcessResult(0, null);
        }
        throw new TimeoutException();
      }
    })
        .when(processExecutor)
        .execute();
    doReturn(processExecutor)
        .when(helmTaskHelper)
        .createProcessExecutorWithRedirectOutput(any(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq("dir"), any());

    assertThatThrownBy(() -> helmTaskHelper.fetchChartVersions(helmChartCollectionParams, "dir", 10000))
        .isInstanceOf(HelmClientException.class)
        .hasMessage("[Timed out] Helm chart fetch versions command failed ");

    verify(chartMuseumClient).stopChartMuseumServer(chartMuseumServer.getStartedProcess());
  }

  @Test
  @Owner(developers = PRABU)
  @Category({UnitTests.class})
  public void testCleanupAfterCollection() throws Exception {
    GCSHelmRepoConfig gcsHelmRepoConfig =
        GCSHelmRepoConfig.builder().accountId(ACCOUNT_ID).bucketName("bucketName").build();
    HelmChartConfigParams helmChartConfigParams = getHelmChartConfigParams(gcsHelmRepoConfig);
    HelmChartCollectionParams helmChartCollectionParams = HelmChartCollectionParams.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .appManifestId(MANIFEST_ID)
                                                              .serviceId(SERVICE_ID)
                                                              .helmChartConfigParams(helmChartConfigParams)
                                                              .build();
    ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(1234).build();

    doNothing().when(chartMuseumClient).stopChartMuseumServer(chartMuseumServer.getStartedProcess());
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doNothing().when(helmTaskHelper).cleanup("dir");

    helmTaskHelper.cleanupAfterCollection(helmChartCollectionParams, "dir", 10000);
    verify(helmTaskHelper, times(1)).cleanup("dir");
    verify(processExecutor, times(1)).execute();
    verify(chartMuseumClient, never()).stopChartMuseumServer(chartMuseumServer.getStartedProcess());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitHelmForwardToHelmTaskHelperBase() throws Exception {
    String workingDirectory = "/working/directory";
    doNothing().when(helmTaskHelperBase).initHelm(workingDirectory, V2, LONG_TIMEOUT_INTERVAL);
    assertThatCode(() -> helmTaskHelper.initHelm("/working/directory", V2, LONG_TIMEOUT_INTERVAL))
        .doesNotThrowAnyException();
    verify(helmTaskHelperBase).initHelm(workingDirectory, V2, LONG_TIMEOUT_INTERVAL);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveRepoForwardHelmTaskHelperBase() {
    final String workingDirectory = "/working/directory";
    final String repoName = "repoName";
    doNothing().when(helmTaskHelperBase).removeRepo(repoName, workingDirectory, V2, LONG_TIMEOUT_INTERVAL);

    assertThatCode(() -> helmTaskHelper.removeRepo(repoName, workingDirectory, V2, LONG_TIMEOUT_INTERVAL))
        .doesNotThrowAnyException();

    verify(helmTaskHelperBase)
        .removeRepo(repoName, workingDirectory, V2, LONG_TIMEOUT_INTERVAL, false, StringUtils.EMPTY);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDownloadAndUnzipCustomSourceManifestFiles() throws IOException {
    final String workingDirPath = "./repository/helm/work/ACTIVITY_ID";
    final String sourceDirPath = "./repository/helm/source/manifests";
    final String zipDirPath = "./repository/helm/zip/ACTIVITY_ID";
    final String zipFilePath = format("%s/destZipFile.zip", zipDirPath);
    final String fileId = "fileId";

    FileIo.createDirectoryIfDoesNotExist(workingDirPath);
    FileIo.createDirectoryIfDoesNotExist(sourceDirPath);
    Files.createFile(Paths.get(sourceDirPath, "test1.yaml"));
    Files.createFile(Paths.get(sourceDirPath, "test2.yaml"));
    FileIo.createDirectoryIfDoesNotExist(zipDirPath);

    zipManifestDirectory(sourceDirPath, zipFilePath);
    InputStream targetStream = FileUtils.openInputStream(new File(zipFilePath));

    doReturn(targetStream)
        .when(delegateFileManagerBase)
        .downloadByFileId(FileBucket.CUSTOM_MANIFEST, fileId, ACCOUNT_ID);

    helmTaskHelper.downloadAndUnzipCustomSourceManifestFiles(workingDirPath, fileId, ACCOUNT_ID);

    File destFile = new File(workingDirPath);
    assertThat(destFile).exists();
    String[] unzippedFiles = destFile.list((dir, name) -> !dir.isHidden());
    assertThat(unzippedFiles).hasSize(1);
    assertThat(unzippedFiles[0]).contains("manifests");
    Path path = Paths.get(workingDirPath, unzippedFiles[0]);
    File file = new File(path.toString());
    assertThat(file.list()).contains("test1.yaml", "test2.yaml");
    deleteDirectoryAndItsContentIfExists(workingDirPath);
    deleteDirectoryAndItsContentIfExists(sourceDirPath);
    deleteDirectoryAndItsContentIfExists(zipDirPath);
  }
}