/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.helm.HelmConstants.DEFAULT_HELM_COMMAND_TIMEOUT;
import static io.harness.helm.HelmConstants.DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS;
import static io.harness.helm.HelmConstants.HELM_COMMAND_FLAG_PLACEHOLDER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;

import static com.google.common.base.Charsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 * Created by anubhaw on 3/22/18.
 */
@Singleton
@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(CDP)
public class HelmClientImpl implements HelmClient {
  @Inject private K8sGlobalConfigService k8sGlobalConfigService;

  private static final String OVERRIDE_FILE_PATH = "./repository/helm/overrides/${CONTENT_HASH}.yaml";
  public static final String DEBUG_COMMAND_FLAG = "--debug";
  public static final LoadingCache<String, Object> lockObjects =
      CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build(CacheLoader.from(Object::new));

  @Override
  public HelmCliResponse install(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled) throws Exception {
    String keyValueOverrides = constructValueOverrideFile(helmCommandData.getYamlFiles());
    String chartReference = getChartReference(helmCommandData.isRepoConfigNull(), helmCommandData.getChartName(),
        helmCommandData.getChartVersion(), helmCommandData.getChartUrl(), helmCommandData.getWorkingDir());
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.INSTALL;

    String installCommand = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                .replace("${OVERRIDE_VALUES}", keyValueOverrides)
                                .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                                .replace("${NAMESPACE}", getNamespaceFlag(helmCommandData.getNamespace()))
                                .replace("${CHART_REFERENCE}", chartReference);

    installCommand = applyCommandFlags(installCommand, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(installCommand, helmCommandData.getLogCallback());
    installCommand = applyKubeConfigToCommand(installCommand, kubeConfigLocation);

    if (!isErrorFrameworkEnabled) {
      return executeHelmCLICommand(installCommand);
    }

    String errorMessagePrefix = "Failed to install helm chart. Executed command: ";
    return fetchCliResponseWithExceptionHandling(
        installCommand, commandType, errorMessagePrefix, null, DEFAULT_HELM_COMMAND_TIMEOUT);
  }

  @Override
  public HelmCliResponse upgrade(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled) throws Exception {
    String keyValueOverrides = constructValueOverrideFile(helmCommandData.getYamlFiles());
    String chartReference = getChartReference(helmCommandData.isRepoConfigNull(), helmCommandData.getChartName(),
        helmCommandData.getChartVersion(), helmCommandData.getChartUrl(), helmCommandData.getWorkingDir());
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();

    HelmCliCommandType commandType = HelmCliCommandType.UPGRADE;
    String upgradeCommand = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                                .replace("${CHART_REFERENCE}", chartReference)
                                .replace("${OVERRIDE_VALUES}", keyValueOverrides);

    upgradeCommand = applyCommandFlags(upgradeCommand, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(upgradeCommand, helmCommandData.getLogCallback());
    upgradeCommand = applyKubeConfigToCommand(upgradeCommand, kubeConfigLocation);

    if (!isErrorFrameworkEnabled) {
      return executeHelmCLICommand(upgradeCommand);
    }

    String errorMessagePrefix = "Failed to upgrade helm chart. Executed command: ";
    return fetchCliResponseWithExceptionHandling(
        upgradeCommand, commandType, errorMessagePrefix, null, DEFAULT_HELM_COMMAND_TIMEOUT);
  }

  @Override
  public HelmCliResponse rollback(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled) throws Exception {
    String kubeConfigLocation = Optional.ofNullable(helmCommandData.getKubeConfigLocation()).orElse("");
    HelmCliCommandType commandType = HelmCliCommandType.ROLLBACK;

    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${RELEASE}", helmCommandData.getReleaseName())
                         .replace("${REVISION}", helmCommandData.getPrevReleaseVersion().toString());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    if (!isErrorFrameworkEnabled) {
      return executeHelmCLICommand(command);
    }

    String errorMessagePrefix = "Failed to rollback helm chart. Executed command: ";
    return fetchCliResponseWithExceptionHandling(
        command, commandType, errorMessagePrefix, null, DEFAULT_HELM_COMMAND_TIMEOUT);
  }

  @Override
  public HelmCliResponse releaseHistory(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    String releaseName = helmCommandData.getReleaseName();
    HelmCliCommandType commandType = HelmCliCommandType.RELEASE_HISTORY;

    String releaseHistory = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                .replace("${FLAGS}", "--max 5")
                                .replace("${RELEASE_NAME}", releaseName);

    releaseHistory = applyCommandFlags(releaseHistory, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());

    if (HelmVersion.V2.equals(helmCommandData.getHelmVersion())) {
      releaseHistory = StringUtils.replaceIgnoreCase(releaseHistory, DEBUG_COMMAND_FLAG, EMPTY);
    }

    logHelmCommandInExecutionLogs(releaseHistory, helmCommandData.getLogCallback());
    releaseHistory = applyKubeConfigToCommand(releaseHistory, kubeConfigLocation);

    if (!isErrorFrameworkEnabled) {
      return executeHelmCLICommand(releaseHistory);
    }

    String errorMessagePrefix = "Failed in release history step. Executed command: ";
    return fetchCliResponseWithExceptionHandling(
        releaseHistory, commandType, errorMessagePrefix, null, DEFAULT_HELM_COMMAND_TIMEOUT);
  }

  @Override
  public HelmCliResponse listReleases(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.LIST_RELEASE;

    String listRelease = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                             .replace("${RELEASE_NAME}", helmCommandData.getReleaseName());

    listRelease = applyCommandFlags(listRelease, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());

    if (HelmVersion.V2.equals(helmCommandData.getHelmVersion())) {
      listRelease = StringUtils.replaceIgnoreCase(listRelease, DEBUG_COMMAND_FLAG, EMPTY);
    }

    logHelmCommandInExecutionLogs(listRelease, helmCommandData.getLogCallback());
    listRelease = applyKubeConfigToCommand(listRelease, kubeConfigLocation);
    String errorMessagePrefix = "Failed in list releases step. Executed command: ";

    try (LogOutputStream errorStream = helmCommandData.getLogCallback() != null
            ? new ErrorActivityOutputStream(helmCommandData.getLogCallback())
            : new LogErrorStream()) {
      return isErrorFrameworkEnabled ? fetchCliResponseWithExceptionHandling(
                 listRelease, commandType, errorMessagePrefix, errorStream, DEFAULT_HELM_COMMAND_TIMEOUT)
                                     : executeHelmCLICommand(listRelease, errorStream);
    }
  }

  @Override
  public HelmCliResponse getClientAndServerVersion(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.VERSION;
    String command = getHelmCommandTemplateWithHelmPath(commandType, null);

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    if (!isErrorFrameworkEnabled) {
      return executeHelmCLICommand(command, DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS);
    }

    String errorMessagePrefix = "Failed in helm version command. ";
    return fetchCliResponseWithExceptionHandling(
        command, commandType, errorMessagePrefix, null, DEFAULT_TILLER_CONNECTION_TIMEOUT_MILLIS);
  }

  @Override
  public HelmCliResponse addPublicRepo(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.REPO_ADD;

    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${REPO_URL}", helmCommandData.getChartUrl())
                         .replace("${REPO_NAME}", helmCommandData.getRepoName());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    if (!isErrorFrameworkEnabled) {
      return executeHelmCLICommand(command);
    }

    String errorMessagePrefix = "Failed to add helm repo. ";
    return fetchCliResponseWithExceptionHandling(
        command, commandType, errorMessagePrefix, null, DEFAULT_HELM_COMMAND_TIMEOUT);
  }

  @Override
  public HelmCliResponse repoUpdate(HelmCommandData helmCommandData)
      throws InterruptedException, TimeoutException, IOException {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.REPO_UPDATE;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    return executeHelmCLICommand(command);
  }

  @Override
  public HelmCliResponse getHelmRepoList(HelmCommandData helmCommandData)
      throws InterruptedException, TimeoutException, IOException {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.REPO_LIST;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion());

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    return executeHelmCLICommand(command);
  }

  @Override
  public HelmCliResponse deleteHelmRelease(HelmCommandData helmCommandData, boolean isErrorFrameworkEnabled)
      throws Exception {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    /*
    In Helm3, release ledger is purged by default unlike helm2
     */
    HelmCliCommandType commandType = HelmCliCommandType.DELETE_RELEASE;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                         .replace("${FLAGS}", "--purge");

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);

    if (!isErrorFrameworkEnabled) {
      return executeHelmCLICommand(command);
    }

    String errorMessagePrefix = "Failed to delete helm release. ";
    return fetchCliResponseWithExceptionHandling(
        command, commandType, errorMessagePrefix, null, DEFAULT_HELM_COMMAND_TIMEOUT);
  }

