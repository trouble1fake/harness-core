/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.CHART_MUSEUM_SERVER_URL;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GCS_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.HTTP_HELM;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.RESOURCE_DIR_BASE;
import static io.harness.exception.WingsException.USER;
import static io.harness.helm.HelmConstants.HELM_HOME_PATH_FLAG;
import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.chartmuseum.ChartMuseumServer;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.chartmuseum.NGChartMuseumService;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HelmClientException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmCommandTemplateFactory;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HelmTaskHelperBaseTest extends CategoryTest {
  private static final String CHART_NAME = "test-helm-chart";
  private static final String CHART_VERSION = "1.0.0";
  private static final String REPO_NAME = "helm_charts";
  private static final String REPO_DISPLAY_NAME = "Helm Charts";

  @Mock K8sGlobalConfigService k8sGlobalConfigService;
  @Mock NGChartMuseumService ngChartMuseumService;

  @InjectMocks @Spy HelmTaskHelperBase helmTaskHelperBase;

  @Mock ProcessExecutor processExecutor;
  @Mock LogCallback logCallback;

  final HelmCommandFlag emptyHelmCommandFlag = HelmCommandFlag.builder().build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    doReturn("v2/helm").when(k8sGlobalConfigService).getHelmPath(HelmVersion.V2);
    doReturn("v3/helm").when(k8sGlobalConfigService).getHelmPath(V3);

    doReturn(processExecutor)
        .when(helmTaskHelperBase)
        .createProcessExecutor(anyString(), anyString(), anyLong(), anyMap());
    doReturn(processExecutor)
        .when(helmTaskHelperBase)
        .createProcessExecutor(anyString(), anyString(), anyLong(), anyMap());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitHelm() throws Exception {
    String workingDirectory = "/working/directory";
    String expectedInitCommand = format("v2/helm init -c --skip-refresh --home %s/helm", workingDirectory);
    doReturn(workingDirectory).when(helmTaskHelperBase).createNewDirectoryAtPath(anyString());
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(Collections.emptyMap(), expectedInitCommand, workingDirectory,
            "Initing helm Command " + expectedInitCommand, 9000L, HelmCliCommandType.INIT);
    assertThatCode(() -> helmTaskHelperBase.initHelm("/working/directory", V2, 9000L)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitHelmFailed() throws Exception {
    String workingDirectory = "/working/directory";
    String expectedInitCommand = format("v2/helm init -c --skip-refresh --home %s/helm", workingDirectory);
    doReturn(workingDirectory).when(helmTaskHelperBase).createNewDirectoryAtPath(anyString());
    doReturn(new ProcessResult(1, new ProcessOutput("something went wrong executing command".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(Collections.emptyMap(), expectedInitCommand, workingDirectory,
            "Initing helm Command " + expectedInitCommand, 9000L, HelmCliCommandType.INIT);
    assertThatThrownBy(() -> helmTaskHelperBase.initHelm("/working/directory", V2, 9000L))
        .isInstanceOf(HelmClientException.class)
        .hasMessageContaining("Failed to init helm")
        .hasMessageContaining("something went wrong executing command");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAddRepo() {
    testAddRepoSuccess();
    testAddRepoIfProcessExecException();
    testAddRepoIfHelmCommandFailed();
  }

  private void testAddRepoIfHelmCommandFailed() {
    doReturn(new ProcessResult(1, new ProcessOutput(new byte[1])))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));
    assertThatExceptionOfType(HelmClientException.class)
        .isThrownBy(()
                        -> helmTaskHelperBase.addRepo("vault", "vault", "https://helm-server", "admin",
                            "secret-text".toCharArray(), "/home", V3, 9000L, false))
        .withMessageContaining(
            "Failed to add helm repo. Executed command v3/helm repo add vault https://helm-server --username admin --password *******");
  }

  private void testAddRepoIfProcessExecException() {
    doThrow(new HelmClientException("ex", HelmCliCommandType.REPO_ADD))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));

    assertThatExceptionOfType(HelmClientException.class)
        .isThrownBy(()
                        -> helmTaskHelperBase.addRepo("vault", "vault", "https://helm-server", "admin",
                            "secret-text".toCharArray(), "/home", V3, 9000L, false));
  }

  private void testAddRepoSuccess() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

    doReturn(new ProcessResult(0, new ProcessOutput(new byte[1])))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));
    helmTaskHelperBase.addRepo(
        "vault", "vault", "https://helm-server", "admin", "secret-text".toCharArray(), "/home", V3, 9000L, false);

    verify(helmTaskHelperBase, times(1))
        .executeCommand(
            anyMap(), captor.capture(), captor.capture(), captor.capture(), eq(9000L), eq(HelmCliCommandType.REPO_ADD));

    assertThat(captor.getAllValues().get(0))
        .isEqualTo("v3/helm repo add vault https://helm-server --username admin --password secret-text");
    assertThat(captor.getAllValues().get(1)).isEqualTo("/home");
    assertThat(captor.getAllValues().get(2))
        .isEqualTo(
            "add helm repo. Executed commandv3/helm repo add vault https://helm-server --username admin --password *******");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAddRepoEmptyPasswordAndUsername() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

    doReturn(new ProcessResult(0, new ProcessOutput(new byte[1])))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.REPO_ADD));
    helmTaskHelperBase.addRepo("vault", "vault", "https://helm-server", null, null, "/home", V3, 9000L, false);

    verify(helmTaskHelperBase, times(1))
        .executeCommand(
            anyMap(), captor.capture(), captor.capture(), captor.capture(), eq(9000L), eq(HelmCliCommandType.REPO_ADD));

    assertThat(captor.getAllValues().get(0)).isEqualTo("v3/helm repo add vault https://helm-server  ");
    assertThat(captor.getAllValues().get(1)).isEqualTo("/home");
    assertThat(captor.getAllValues().get(2))
        .isEqualTo("add helm repo. Executed commandv3/helm repo add vault https://helm-server  ");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmFetchCommandV2() {
    String workingDirectory = "/pwd";
    String expectedCommand = String.format("v2/helm fetch %s/%s --untar --version %s --home %s/helm", REPO_NAME,
        CHART_NAME, CHART_VERSION, workingDirectory);

    String fetchCommand = helmTaskHelperBase.getHelmFetchCommand(
        CHART_NAME, CHART_VERSION, REPO_NAME, workingDirectory, HelmVersion.V2, emptyHelmCommandFlag);
    assertThat(fetchCommand).isEqualTo(expectedCommand);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmFetchCommandV3() {
    String expectedCommand =
        String.format("v3/helm pull %s/%s  --untar --version %s", REPO_NAME, CHART_NAME, CHART_VERSION);

    String fetchCommand =
        helmTaskHelperBase.getHelmFetchCommand(CHART_NAME, CHART_VERSION, REPO_NAME, "/pwd", V3, emptyHelmCommandFlag);
    assertThat(fetchCommand).isEqualTo(expectedCommand);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmFetchCommandEmptyRepoName() {
    String chartName = "chart-empty-repo";
    String chartVersion = "1.0.0";
    String expectedCommand = String.format("v3/helm pull %s  --untar --version %s", chartName, chartVersion);

    String fetchCommand =
        helmTaskHelperBase.getHelmFetchCommand(chartName, chartVersion, "", "/pwd", V3, emptyHelmCommandFlag);
    assertThat(fetchCommand).isEqualTo(expectedCommand);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchChartFromRepo() {
    doReturn(new ProcessResult(0, new ProcessOutput(null)))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.FETCH));

    assertThatCode(()
                       -> helmTaskHelperBase.fetchChartFromRepo(
                           "repo", "repo display", "chart", "1.0.0", "/dir", V3, emptyHelmCommandFlag, 90000, false))
        .doesNotThrowAnyException();

    verify(helmTaskHelperBase, times(1))
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.FETCH));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchChartFromRepoFailed() {
    String cliError = "some helm command error";

    doReturn(new ProcessResult(123, new ProcessOutput(cliError.getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.FETCH));

    assertThatThrownBy(()
                           -> helmTaskHelperBase.fetchChartFromRepo(REPO_NAME, REPO_DISPLAY_NAME, CHART_NAME,
                               CHART_VERSION, "/dir", V3, emptyHelmCommandFlag, 90000, false))
        .isInstanceOf(HelmClientException.class);

    verify(helmTaskHelperBase, times(1))
        .executeCommand(anyMap(), anyString(), anyString(), anyString(), anyLong(), eq(HelmCliCommandType.FETCH));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesFromHttpRepo() {
    String repoUrl = "https://repo-chart/";
    String username = "username";
    char[] password = "password".toCharArray();
    String chartOutput = "/directory";
    long timeout = 90000L;

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .chartName(CHART_NAME)
            .chartVersion(CHART_VERSION)
            .helmVersion(V3)
            .helmCommandFlag(emptyHelmCommandFlag)
            .storeDelegateConfig(
                HttpHelmStoreDelegateConfig.builder()
                    .repoName(REPO_NAME)
                    .repoDisplayName(REPO_DISPLAY_NAME)
                    .httpHelmConnector(
                        HttpHelmConnectorDTO.builder()
                            .helmRepoUrl(repoUrl)
                            .auth(HttpHelmAuthenticationDTO.builder()
                                      .authType(HttpHelmAuthType.USER_PASSWORD)
                                      .credentials(
                                          HttpHelmUsernamePasswordDTO.builder()
                                              .username(username)
                                              .passwordRef(SecretRefData.builder().decryptedValue(password).build())
                                              .build())
                                      .build())
                            .build())
                    .build())
            .build();

    doNothing()
        .when(helmTaskHelperBase)
        .addRepo(REPO_NAME, REPO_DISPLAY_NAME, repoUrl, username, password, chartOutput, V3, timeout, false);
    doNothing()
        .when(helmTaskHelperBase)
        .fetchChartFromRepo(REPO_NAME, REPO_DISPLAY_NAME, CHART_NAME, CHART_VERSION, chartOutput, V3,
            emptyHelmCommandFlag, timeout, false);

    helmTaskHelperBase.downloadChartFilesFromHttpRepo(helmChartManifestDelegateConfig, chartOutput, timeout);

    verify(helmTaskHelperBase, times(1))
        .addRepo(REPO_NAME, REPO_DISPLAY_NAME, repoUrl, username, password, chartOutput, V3, timeout, false);
    verify(helmTaskHelperBase, times(1))
        .fetchChartFromRepo(REPO_NAME, REPO_DISPLAY_NAME, CHART_NAME, CHART_VERSION, chartOutput, V3,
            emptyHelmCommandFlag, timeout, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesFromHttpRepoAnonymousAuth() {
    String repoUrl = "http://repo";
    String chartOutput = "/dir";
    long timeout = 90000L;

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder()
            .chartName(CHART_NAME)
            .chartVersion(CHART_VERSION)
            .helmVersion(V3)
            .helmCommandFlag(emptyHelmCommandFlag)
            .storeDelegateConfig(
                HttpHelmStoreDelegateConfig.builder()
                    .repoName(REPO_NAME)
                    .repoDisplayName(REPO_DISPLAY_NAME)
                    .httpHelmConnector(
                        HttpHelmConnectorDTO.builder()
                            .helmRepoUrl(repoUrl)
                            .auth(HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.ANONYMOUS).build())
                            .build())
                    .build())
            .build();

    doNothing()
        .when(helmTaskHelperBase)
        .addRepo(REPO_NAME, REPO_DISPLAY_NAME, repoUrl, null, null, chartOutput, V3, timeout, false);
    doNothing()
        .when(helmTaskHelperBase)
        .fetchChartFromRepo(REPO_NAME, REPO_DISPLAY_NAME, CHART_NAME, CHART_VERSION, chartOutput, V3,
            emptyHelmCommandFlag, timeout, false);

    helmTaskHelperBase.downloadChartFilesFromHttpRepo(helmChartManifestDelegateConfig, chartOutput, timeout);

    verify(helmTaskHelperBase, times(1))
        .addRepo(REPO_NAME, REPO_DISPLAY_NAME, repoUrl, null, null, chartOutput, V3, timeout, false);
    verify(helmTaskHelperBase, times(1))
        .fetchChartFromRepo(REPO_NAME, REPO_DISPLAY_NAME, CHART_NAME, CHART_VERSION, chartOutput, V3,
            emptyHelmCommandFlag, timeout, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesFromHttpRepoInvalidStoreConfig() {
    S3HelmStoreDelegateConfig s3HelmStoreDelegateConfig = S3HelmStoreDelegateConfig.builder().build();
    HelmChartManifestDelegateConfig manifestDelegateConfig =
        HelmChartManifestDelegateConfig.builder().storeDelegateConfig(s3HelmStoreDelegateConfig).build();

    assertThatThrownBy(() -> helmTaskHelperBase.downloadChartFilesFromHttpRepo(manifestDelegateConfig, "output", 9000L))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetChartDirectory() {
    String chartName = "chart_name";
    String chartNameWithRepo = "public_repo/chart_name";
    String parentDirectory = "/manifests";

    assertThat(HelmTaskHelperBase.getChartDirectory(parentDirectory, chartName)).isEqualTo("/manifests/chart_name");
    assertThat(HelmTaskHelperBase.getChartDirectory(parentDirectory, chartNameWithRepo))
        .isEqualTo("/manifests/chart_name");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAddChartMuseumRepo() {
    final String chartDirectory = "chart_directory";
    final int port = 1234;
    final long timeoutInMillis = 9000L;

    doReturn(new ProcessResult(0, new ProcessOutput(null)))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), eq(chartDirectory), anyString(), eq(timeoutInMillis),
            eq(HelmCliCommandType.REPO_ADD));

    helmTaskHelperBase.addChartMuseumRepo(REPO_NAME, REPO_DISPLAY_NAME, port, chartDirectory, V3, timeoutInMillis);
    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(helmTaskHelperBase, times(1))
        .executeCommand(anyMap(), commandCaptor.capture(), eq(chartDirectory), anyString(), eq(timeoutInMillis),
            eq(HelmCliCommandType.REPO_ADD));
    String executedCommand = commandCaptor.getValue();
    assertThat(executedCommand).contains(CHART_MUSEUM_SERVER_URL.replace("${PORT}", Integer.toString(port)));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAddChartMuseumRepoFailed() {
    final String chartDirectory = "chart_directory";
    final int port = 1234;
    final long timeoutInMillis = 9000L;

    doReturn(new ProcessResult(1, new ProcessOutput("Something went wrong".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), anyString(), eq(chartDirectory), anyString(), eq(timeoutInMillis),
            eq(HelmCliCommandType.REPO_ADD));

    assertThatThrownBy(()
                           -> helmTaskHelperBase.addChartMuseumRepo(
                               REPO_NAME, REPO_DISPLAY_NAME, port, chartDirectory, V3, timeoutInMillis))
        .isInstanceOf(HelmClientException.class)
        .hasMessageContaining("Failed to add helm repo. Executed command");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesUsingChartMuseumS3() throws Exception {
    final S3HelmStoreDelegateConfig s3StoreDelegateConfig =
        S3HelmStoreDelegateConfig.builder().repoName(REPO_NAME).repoDisplayName(REPO_DISPLAY_NAME).build();
    testDownloadChartFilesUsingChartMuseum(s3StoreDelegateConfig);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadChartFilesUsingChartMuseumGCS() throws Exception {
    final GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
        GcsHelmStoreDelegateConfig.builder().repoName(REPO_NAME).repoDisplayName(REPO_DISPLAY_NAME).build();
    testDownloadChartFilesUsingChartMuseum(gcsHelmStoreDelegateConfig);
  }

  private void testDownloadChartFilesUsingChartMuseum(StoreDelegateConfig storeDelegateConfig) throws Exception {
    final String destinationDirectory = "destinationDirectory";
    final String resourceDirectory = "resourceDirectory";
    final long timeoutInMillis = 90000L;
    final int port = 33344;
    final ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(port).build();
    final HelmChartManifestDelegateConfig manifest = HelmChartManifestDelegateConfig.builder()
                                                         .chartName(CHART_NAME)
                                                         .chartVersion(CHART_VERSION)
                                                         .storeDelegateConfig(storeDelegateConfig)
                                                         .helmVersion(V3)
                                                         .build();

    doReturn(resourceDirectory).when(helmTaskHelperBase).createNewDirectoryAtPath(RESOURCE_DIR_BASE);
    doReturn(chartMuseumServer)
        .when(ngChartMuseumService)
        .startChartMuseumServer(storeDelegateConfig, resourceDirectory);
    doNothing()
        .when(helmTaskHelperBase)
        .addChartMuseumRepo(REPO_NAME, REPO_DISPLAY_NAME, port, destinationDirectory, V3, timeoutInMillis);
    doNothing()
        .when(helmTaskHelperBase)
        .fetchChartFromRepo(REPO_NAME, REPO_DISPLAY_NAME, CHART_NAME, CHART_VERSION, destinationDirectory, V3, null,
            timeoutInMillis, false);

    helmTaskHelperBase.downloadChartFilesUsingChartMuseum(manifest, destinationDirectory, timeoutInMillis);

    verify(ngChartMuseumService, times(1)).startChartMuseumServer(storeDelegateConfig, resourceDirectory);
    verify(ngChartMuseumService, times(1)).stopChartMuseumServer(chartMuseumServer);
    verify(helmTaskHelperBase, times(1))
        .addChartMuseumRepo(REPO_NAME, REPO_DISPLAY_NAME, port, destinationDirectory, V3, timeoutInMillis);
    verify(helmTaskHelperBase, times(1))
        .fetchChartFromRepo(REPO_NAME, REPO_DISPLAY_NAME, CHART_NAME, CHART_VERSION, destinationDirectory, V3, null,
            timeoutInMillis, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyHelmHomePath() {
    final String repoAddCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD, V2);
    String result = helmTaskHelperBase.applyHelmHomePath(repoAddCommand, "working");
    assertThat(result).isEqualTo(
        repoAddCommand.replace(HELM_HOME_PATH_FLAG, "--home" + helmTaskHelperBase.getHelmHomePath("working")));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApplyHelmHomePathEmptyWorkingDirectory() {
    final String repoAddCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD, V2);
    String result = helmTaskHelperBase.applyHelmHomePath(repoAddCommand, "");
    assertThat(result).isEqualTo(repoAddCommand.replace(HELM_HOME_PATH_FLAG, ""));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecuteCommand() throws Exception {
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    helmTaskHelperBase.executeCommand(Collections.emptyMap(), "", ".", "", 9000L, HelmCliCommandType.INIT);

    doThrow(new IOException()).when(processExecutor).execute();
    assertThatExceptionOfType(HelmClientException.class)
        .isThrownBy(()
                        -> helmTaskHelperBase.executeCommand(
                            Collections.emptyMap(), "", ".", "", 9000L, HelmCliCommandType.INIT))
        .withNoCause()
        .withMessageContaining("[IO exception]");

    doThrow(new InterruptedException()).when(processExecutor).execute();
    assertThatExceptionOfType(HelmClientException.class)
        .isThrownBy(()
                        -> helmTaskHelperBase.executeCommand(
                            Collections.emptyMap(), "", ".", "foo", 9000L, HelmCliCommandType.INIT))
        .withNoCause()
        .withMessageContaining("[Interrupted] foo");

    doThrow(new TimeoutException()).when(processExecutor).execute();
    assertThatExceptionOfType(HelmClientException.class)
        .isThrownBy(()
                        -> helmTaskHelperBase.executeCommand(
                            Collections.emptyMap(), "", ".", null, 9000L, HelmCliCommandType.INIT))
        .withNoCause()
        .withMessageContaining("[Timed out]");

    doThrow(new RuntimeException("test")).when(processExecutor).execute();
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(()
                        -> helmTaskHelperBase.executeCommand(
                            Collections.emptyMap(), "", ".", "", 9000L, HelmCliCommandType.INIT))
        .withNoCause()
        .withMessageContaining("test");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveRepo() {
    final String workingDirectory = "/working/directory";
    final String repoName = "repoName";
    final String expectedRemoveCommand = format("v2/helm repo remove %s --home %s/helm", repoName, workingDirectory);
    doReturn(new ProcessResult(0, new ProcessOutput("success".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(Collections.emptyMap(), expectedRemoveCommand, null, format("remove helm repo %s", repoName),
            9000L, HelmCliCommandType.REPO_REMOVE);

    assertThatCode(() -> helmTaskHelperBase.removeRepo(repoName, workingDirectory, V2, 9000L))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveRepoFailedWithoutAnyExceptions() {
    final String workingDirectory = "/working/directory";
    final String repoName = "repoName";
    final String expectedRemoveCommand = format("v2/helm repo remove %s --home %s/helm", repoName, workingDirectory);
    doReturn(new ProcessResult(1, new ProcessOutput("something went wrong executing command".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(Collections.emptyMap(), expectedRemoveCommand, null, format("remove helm repo %s", repoName),
            9000L, HelmCliCommandType.REPO_REMOVE);

    assertThatCode(() -> helmTaskHelperBase.removeRepo(repoName, workingDirectory, V2, 9000L))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRemoveRepoFailedWithException() {
    final String workingDirectory = "/working/directory";
    final String repoName = "repoName";
    final String expectedRemoveCommand = format("v2/helm repo remove %s --home %s/helm", repoName, workingDirectory);
    doThrow(new InvalidRequestException("Something went wrong", USER))
        .when(helmTaskHelperBase)
        .executeCommand(Collections.emptyMap(), expectedRemoveCommand, null, format("remove helm repo %s", repoName),
            9000L, HelmCliCommandType.REPO_REMOVE);

    assertThatCode(() -> helmTaskHelperBase.removeRepo(repoName, workingDirectory, V2, 9000L))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrintHelmChartInfoInExecutionLogsHttp() {
    final String repoDisplayName = "HTTP Repo";
    final String chartRepoUrl = "https://helm.repo/index";
    final String chartName = "todolist";
    final String chartVersion = "1.0.1";
    HttpHelmStoreDelegateConfig helmStoreDelegateConfig =
        HttpHelmStoreDelegateConfig.builder()
            .httpHelmConnector(HttpHelmConnectorDTO.builder().helmRepoUrl(chartRepoUrl).build())
            .repoDisplayName(repoDisplayName)
            .build();
    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig = HelmChartManifestDelegateConfig.builder()
                                                                          .chartName(chartName)
                                                                          .chartVersion(chartVersion)
                                                                          .storeDelegateConfig(helmStoreDelegateConfig)
                                                                          .helmVersion(V3)
                                                                          .build();

    helmTaskHelperBase.printHelmChartInfoInExecutionLogs(helmChartManifestDelegateConfig, logCallback);

    verify(logCallback).saveExecutionLog("Helm repository: " + repoDisplayName);
    verify(logCallback).saveExecutionLog("Chart name: " + chartName);
    verify(logCallback).saveExecutionLog("Chart version: " + chartVersion);
    verify(logCallback).saveExecutionLog("Helm version: " + V3);
    verify(logCallback).saveExecutionLog("Repo url: " + chartRepoUrl);
    verifyNoMoreInteractions(logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrintHelmChartInfoInExecutionLogsS3() {
    final String repoDisplayName = "HTTP Repo";
    final String region = "us-east-1";
    final String bucketName = "helm-charts";
    final String chartName = "todolist";
    final String chartVersion = "1.0.1";

    S3HelmStoreDelegateConfig helmStoreDelegateConfig = S3HelmStoreDelegateConfig.builder()
                                                            .repoDisplayName(repoDisplayName)
                                                            .region(region)
                                                            .bucketName(bucketName)
                                                            .build();

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig = HelmChartManifestDelegateConfig.builder()
                                                                          .chartName(chartName)
                                                                          .chartVersion(chartVersion)
                                                                          .storeDelegateConfig(helmStoreDelegateConfig)
                                                                          .helmVersion(V3)
                                                                          .build();

    helmTaskHelperBase.printHelmChartInfoInExecutionLogs(helmChartManifestDelegateConfig, logCallback);

    verify(logCallback).saveExecutionLog("Helm repository: " + repoDisplayName);
    verify(logCallback).saveExecutionLog("Chart name: " + chartName);
    verify(logCallback).saveExecutionLog("Chart version: " + chartVersion);
    verify(logCallback).saveExecutionLog("Helm version: " + V3);
    verify(logCallback).saveExecutionLog("Chart bucket: " + bucketName);
    verify(logCallback).saveExecutionLog("Region: " + region);
    verifyNoMoreInteractions(logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrintHelmChartInfoInExecutionLogsGcs() {
    final String repoDisplayName = "HTTP Repo";
    final String bucketName = "helm-charts";
    final String basePath = "v3/";
    final String chartName = "todolist";
    final String chartVersion = "1.0.1";

    GcsHelmStoreDelegateConfig helmStoreDelegateConfig = GcsHelmStoreDelegateConfig.builder()
                                                             .repoDisplayName(repoDisplayName)
                                                             .bucketName(bucketName)
                                                             .folderPath(basePath)
                                                             .build();

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig = HelmChartManifestDelegateConfig.builder()
                                                                          .chartName(chartName)
                                                                          .chartVersion(chartVersion)
                                                                          .storeDelegateConfig(helmStoreDelegateConfig)
                                                                          .helmVersion(V3)
                                                                          .build();

    helmTaskHelperBase.printHelmChartInfoInExecutionLogs(helmChartManifestDelegateConfig, logCallback);

    verify(logCallback).saveExecutionLog("Helm repository: " + repoDisplayName);
    verify(logCallback).saveExecutionLog("Base Path: " + basePath);
    verify(logCallback).saveExecutionLog("Chart name: " + chartName);
    verify(logCallback).saveExecutionLog("Chart version: " + chartVersion);
    verify(logCallback).saveExecutionLog("Helm version: " + V3);
    verify(logCallback).saveExecutionLog("Chart bucket: " + bucketName);
    verifyNoMoreInteractions(logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateProcessExecutor() {
    doCallRealMethod()
        .when(helmTaskHelperBase)
        .createProcessExecutor("helm repo add test", "pwd", 9000L, Collections.emptyMap());
    doCallRealMethod()
        .when(helmTaskHelperBase)
        .createProcessExecutor("helm repo add test", "pwd", 9000L, Collections.emptyMap());
    ProcessExecutor executor =
        helmTaskHelperBase.createProcessExecutor("helm repo add test", "pwd", 9000L, Collections.emptyMap());
    assertThat(executor.getCommand()).containsExactly("helm", "repo", "add", "test");
    assertThat(executor.getDirectory().toString()).endsWith("pwd");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFetchVersionsFromHttp() throws Exception {
    final String V_3_HELM_SEARCH_REPO_COMMAND = "v3/helm search repo repoName/chartName -l --devel --max-col-width 300";
    String repoUrl = "https://repo-chart/";
    String username = "username";
    char[] password = "password".toCharArray();
    String directory = "dir";
    long timeout = 90000L;

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        getHelmChartManifestDelegateConfig(repoUrl, username, password, V3, HTTP_HELM);
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();

    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .createDirectory(directory);
    doReturn(new ProcessResult(0, new ProcessOutput(getHelmCollectionResult().getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq(directory), anyString(), eq(timeout),
            any(HelmCliCommandType.class));

    // with username and pass
    List<String> chartVersions =
        helmTaskHelperBase.fetchChartVersions(helmChartManifestDelegateConfig, timeout, directory);
    assertThat(chartVersions.size()).isEqualTo(2);
    assertThat(chartVersions.get(0)).isEqualTo("1.0.2");
    assertThat(chartVersions.get(1)).isEqualTo("1.0.1");
    verify(processExecutor, times(2)).execute();

    // anonymous user
    List<String> chartVersions2 = helmTaskHelperBase.fetchChartVersions(
        getHelmChartManifestDelegateConfig(repoUrl, null, null, V3, HTTP_HELM), timeout, directory);
    assertThat(chartVersions2.size()).isEqualTo(2);
    assertThat(chartVersions2.get(0)).isEqualTo("1.0.2");
    assertThat(chartVersions2.get(1)).isEqualTo("1.0.1");
    verify(processExecutor, times(4)).execute();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFetchVersionsFromHttpV2() throws Exception {
    final String V_2_HELM_SEARCH_REPO_COMMAND =
        "v2/helm search repoName/chartName -l ${HELM_HOME_PATH_FLAG} --col-width 300";
    String repoUrl = "https://repo-chart/";
    String username = "username";
    char[] password = "password".toCharArray();
    String directory = "dir";
    long timeout = 90000L;

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        getHelmChartManifestDelegateConfig(repoUrl, username, password, V2, HTTP_HELM);
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();

    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .createDirectory(directory);
    doReturn(new ProcessResult(0, new ProcessOutput(getHelmCollectionResult().getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), eq(V_2_HELM_SEARCH_REPO_COMMAND), eq(directory), anyString(), eq(timeout),
            any(HelmCliCommandType.class));
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .applyHelmHomePath(V_2_HELM_SEARCH_REPO_COMMAND, directory);

    // with username and pass
    List<String> chartVersions =
        helmTaskHelperBase.fetchChartVersions(helmChartManifestDelegateConfig, timeout, directory);
    assertThat(chartVersions.size()).isEqualTo(2);
    assertThat(chartVersions.get(0)).isEqualTo("1.0.2");
    assertThat(chartVersions.get(1)).isEqualTo("1.0.1");
    // For helm version 2, we execute another command for helm init apart from add and update repo
    verify(processExecutor, times(3)).execute();
    verify(helmTaskHelperBase, times(1)).initHelm(directory, V2, timeout);

    // anonymous user
    List<String> chartVersions2 = helmTaskHelperBase.fetchChartVersions(
        getHelmChartManifestDelegateConfig(repoUrl, null, null, V2, HTTP_HELM), timeout, directory);
    assertThat(chartVersions2.size()).isEqualTo(2);
    assertThat(chartVersions2.get(0)).isEqualTo("1.0.2");
    assertThat(chartVersions2.get(1)).isEqualTo("1.0.1");
    // For helm version 2, we execute another command for helm init apart from add and update repo
    verify(processExecutor, times(6)).execute();
    verify(helmTaskHelperBase, times(2)).initHelm(directory, V2, timeout);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFetchVersionsFromGcs() throws Exception {
    final String V_3_HELM_SEARCH_REPO_COMMAND = "v3/helm search repo repoName/chartName -l --devel --max-col-width 300";
    String repoUrl = "https://localhost:9999/";
    String directory = "dir";
    long timeout = 90000L;
    final ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(9999).build();

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        getHelmChartManifestDelegateConfig(repoUrl, null, null, V3, GCS_HELM);
    doReturn(chartMuseumServer)
        .when(ngChartMuseumService)
        .startChartMuseumServer(helmChartManifestDelegateConfig.getStoreDelegateConfig(), RESOURCE_DIR_BASE);
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .createNewDirectoryAtPath(RESOURCE_DIR_BASE);
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .createDirectory(directory);
    doReturn(new ProcessResult(0, new ProcessOutput(getHelmCollectionResult().getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq(directory), anyString(), eq(timeout),
            any(HelmCliCommandType.class));

    List<String> chartVersions =
        helmTaskHelperBase.fetchChartVersions(helmChartManifestDelegateConfig, timeout, directory);
    assertThat(chartVersions.size()).isEqualTo(2);
    assertThat(chartVersions.get(0)).isEqualTo("1.0.2");
    assertThat(chartVersions.get(1)).isEqualTo("1.0.1");
    verify(processExecutor, times(1)).execute();
    verify(ngChartMuseumService)
        .startChartMuseumServer(helmChartManifestDelegateConfig.getStoreDelegateConfig(), RESOURCE_DIR_BASE);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testReturnEmptyHelmChartsForEmptyResponse() throws Exception {
    final String V_3_HELM_SEARCH_REPO_COMMAND = "v3/helm search repo repoName/chartName -l --devel --max-col-width 300";
    String repoUrl = "https://repo-chart/";
    String directory = "dir";
    long timeout = 90000L;

    processExecutor.readOutput(true);
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .createDirectory(directory);
    doReturn(new ProcessResult(0, new ProcessOutput("".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq(directory), anyString(), eq(timeout),
            any(HelmCliCommandType.class));

    assertThatThrownBy(
        ()
            -> helmTaskHelperBase.fetchChartVersions(
                getHelmChartManifestDelegateConfig(repoUrl, null, null, V3, HTTP_HELM), timeout, directory))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No chart with the given name found. Chart might be deleted at source");
    verify(processExecutor, times(2)).execute();

    doReturn(new ProcessResult(0, new ProcessOutput("No results Found".getBytes())))
        .when(helmTaskHelperBase)
        .executeCommand(anyMap(), eq(V_3_HELM_SEARCH_REPO_COMMAND), eq(directory), anyString(), eq(timeout),
            any(HelmCliCommandType.class));
    assertThatThrownBy(
        ()
            -> helmTaskHelperBase.fetchChartVersions(
                getHelmChartManifestDelegateConfig(repoUrl, null, null, V3, HTTP_HELM), timeout, directory))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No chart with the given name found. Chart might be deleted at source");
    verify(processExecutor, times(4)).execute();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testFetchVersionsTimeOut() throws Exception {
    String repoUrl = "https://localhost:9999/";
    String directory = "dir";
    long timeout = 90000L;
    final ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(9999).build();

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        getHelmChartManifestDelegateConfig(repoUrl, null, null, V3, GCS_HELM);
    doReturn(chartMuseumServer)
        .when(ngChartMuseumService)
        .startChartMuseumServer(helmChartManifestDelegateConfig.getStoreDelegateConfig(), RESOURCE_DIR_BASE);
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
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .createNewDirectoryAtPath(RESOURCE_DIR_BASE);
    doAnswer(invocationOnMock -> invocationOnMock.getArgumentAt(0, String.class))
        .when(helmTaskHelperBase)
        .createDirectory(directory);

    assertThatThrownBy(() -> helmTaskHelperBase.fetchChartVersions(helmChartManifestDelegateConfig, timeout, directory))
        .isInstanceOf(HelmClientException.class)
        .hasMessageContaining("[Timed out] Helm chart fetch versions command failed ");
    verify(ngChartMuseumService)
        .startChartMuseumServer(helmChartManifestDelegateConfig.getStoreDelegateConfig(), RESOURCE_DIR_BASE);
    verify(ngChartMuseumService).stopChartMuseumServer(chartMuseumServer);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCleanupAfterCollection() throws Exception {
    String repoUrl = "https://localhost:9999/";
    String directory = "dir";
    long timeout = 90000L;
    final ChartMuseumServer chartMuseumServer = ChartMuseumServer.builder().port(9999).build();

    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        getHelmChartManifestDelegateConfig(repoUrl, null, null, V3, GCS_HELM);
    doNothing().when(ngChartMuseumService).stopChartMuseumServer(chartMuseumServer);
    doReturn(new ProcessResult(0, null)).when(processExecutor).execute();
    doNothing().when(helmTaskHelperBase).cleanup(directory);

    helmTaskHelperBase.cleanupAfterCollection(helmChartManifestDelegateConfig, directory, timeout);
    verify(helmTaskHelperBase).cleanup(directory);
    verify(processExecutor).execute();
  }

  private String getHelmCollectionResult() {
    return "NAME\tCHART VERSION\tAPP VERSION\tDESCRIPTION\n"
        + "repoName/chartName\t1.0.2\t0\tDeploys harness delegate\n"
        + "repoName/chartName\t1.0.1\t0\tDeploys harness delegate";
  }

  private HelmChartManifestDelegateConfig getHelmChartManifestDelegateConfig(String repoUrl, String username,
      char[] password, HelmVersion helmVersion, StoreDelegateConfigType storeDelegateConfigType) {
    return HelmChartManifestDelegateConfig.builder()
        .chartName("chartName")
        .helmVersion(helmVersion)
        .helmCommandFlag(emptyHelmCommandFlag)
        .storeDelegateConfig(getStoreDelegateConfig(repoUrl, username, password, storeDelegateConfigType))
        .build();
  }

  private StoreDelegateConfig getStoreDelegateConfig(
      String repoUrl, String username, char[] password, StoreDelegateConfigType storeDelegateConfigType) {
    switch (storeDelegateConfigType) {
      case HTTP_HELM:
        return getHttpHelmStoreConfig(repoUrl, username, password);
      case GCS_HELM:
        return getGcsHelmStoreConfig();
      default:
        return null;
    }
  }

  private StoreDelegateConfig getGcsHelmStoreConfig() {
    return GcsHelmStoreDelegateConfig.builder().repoName("repoName").repoDisplayName(REPO_DISPLAY_NAME).build();
  }

  private HttpHelmStoreDelegateConfig getHttpHelmStoreConfig(String repoUrl, String username, char[] password) {
    return HttpHelmStoreDelegateConfig.builder()
        .repoName("repoName")
        .repoDisplayName(REPO_DISPLAY_NAME)
        .httpHelmConnector(password == null
                ? HttpHelmConnectorDTO.builder()
                      .helmRepoUrl(repoUrl)
                      .auth(HttpHelmAuthenticationDTO.builder().authType(HttpHelmAuthType.ANONYMOUS).build())
                      .build()
                : HttpHelmConnectorDTO.builder()
                      .helmRepoUrl(repoUrl)
                      .auth(HttpHelmAuthenticationDTO.builder()
                                .authType(HttpHelmAuthType.USER_PASSWORD)
                                .credentials(HttpHelmUsernamePasswordDTO.builder()
                                                 .username(username)
                                                 .passwordRef(SecretRefData.builder().decryptedValue(password).build())
                                                 .build())
                                .build())
                      .build())
        .build();
  }
}