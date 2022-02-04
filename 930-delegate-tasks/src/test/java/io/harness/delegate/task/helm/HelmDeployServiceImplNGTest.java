/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.exception.HelmNGException;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig.HelmChartManifestDelegateConfigBuilder;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.encryption.SecretRefData;
import io.harness.exception.GeneralException;
import io.harness.exception.GitOperationException;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmClient;
import io.harness.helm.HelmClientImpl.HelmCliResponse;
import io.harness.helm.HelmCommandData;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import software.wings.helpers.ext.helm.response.ReleaseInfo;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class HelmDeployServiceImplNGTest extends CategoryTest {
  @Mock private HelmClient helmClient;
  @Mock private LogCallback logCallback;
  @Mock private KubernetesConfig kubernetesConfig;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private NGGitService ngGitService;
  @Mock private HelmTaskHelperBase helmTaskHelperBase;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private TimeLimiter mockTimeLimiter; // don't remove; used internally as part of testDeployInstall
  @Mock private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private ScmFetchFilesHelperNG scmFetchFilesHelperNG;
  @InjectMocks HelmDeployServiceImplNG helmDeployService;

  private HelmInstallCommandRequestNG helmInstallCommandRequestNG;

  private HelmRollbackCommandRequestNG helmRollbackCommandRequestNG;

  private HelmCliResponse helmCliReleaseHistoryResponse;
  private HelmCliResponse helmCliResponse;
  private HelmCliResponse helmCliListReleasesResponse;

  private TimeLimiter timeLimiter = new FakeTimeLimiter();

  private HttpHelmStoreDelegateConfig httpHelmStoreDelegateConfig =
      HttpHelmStoreDelegateConfig.builder()
          .httpHelmConnector(HttpHelmConnectorDTO.builder()
                                 .helmRepoUrl("www.my-helm-repo.com")
                                 .auth(HttpHelmAuthenticationDTO.builder()
                                           .authType(HttpHelmAuthType.USER_PASSWORD)
                                           .credentials(HttpHelmUsernamePasswordDTO.builder().build())
                                           .build())
                                 .build())
          .build();
  private GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
      GcsHelmStoreDelegateConfig.builder()
          .gcpConnector(GcpConnectorDTO.builder().credential(GcpConnectorCredentialDTO.builder().build()).build())
          .bucketName("helm-gcs")
          .folderPath("abc/xyz")
          .build();
  private S3HelmStoreDelegateConfig s3HelmStoreDelegateConfig =
      S3HelmStoreDelegateConfig.builder().bucketName("helm-bucket").region("us-east").folderPath("/tmp/bazel").build();

  private GitStoreDelegateConfig gitStoreDelegateConfig =
      GitStoreDelegateConfig.builder()
          .branch("master")
          .fetchType(FetchType.BRANCH)
          .gitConfigDTO(GitConfigDTO.builder().branchName("master").build())
          .build();

  private HelmChartManifestDelegateConfigBuilder helmChartManifestDelegateConfig =
      HelmChartManifestDelegateConfig.builder()
          .chartName(HelmTestConstants.CHART_NAME_KEY)
          .chartVersion("V3")
          .helmCommandFlag(HelmCommandFlag.builder().valueMap(new HashMap<>()).build())
          .storeDelegateConfig(httpHelmStoreDelegateConfig);

  HelmDeployServiceImplNG spyHelmDeployService;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    helmCliListReleasesResponse = getHelmCliResponse();
    helmCliReleaseHistoryResponse = getHelmCliResponse();
    helmCliResponse = getHelmCliResponse();
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    helmCliResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    helmCliListReleasesResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    logCallback = mock(LogCallback.class);
    doNothing().when(logCallback).saveExecutionLog(anyString());
    helmInstallCommandRequestNG = createHelmInstallCommandRequestNG();
    helmRollbackCommandRequestNG = createHelmRollbackCommandRequestNG();
    kubernetesConfig = KubernetesConfig.builder().build();
    when(containerDeploymentDelegateBaseHelper.createKubernetesConfig(any())).thenReturn(kubernetesConfig);
    spyHelmDeployService = spy(helmDeployService);
    doNothing()
        .when(helmTaskHelperBase)
        .printHelmChartInfoInExecutionLogs(eq(helmChartManifestDelegateConfig.build()), any());
    doNothing().when(helmTaskHelperBase).initHelm(anyString(), any(), anyLong());
    doNothing()
        .when(helmTaskHelperBase)
        .downloadChartFilesFromHttpRepo(eq(helmChartManifestDelegateConfig.build()), anyString(), anyLong());
    doReturn(logCallback).when(k8sTaskHelperBase).getLogCallback(any(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testFetchChartRepo() throws Exception {
    // test chart repo -- No exception
    doNothing()
        .when(helmTaskHelperBase)
        .downloadChartFilesFromHttpRepo(eq(helmChartManifestDelegateConfig.build()), anyString(), anyLong());
    doReturn("abc").when(spyHelmDeployService).getManifestFileNamesInLogFormat(anyString());

    spyHelmDeployService.prepareRepoAndCharts(
        helmInstallCommandRequestNG, helmInstallCommandRequestNG.getTimeoutInMillis());
    ArgumentCaptor<HelmChartManifestDelegateConfig> argumentCaptor =
        ArgumentCaptor.forClass(HelmChartManifestDelegateConfig.class);
    verify(helmTaskHelperBase)
        .downloadChartFilesFromHttpRepo(
            argumentCaptor.capture(), eq("tmp"), eq(helmInstallCommandRequestNG.getTimeoutInMillis()));

    // HelmClientRuntimeException
    HelmClientException helmClientException = new HelmClientException("failed", HelmCliCommandType.FETCH);
    doThrow(helmClientException)
        .when(helmTaskHelperBase)
        .downloadChartFilesFromHttpRepo(eq(helmChartManifestDelegateConfig.build()), anyString(), anyLong());

    assertThatThrownBy(()
                           -> spyHelmDeployService.prepareRepoAndCharts(
                               helmInstallCommandRequestNG, helmInstallCommandRequestNG.getTimeoutInMillis()))
        .isInstanceOf(HelmClientRuntimeException.class);

    // general exception -- throws HelmClientException
    Exception ex = new Exception();
    helmChartManifestDelegateConfig.storeDelegateConfig(gcsHelmStoreDelegateConfig);
    helmInstallCommandRequestNG.setManifestDelegateConfig(helmChartManifestDelegateConfig.build());
    doThrow(ex)
        .when(helmTaskHelperBase)
        .downloadChartFilesUsingChartMuseum(eq(helmChartManifestDelegateConfig.build()), anyString(), anyLong());

    assertThatThrownBy(()
                           -> spyHelmDeployService.prepareRepoAndCharts(
                               helmInstallCommandRequestNG, helmInstallCommandRequestNG.getTimeoutInMillis()))
        .extracting(exc -> ((HelmClientRuntimeException) exc).getHelmClientException().getMessage())
        .isEqualTo("Failed to download manifest files from GCS_HELM repo. ");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testFetchSourceRepo() throws IOException {
    // no exception
    doNothing().when(gitDecryptionHelper).decryptGitConfig(any(), eq(gitStoreDelegateConfig.getEncryptedDataDetails()));
    doReturn(new SshSessionConfig()).when(gitDecryptionHelper).getSSHSessionConfig(any(), any());
    doNothing()
        .when(ngGitService)
        .downloadFiles(
            eq(gitStoreDelegateConfig), anyString(), eq(helmInstallCommandRequestNG.getAccountId()), any(), any());
    when(spyHelmDeployService.getManifestFileNamesInLogFormat(anyString())).thenReturn("abc");

    helmChartManifestDelegateConfig.storeDelegateConfig(gitStoreDelegateConfig);
    helmInstallCommandRequestNG.setManifestDelegateConfig(helmChartManifestDelegateConfig.build());
    assertThatCode(()
                       -> spyHelmDeployService.prepareRepoAndCharts(
                           helmInstallCommandRequestNG, helmInstallCommandRequestNG.getTimeoutInMillis()));

    // throw exception
    when(spyHelmDeployService.getManifestFileNamesInLogFormat(anyString())).thenThrow(new IOException());

    assertThatThrownBy(()
                           -> spyHelmDeployService.prepareRepoAndCharts(
                               helmInstallCommandRequestNG, helmInstallCommandRequestNG.getTimeoutInMillis()))
        .isInstanceOf(GitOperationException.class);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testOptimizedFileFetch() throws IOException {
    gitStoreDelegateConfig = getGitStoreDelegateConfigForSCM();
    helmChartManifestDelegateConfig.storeDelegateConfig(gitStoreDelegateConfig);
    helmInstallCommandRequestNG.setManifestDelegateConfig(helmChartManifestDelegateConfig.build());
    doReturn(GithubUsernameTokenDTO.builder().build())
        .when(secretDecryptionService)
        .decrypt(any(), eq(gitStoreDelegateConfig.getApiAuthEncryptedDataDetails()));
    doReturn(new SshSessionConfig()).when(gitDecryptionHelper).getSSHSessionConfig(any(), any());
    doNothing()
        .when(scmFetchFilesHelperNG)
        .downloadFilesUsingScm(
            anyString(), eq(gitStoreDelegateConfig), eq(helmInstallCommandRequestNG.getLogCallback()));
    when(spyHelmDeployService.getManifestFileNamesInLogFormat(anyString())).thenReturn("abc");

    assertThatCode(()
                       -> spyHelmDeployService.prepareRepoAndCharts(
                           helmInstallCommandRequestNG, helmInstallCommandRequestNG.getTimeoutInMillis()));
  }

  private GitStoreDelegateConfig getGitStoreDelegateConfigForSCM() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .authentication(GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                          .username("username")
                                                                          .passwordRef(SecretRefData.builder().build())
                                                                          .build())
                                                 .build())
                                .build())
            .apiAccess(GithubApiAccessDTO.builder().spec(GithubTokenSpecDTO.builder().build()).build())
            .build();

    List<EncryptedDataDetail> encryptionDataDetails = new ArrayList<>();
    List<EncryptedDataDetail> apiAuthEncryptedDataDetails = new ArrayList<>();
    SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
    return GitStoreDelegateConfig.builder()
        .branch("master")
        .fetchType(FetchType.BRANCH)
        .connectorName("conenctor")
        .gitConfigDTO(githubConnectorDTO)
        .path("manifest")
        .encryptedDataDetails(encryptionDataDetails)
        .apiAuthEncryptedDataDetails(apiAuthEncryptedDataDetails)
        .sshKeySpecDTO(sshKeySpecDTO)
        .optimizedFilesFetch(true)
        .build();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetContainerInfos() throws Exception {
    setFakeTimeLimiter();
    // getKubectlContainerInfos -- steadyStateCheck pass
    doReturn("kubectl-path").when(k8sGlobalConfigService).getKubectlPath(anyBoolean());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(), anyList(), any(), eq(helmInstallCommandRequestNG.getNamespace()),
            eq(helmInstallCommandRequestNG.getLogCallback()), eq(false));
    ContainerInfo containerInfo = ContainerInfo.builder().build();
    doReturn(Collections.singletonList(containerInfo))
        .when(k8sTaskHelperBase)
        .getContainerInfos(eq(kubernetesConfig), eq(helmInstallCommandRequestNG.getReleaseName()),
            eq(helmInstallCommandRequestNG.getNamespace()), eq(helmInstallCommandRequestNG.getTimeoutInMillis()));

    assertThat(helmDeployService.getContainerInfos(helmInstallCommandRequestNG, Collections.emptyList(), true,
                   logCallback, helmInstallCommandRequestNG.getTimeoutInMillis()))
        .isNotNull();

    // getKubectlContainerInfos -- steadyStateCheck fail
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(), anyList(), any(), eq(helmInstallCommandRequestNG.getNamespace()),
            eq(helmInstallCommandRequestNG.getLogCallback()), eq(false));
    assertThatThrownBy(
        ()
            -> helmDeployService.getContainerInfos(helmInstallCommandRequestNG,
                Collections.singletonList(KubernetesResourceId.builder().namespace("default").name("abc").build()),
                true, logCallback, helmInstallCommandRequestNG.getTimeoutInMillis()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Steady state check failed");

    // getFabric8ContainerInfo
    doReturn(Collections.emptyList())
        .when(containerDeploymentDelegateBaseHelper)
        .getContainerInfosWhenReadyByLabels(eq(kubernetesConfig), eq(logCallback), anyMap(), anyList());

    assertThat(helmDeployService.getContainerInfos(helmInstallCommandRequestNG,
                   Collections.singletonList(KubernetesResourceId.builder().namespace("default").name("abc").build()),
                   false, logCallback, helmInstallCommandRequestNG.getTimeoutInMillis()))
        .isEmpty();
  }

  private void initForDeploy() throws Exception {
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(SUCCESS);
    when(helmClient.releaseHistory(any(), eq(true))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.install(any(), eq(true))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(true))).thenReturn(helmCliListReleasesResponse);

    when(spyHelmDeployService.getManifestFileNamesInLogFormat(anyString())).thenReturn("abc");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testDeployInstall() throws Exception {
    initForDeploy();
    doReturn(Collections.emptyList()).when(spyHelmDeployService).printHelmChartKubernetesResources(any());
    // K8SteadyStateCheckEnabled false
    ArgumentCaptor<HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmCommandResponseNG helmCommandResponseNG = spyHelmDeployService.deploy(helmInstallCommandRequestNG);
    assertThat(helmCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(helmClient).install(argumentCaptor.capture(), eq(true));

    // K8SteadyStateCheckEnabled true
    helmInstallCommandRequestNG.setK8SteadyStateCheckEnabled(true);
    doReturn("1.16").when(kubernetesContainerService).getVersionAsString(eq(kubernetesConfig));
    assertThat(spyHelmDeployService.deploy(helmInstallCommandRequestNG)).isEqualTo(helmCommandResponseNG);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testDeployInstallWingsException() throws Exception {
    initForDeploy();
    when(helmTaskHelperBase.getHelmChartInfoFromChartsYamlFile(any())).thenReturn(HelmChartInfo.builder().build());
    doReturn(Collections.emptyList()).when(spyHelmDeployService).printHelmChartKubernetesResources(any());
    GeneralException exception = new GeneralException("Something went wrong");
    when(helmClient.install(any(), eq(true))).thenThrow(exception);

    assertThatThrownBy(() -> spyHelmDeployService.deploy(helmInstallCommandRequestNG))
        .isInstanceOf(HelmNGException.class);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testDeployInstallException() throws Exception {
    initForDeploy();
    doReturn(Collections.emptyList()).when(spyHelmDeployService).printHelmChartKubernetesResources(any());
    InterruptedException e = new InterruptedException();
    when(helmClient.install(any(), eq(true))).thenThrow(e);

    assertThatThrownBy(() -> spyHelmDeployService.deploy(helmInstallCommandRequestNG))
        .isInstanceOf(HelmNGException.class);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testDeployTimeout() throws Exception {
    initForDeploy();
    helmChartManifestDelegateConfig.storeDelegateConfig(gitStoreDelegateConfig);
    helmInstallCommandRequestNG.setManifestDelegateConfig(helmChartManifestDelegateConfig.build());
    doReturn(Collections.emptyList()).when(spyHelmDeployService).printHelmChartKubernetesResources(any());
    TimeLimiter mockTimeLimiter = mock(TimeLimiter.class);
    on(spyHelmDeployService).set("timeLimiter", mockTimeLimiter);
    helmCliResponse.setOutput("Timed out");

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenThrow(new UncheckedTimeoutException("Timed out"));

    assertThatThrownBy(() -> spyHelmDeployService.deploy(helmInstallCommandRequestNG))
        .isInstanceOf(HelmNGException.class);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeployUpgrade() throws Exception {
    initForDeploy();
    doReturn(Collections.emptyList()).when(spyHelmDeployService).printHelmChartKubernetesResources(any());
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(SUCCESS);
    helmCliListReleasesResponse.setOutput(HelmTestConstants.LIST_RELEASE_V2);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(SUCCESS);

    when(helmClient.releaseHistory(any(), eq(true))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.upgrade(any(), eq(true))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(true))).thenReturn(helmCliListReleasesResponse);
    when(helmClient.renderChart(any(), anyString(), anyString(), anyList(), eq(true))).thenReturn(helmCliResponse);

    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    assertThatCode(() -> spyHelmDeployService.deploy(helmInstallCommandRequestNG)).doesNotThrowAnyException();
    verify(helmClient).upgrade(argumentCaptor.capture(), eq(true));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testPrintHelmChartForK8sResourcesOnDeploy() throws Exception {
    helmInstallCommandRequestNG.setNamespace("default");
    helmInstallCommandRequestNG.setValuesYamlList(Collections.emptyList());
    String renderedHelmChart = "apiVersion: apps/v1\n"
        + "kind: Deployment\n"
        + "metadata:\n"
        + "  name: example\n"
        + "  labels: []\n"
        + "spec:\n"
        + "  selector:\n"
        + "    app: test-app\n"
        + "    release: helm-release\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      name: example\n"
        + "      labels:\n"
        + "         app: test-app\n"
        + "         release: helm-release\n"
        + "---\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: example\n"
        + "type: Opaque\n"
        + "data:\n"
        + "  sample: c29tZXRpbWVzIHNjaWVuY2UgaXMgbW9yZSBhcnQgdGhhbiBzY2llbmNlLCBNb3J0eQ==\n";
    List<KubernetesResource> expectedLoggedResources = ManifestHelper.processYaml(renderedHelmChart);
    String expectedLoggedYaml = ManifestHelper.toYamlForLogs(expectedLoggedResources);
    HelmCliResponse renderedHelmChartResponse =
        HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(renderedHelmChart).build();

    initForDeploy();
    doReturn(renderedHelmChartResponse)
        .when(helmClient)
        .renderChart(
            any(HelmCommandData.class), eq("tmp/chart-name"), eq("default"), eq(Collections.emptyList()), eq(true));
    doReturn(renderedHelmChart)
        .when(delegateLocalConfigService)
        .replacePlaceholdersWithLocalConfig(eq(renderedHelmChart));
    doNothing()
        .when(k8sTaskHelperBase)
        .setNamespaceToKubernetesResourcesIfRequired(anyList(), eq(helmInstallCommandRequestNG.getNamespace()));

    spyHelmDeployService.deploy(helmInstallCommandRequestNG);
    ArgumentCaptor<String> savedLogsCaptor = ArgumentCaptor.forClass(String.class);
    verify(logCallback, atLeastOnce()).saveExecutionLog(savedLogsCaptor.capture());
    assertThat(savedLogsCaptor.getAllValues()).contains(expectedLoggedYaml);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testDeleteAndPurgeHelmReleaseName() throws Exception {
    HelmCliResponse helmCliResponse = HelmCliResponse.builder().build();
    doReturn(helmCliResponse)
        .when(helmClient)
        .deleteHelmRelease(HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true);

    helmDeployService.deleteAndPurgeHelmRelease(helmInstallCommandRequestNG, logCallback);

    verify(helmClient, times(1))
        .deleteHelmRelease(HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testDeleteAndPurgeHelmReleaseNameNotFailOnException() throws Exception {
    doThrow(new IOException("Unable to execute process"))
        .when(helmClient)
        .deleteHelmRelease(HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true);

    assertThatCode(() -> helmDeployService.deleteAndPurgeHelmRelease(helmInstallCommandRequestNG, logCallback))
        .doesNotThrowAnyException();
  }

  private void initForRollback() throws Exception {
    doReturn(helmCliResponse).when(helmClient).rollback(any(), eq(true));
  }

  private HelmCommandResponseNG executeRollbackWithReleaseHistory(ReleaseHistory releaseHistory, int version)
      throws Exception {
    List<ContainerInfo> containerInfosDefault1 =
        ImmutableList.of(ContainerInfo.builder().hostName("resource-1").build());

    when(helmClient.rollback(any(HelmCommandData.class), eq(true)))
        .thenReturn(
            HelmCliResponse.builder().output("Rollback was a success.").commandExecutionStatus(SUCCESS).build());
    when(k8sTaskHelperBase.getReleaseHistoryFromSecret(any(KubernetesConfig.class), eq("release")))
        .thenReturn(releaseHistory.getAsYaml());

    when(k8sTaskHelperBase.getContainerInfos(
             any(), eq("release"), eq("default"), eq(helmRollbackCommandRequestNG.getTimeoutInMillis())))
        .thenReturn(containerInfosDefault1);

    when(k8sTaskHelperBase.doStatusCheckForAllResources(any(Kubectl.class), anyListOf(KubernetesResourceId.class),
             any(), anyString(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(true);

    return helmDeployService.rollback(helmRollbackCommandRequestNG);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRollback() throws Exception {
    // K8SteadyStateCheckEnabled false
    setFakeTimeLimiter();
    initForRollback();
    ArgumentCaptor<HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmCommandResponseNG helmCommandResponseNG = helmDeployService.rollback(helmRollbackCommandRequestNG);
    assertThat(helmCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(helmClient).rollback(argumentCaptor.capture(), eq(true));

    // K8SteadyStateCheckEnabled true -- empty releaseHistory
    helmRollbackCommandRequestNG.setK8SteadyStateCheckEnabled(true);
    doReturn("1.16").when(kubernetesContainerService).getVersionAsString(eq(kubernetesConfig));
    doReturn("")
        .when(k8sTaskHelperBase)
        .getReleaseHistoryFromSecret(any(), eq(helmRollbackCommandRequestNG.getReleaseName()));
    doNothing()
        .when(k8sTaskHelperBase)
        .saveReleaseHistory(any(), eq(helmRollbackCommandRequestNG.getReleaseName()), anyString(), anyBoolean());

    assertThatThrownBy(() -> helmDeployService.rollback(helmRollbackCommandRequestNG))
        .isInstanceOf(GeneralException.class);

    // K8SteadyStateCheckEnabled true -- valid releaseHistory
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.createNewRelease(Collections.singletonList(
        KubernetesResourceId.builder().namespace("default").name("resource-1").kind(Kind.StatefulSet.name()).build()));
    releaseHistory.setReleaseNumber(2);
    releaseHistory.setReleaseStatus(Release.Status.Succeeded);
    doReturn("1.16").when(kubernetesContainerService).getVersionAsString(eq(kubernetesConfig));

    HelmInstallCmdResponseNG helmInstallCmdResponseNG =
        (HelmInstallCmdResponseNG) executeRollbackWithReleaseHistory(releaseHistory, 2);
    assertThat(helmInstallCmdResponseNG.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(helmInstallCmdResponseNG.getContainerInfoList().stream().map(ContainerInfo::getHostName))
        .containsExactlyInAnyOrder("resource-1");

    // K8SteadyStateCheckEnabled true -- failed release
    releaseHistory.setReleaseStatus(Release.Status.Failed);

    assertThatThrownBy(() -> executeRollbackWithReleaseHistory(releaseHistory, 2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid status for release with number 2. Expected 'Succeeded' status, actual status is 'Failed'");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRollbackTimeout() throws Exception {
    TimeLimiter mockTimeLimiter = mock(TimeLimiter.class);
    on(helmDeployService).set("timeLimiter", mockTimeLimiter);

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenThrow(new UncheckedTimeoutException("Timed out"));
    doReturn(HelmCliResponse.builder().output("Rollback was a success.").commandExecutionStatus(SUCCESS).build())
        .when(helmClient)
        .rollback(any(HelmCommandData.class), eq(true));

    assertThatThrownBy(() -> helmDeployService.rollback(helmRollbackCommandRequestNG))
        .isInstanceOf(UncheckedTimeoutException.class)
        .hasMessageContaining("Timed out");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRollbackException() throws Exception {
    IOException ioException = new IOException("Some I/O issue");
    doThrow(ioException).when(helmClient).rollback(any(HelmCommandData.class), eq(true));

    assertThatThrownBy(() -> helmDeployService.rollback(helmRollbackCommandRequestNG))
        .isInstanceOf(IOException.class)
        .hasMessage("Some I/O issue");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRollbackFailedCommand() throws Exception {
    HelmCliResponse failureResponse =
        HelmCliResponse.builder().output("Unable to rollback").commandExecutionStatus(FAILURE).build();

    doReturn(failureResponse).when(helmClient).rollback(any(HelmCommandData.class), eq(true));

    HelmInstallCmdResponseNG response =
        (HelmInstallCmdResponseNG) helmDeployService.rollback(helmRollbackCommandRequestNG);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getOutput()).isEqualTo("Unable to rollback");
  }

  private void successWhenHelm3PresentInClientTools() throws InterruptedException, IOException, TimeoutException {
    doReturn("/client-tools/helm").when(helmTaskHelperBase).getHelmPath(V3);

    HelmCommandResponseNG helmCommandResponse = helmDeployService.ensureHelm3Installed(helmInstallCommandRequestNG);

    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  private void failureWhenHelm3AbsentInClientTools() throws InterruptedException, IOException, TimeoutException {
    doReturn("").when(helmTaskHelperBase).getHelmPath(V3);

    HelmCommandResponseNG helmCommandResponse = helmDeployService.ensureHelm3Installed(helmInstallCommandRequestNG);

    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testEnsureHelm3Installed() throws InterruptedException, TimeoutException, IOException {
    successWhenHelm3PresentInClientTools();
    failureWhenHelm3AbsentInClientTools();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testEnsureHelmInstalled() {
    shouldCallEnsureHelm2InstalledWhenVersionV2();
    shouldCallEnsureHelm2InstalledWhenVersionNull();
  }

  private void shouldCallEnsureHelm2InstalledWhenVersionNull() {
    HelmInstallCommandRequestNG helmInstallCommandRequest = HelmInstallCommandRequestNG.builder().build();
    doReturn(null).when(spyHelmDeployService).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);

    spyHelmDeployService.ensureHelmInstalled(helmInstallCommandRequest);

    verify(spyHelmDeployService, times(1)).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);
  }

  private void shouldCallEnsureHelm2InstalledWhenVersionV2() {
    HelmInstallCommandRequestNG helmInstallCommandRequest =
        HelmInstallCommandRequestNG.builder().helmVersion(V2).build();
    doReturn(null).when(spyHelmDeployService).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);

    spyHelmDeployService.ensureHelmInstalled(helmInstallCommandRequest);

    verify(spyHelmDeployService, times(1)).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfInstalled() throws Exception {
    setFakeTimeLimiter();

    helmInstallCommandRequestNG.setHelmVersion(V2);

    when(helmClient.getClientAndServerVersion(
             HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.VERSION_V2).build());

    HelmCommandResponseNG response = helmDeployService.ensureHelmCliAndTillerInstalled(helmInstallCommandRequestNG);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getOutput()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfNotInstalled() throws Exception {
    setFakeTimeLimiter();

    when(helmClient.getClientAndServerVersion(
             HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), false))
        .thenReturn(HelmCliResponse.builder().commandExecutionStatus(FAILURE).build());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> helmDeployService.ensureHelmCliAndTillerInstalled(helmInstallCommandRequestNG));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfV3Installed() throws Exception {
    setFakeTimeLimiter();

    when(k8sGlobalConfigService.getHelmPath(any())).thenReturn("helm/v3");
    when(helmClient.getClientAndServerVersion(
             HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.VERSION_V3).build());

    assertThatThrownBy(() -> helmDeployService.ensureHelmCliAndTillerInstalled(helmInstallCommandRequestNG))
        .getCause()
        .isInstanceOf(HintException.class)
        .hasMessageContaining("Change the version to V3 from V2");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfClusterUnreachable() throws Exception {
    setFakeTimeLimiter();

    when(helmClient.getClientAndServerVersion(
             HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true))
        .thenThrow(new UncheckedTimeoutException());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> helmDeployService.ensureHelmCliAndTillerInstalled(helmInstallCommandRequestNG));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testReleaseHistory() throws Exception {
    shouldListReleaseHistoryV2();
    shouldListReleaseHistoryV3();
    shouldNotThrowExceptionInReleaseHist();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetRepoUrlForHelmRepoConfig() {
    // S3
    String str = helmDeployService.getRepoUrlForHelmRepoConfig(
        helmChartManifestDelegateConfig.storeDelegateConfig(s3HelmStoreDelegateConfig).build());
    assertThat(str).contains("helm-bucket");

    // GCS
    str = helmDeployService.getRepoUrlForHelmRepoConfig(
        helmChartManifestDelegateConfig.storeDelegateConfig(gcsHelmStoreDelegateConfig).build());
    assertThat(str).contains("abc/xyz");
  }

  private void shouldListReleaseHistoryV2() throws Exception {
    HelmReleaseHistoryCommandRequestNG request = HelmReleaseHistoryCommandRequestNG.builder()
                                                     .manifestDelegateConfig(helmChartManifestDelegateConfig.build())
                                                     .build();
    request.setHelmVersion(V2);

    when(helmClient.releaseHistory(HelmCommandDataMapperNG.getHelmCmdDataNG(request), true))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.RELEASE_HIST_V2)
                        .build());

    HelmReleaseHistoryCmdResponseNG response = helmDeployService.releaseHistory(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(5);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision))
        .hasSameElementsAs(asList("1", "2", "3", "4", "5"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getChart))
        .hasSameElementsAs(asList(
            "chartmuseum-2.3.1", "chartmuseum-2.3.2", "chartmuseum-2.3.3", "chartmuseum-2.3.4", "chartmuseum-2.3.5"));
  }

  private void shouldListReleaseHistoryV3() throws Exception {
    HelmReleaseHistoryCommandRequestNG request = HelmReleaseHistoryCommandRequestNG.builder()
                                                     .manifestDelegateConfig(helmChartManifestDelegateConfig.build())
                                                     .build();
    request.setHelmVersion(V3);

    when(helmClient.releaseHistory(HelmCommandDataMapperNG.getHelmCmdDataNG(request), true))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.RELEASE_HIST_V3)
                        .build());

    HelmReleaseHistoryCmdResponseNG response = helmDeployService.releaseHistory(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(4);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision))
        .hasSameElementsAs(asList("1", "2", "3", "4"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getChart))
        .hasSameElementsAs(asList("zetcd-0.1.4", "zetcd-0.1.9", "zetcd-0.2.9", "chartmuseum-2.7.0"));
  }

  private void shouldNotThrowExceptionInReleaseHist() throws Exception {
    HelmReleaseHistoryCommandRequestNG request = HelmReleaseHistoryCommandRequestNG.builder()
                                                     .manifestDelegateConfig(helmChartManifestDelegateConfig.build())
                                                     .build();

    when(helmClient.releaseHistory(HelmCommandDataMapperNG.getHelmCmdDataNG(request), true))
        .thenThrow(new InterruptedException());

    HelmReleaseHistoryCmdResponseNG response = helmDeployService.releaseHistory(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getReleaseInfoList()).isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testListRelease() throws Exception {
    shouldListReleaseV2();
    shouldListReleaseV3();
    shouldNotThrowExceptionInListRelease();
  }

  private void shouldListReleaseV2() throws Exception {
    helmInstallCommandRequestNG.setHelmVersion(V2);

    when(helmClient.listReleases(HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.LIST_RELEASE_V2)
                        .build());

    HelmListReleaseResponseNG response = helmDeployService.listReleases(helmInstallCommandRequestNG);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(1);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getName))
        .hasSameElementsAs(asList("helm-release-name"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision)).hasSameElementsAs(asList("85"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getNamespace))
        .hasSameElementsAs(asList("default"));
  }

  private void shouldListReleaseV3() throws Exception {
    when(helmClient.listReleases(HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.LIST_RELEASE_V3)
                        .build());

    HelmListReleaseResponseNG response = helmDeployService.listReleases(helmInstallCommandRequestNG);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(3);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getName))
        .hasSameElementsAs(asList("ft-test", "ft-test1", "helm2-http"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision))
        .hasSameElementsAs(asList("1", "1", "6"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getNamespace))
        .hasSameElementsAs(asList("default", "harness", "default"));
  }

  private void shouldNotThrowExceptionInListRelease() throws Exception {
    when(helmClient.listReleases(HelmCommandDataMapperNG.getHelmCmdDataNG(helmInstallCommandRequestNG), true))
        .thenThrow(new InterruptedException());

    HelmListReleaseResponseNG response = helmDeployService.listReleases(helmInstallCommandRequestNG);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getReleaseInfoList()).isNull();
  }

  private void setFakeTimeLimiter() {
    on(helmDeployService).set("timeLimiter", timeLimiter);
  }

  private HelmCliResponse getHelmCliResponse() {
    return HelmCliResponse.builder().output("Some sensible output").build();
  }

  private HelmInstallCommandRequestNG createHelmInstallCommandRequestNG() {
    HelmInstallCommandRequestNG request = HelmInstallCommandRequestNG.builder()
                                              .releaseName(HelmTestConstants.HELM_RELEASE_NAME_KEY)
                                              .kubeConfigLocation(HelmTestConstants.HELM_KUBE_CONFIG_LOCATION_KEY)
                                              .logCallback(logCallback)
                                              .manifestDelegateConfig(helmChartManifestDelegateConfig.build())
                                              .workingDir("tmp")
                                              .namespace("default")
                                              .accountId("harnessCDP123")
                                              .helmVersion(V3)
                                              .k8SteadyStateCheckEnabled(false)
                                              .build();

    return request;
  }

  private HelmRollbackCommandRequestNG createHelmRollbackCommandRequestNG() {
    HelmRollbackCommandRequestNG request = HelmRollbackCommandRequestNG.builder()
                                               .releaseName(HelmTestConstants.HELM_RELEASE_NAME_KEY)
                                               .kubeConfigLocation(HelmTestConstants.HELM_KUBE_CONFIG_LOCATION_KEY)
                                               .logCallback(logCallback)
                                               .manifestDelegateConfig(helmChartManifestDelegateConfig.build())
                                               .workingDir("tmp")
                                               .namespace("default")
                                               .accountId("harnessCDP123")
                                               .k8SteadyStateCheckEnabled(false)
                                               .prevReleaseVersion(2)
                                               .newReleaseVersion(3)
                                               .releaseName("release")
                                               .build();

    return request;
  }
}