  /*
  TODO: YOGESH check why this function is not called and do we need a version here.
   */
  @Override
  public HelmCliResponse templateForK8sV2(String releaseName, String namespace, String chartLocation,
      List<String> valuesOverrides) throws InterruptedException, TimeoutException, IOException, ExecutionException {
    String keyValueOverrides = constructValueOverrideFile(valuesOverrides);

    String templateCommand = HelmConstants.V2Commands.HELM_TEMPLATE_COMMAND_FOR_KUBERNETES_TEMPLATE
                                 .replace("${CHART_LOCATION}", chartLocation)
                                 .replace("${OVERRIDE_VALUES}", keyValueOverrides)
                                 .replace("${RELEASE_NAME}", releaseName)
                                 .replace("${NAMESPACE}", getNamespaceFlag(namespace));

    return executeHelmCLICommand(templateCommand);
  }

  @Override
  public HelmCliResponse renderChart(HelmCommandData helmCommandData, String chartLocation, String namespace,
      List<String> valuesOverrides, boolean isErrorFrameworkEnabled) throws Exception {
    String kubeConfigLocation = Optional.ofNullable(helmCommandData.getKubeConfigLocation()).orElse("");
    String keyValueOverrides = constructValueOverrideFile(valuesOverrides);

    HelmCliCommandType commandType = HelmCliCommandType.RENDER_CHART;
    String command = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                         .replace("${CHART_LOCATION}", chartLocation)
                         .replace("${RELEASE_NAME}", helmCommandData.getReleaseName())
                         .replace("${NAMESPACE}", namespace)
                         .replace("${OVERRIDE_VALUES}", keyValueOverrides);

    command = applyCommandFlags(command, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(command, helmCommandData.getLogCallback());
    command = applyKubeConfigToCommand(command, kubeConfigLocation);
    String errorMessagePrefix = "Failed to render helm chart. ";

    try (LogOutputStream errorStream = helmCommandData.getLogCallback() != null
            ? new ErrorActivityOutputStream(helmCommandData.getLogCallback())
            : new LogErrorStream()) {
      return isErrorFrameworkEnabled ? fetchCliResponseWithExceptionHandling(
                 command, commandType, errorMessagePrefix, errorStream, DEFAULT_HELM_COMMAND_TIMEOUT)
                                     : executeHelmCLICommand(command, errorStream);
    }
  }

  /**
   * The type Helm cli response.
   */
  @Data
  @Builder
  public static class HelmCliResponse {
    private CommandExecutionStatus commandExecutionStatus;
    private String output;
  }

  @Override
  public HelmCliResponse searchChart(HelmCommandData helmCommandData, String chartInfo)
      throws InterruptedException, TimeoutException, IOException {
    String kubeConfigLocation = helmCommandData.getKubeConfigLocation();
    HelmCliCommandType commandType = HelmCliCommandType.SEARCH_REPO;
    String searchChartCommand = getHelmCommandTemplateWithHelmPath(commandType, helmCommandData.getHelmVersion())
                                    .replace("${CHART_INFO}", chartInfo);

    searchChartCommand = applyCommandFlags(searchChartCommand, commandType, helmCommandData.getCommandFlags(),
        helmCommandData.isHelmCmdFlagsNull(), helmCommandData.getValueMap(), helmCommandData.getHelmVersion());
    logHelmCommandInExecutionLogs(searchChartCommand, helmCommandData.getLogCallback());
    searchChartCommand = applyKubeConfigToCommand(searchChartCommand, kubeConfigLocation);

    return executeHelmCLICommand(searchChartCommand);
  }

  @VisibleForTesting
  HelmCliResponse executeHelmCLICommand(String command, OutputStream errorStream)
      throws IOException, InterruptedException, TimeoutException {
    return executeHelmCLICommand(command, DEFAULT_HELM_COMMAND_TIMEOUT, errorStream);
  }

  @VisibleForTesting
  HelmCliResponse executeHelmCLICommand(String command) throws IOException, InterruptedException, TimeoutException {
    return executeHelmCLICommand(command, DEFAULT_HELM_COMMAND_TIMEOUT, null);
  }

  @VisibleForTesting
  HelmCliResponse executeHelmCLICommand(String command, long timeoutInMillis)
      throws IOException, InterruptedException, TimeoutException {
    return executeHelmCLICommand(command, timeoutInMillis, null);
  }

  @VisibleForTesting
  HelmCliResponse executeHelmCLICommand(String command, long timeoutInMillis, OutputStream errorStream)
      throws IOException, InterruptedException, TimeoutException {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
                                          .command("/bin/sh", "-c", command)
                                          .readOutput(true)
                                          .redirectOutput(new LogOutputStream() {
                                            @Override
                                            protected void processLine(String line) {
                                              log.info(line);
                                            }
                                          });

    if (errorStream != null) {
      processExecutor.redirectError(errorStream);
    }

    ProcessResult processResult = processExecutor.execute();
    CommandExecutionStatus status = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;

    String streamOutput = "";
    if (errorStream != null) {
      if (errorStream instanceof LogErrorStream) {
        streamOutput = ((LogErrorStream) errorStream).getOutput();
      }

      if (errorStream instanceof ErrorActivityOutputStream) {
        streamOutput = ((ErrorActivityOutputStream) errorStream).getOutput();
      }
    }

    String output = processResult.outputUTF8();

    return HelmCliResponse.builder()
        .commandExecutionStatus(status)
        .output(isNotEmpty(streamOutput) ? streamOutput.concat(output) : output)
        .build();
  }

  private String getNamespaceFlag(String namespace) {
    return "--namespace " + namespace;
  }

  private String getChartReference(String chartName, String chartVersion, String chartUrl) {
    if (isNotEmpty(chartUrl)) {
      chartName = chartName + " --repo " + chartUrl;
    }

    if (isNotEmpty(chartVersion)) {
      chartName = chartName + " --version " + chartVersion;
    }

    return chartName;
  }

  private String constructValueOverrideFile(List<String> valuesYamlFiles) throws IOException, ExecutionException {
    StringBuilder fileOverrides = new StringBuilder();
    if (isNotEmpty(valuesYamlFiles)) {
      for (String yamlFileContent : valuesYamlFiles) {
        String md5Hash = DigestUtils.md5Hex(yamlFileContent);
        String overrideFilePath = OVERRIDE_FILE_PATH.replace("${CONTENT_HASH}", md5Hash);

        synchronized (lockObjects.get(md5Hash)) {
          File overrideFile = new File(overrideFilePath);
          if (!overrideFile.exists()) {
            FileUtils.forceMkdir(overrideFile.getParentFile());
            FileUtils.writeStringToFile(overrideFile, yamlFileContent, UTF_8);
          }
          fileOverrides.append(" -f ").append(overrideFilePath);
        }
      }
    }
    return fileOverrides.toString();
  }

  private void logHelmCommandInExecutionLogs(String helmCommand, LogCallback logCallback) {
    if (logCallback != null) {
      String msg = "Executing command - " + helmCommand + "\n";
      log.info(msg);
      logCallback.saveExecutionLog(msg, LogLevel.INFO);
    }
  }

  private String applyKubeConfigToCommand(String command, String kubeConfigLocation) {
    if (isNotEmpty(kubeConfigLocation)) {
      return command.replace("${KUBECONFIG_PATH}", kubeConfigLocation);
    } else {
      return command.replace("KUBECONFIG=${KUBECONFIG_PATH}", EMPTY).trim();
    }
  }

  private String applyCommandFlags(String command, HelmCliCommandType commandType, String commandFlags,
      boolean isHelmCmdFlagsNull, Map<HelmSubCommandType, String> valueMap, HelmVersion helmVersion) {
    String flags = isBlank(commandFlags) ? "" : commandFlags;
    if (!isHelmCmdFlagsNull && isNotEmpty(valueMap)) {
      return HelmCommandFlagsUtils.applyHelmCommandFlags(command, commandType.name(), valueMap, helmVersion);
    }
    return command.replace(HELM_COMMAND_FLAG_PLACEHOLDER, flags);
  }

  private String getChartReference(
      boolean isRepoConfigNull, String chartName, String chartVersion, String chartUrl, String workingDir) {
    String chartReference;
    if (isRepoConfigNull) {
      chartReference = getChartReference(chartName, chartVersion, chartUrl);
    } else {
      chartReference = Paths.get(workingDir).toString();
    }
    return chartReference;
  }

  private String getHelmCommandTemplateWithHelmPath(HelmCliCommandType commandType, HelmVersion helmVersion) {
    String helmPath = getHelmPath(helmVersion);
    return HelmCommandTemplateFactory.getHelmCommandTemplate(commandType, helmVersion)
        .replace(HelmConstants.HELM_PATH_PLACEHOLDER, helmPath);
  }

  @Override
  public String getHelmPath(HelmVersion helmVersion) {
    return helmVersion == HelmVersion.V3 ? k8sGlobalConfigService.getHelmPath(HelmVersion.V3) : "helm";
  }

  public HelmCliResponse fetchCliResponseWithExceptionHandling(String command, HelmCliCommandType commandType,
      String errorMessagePrefix, OutputStream errorStream, long timeoutInMillis) {
    try {
      HelmCliResponse helmCliResponse = executeHelmCLICommand(command, timeoutInMillis, errorStream);

      if (helmCliResponse.getCommandExecutionStatus() != SUCCESS) {
        // if helm hist fails due to 'release not found' -- then we don't fail/ throw exception
        // (because for first time deployment, release history cmd fails with release not found)
        if (commandType == HelmCliCommandType.RELEASE_HISTORY
            && (helmCliResponse.getOutput().contains("not found") && helmCliResponse.getOutput().contains("release"))) {
          return helmCliResponse;
        }
        throw new HelmClientRuntimeException(new HelmClientException(
            errorMessagePrefix + command + ". " + helmCliResponse.getOutput(), USER, commandType));
      }
      return helmCliResponse;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HelmClientRuntimeException(new HelmClientException(ExceptionUtils.getMessage(e), USER, commandType));
    } catch (Exception e) {
      throw new HelmClientRuntimeException(new HelmClientException(ExceptionUtils.getMessage(e), USER, commandType));
    }
  }

  @RequiredArgsConstructor
  static class ErrorActivityOutputStream extends LogOutputStream {
    @NonNull private final LogCallback logCallback;
    @Getter private String output;
    @Override
    protected void processLine(String s) {
      output = s;
      logCallback.saveExecutionLog(s, ERROR);
    }
  }

  static class LogErrorStream extends LogOutputStream {
    @Getter private String output;
    @Override
    protected void processLine(String s) {
      output = s;
      log.error(s);
    }
  }
}