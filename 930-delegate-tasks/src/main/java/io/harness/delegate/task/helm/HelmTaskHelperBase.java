/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.CHART_MUSEUM_SERVER_URL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GCS_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.HTTP_HELM;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.S3_HELM;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.helm.HelmConstants.ADD_COMMAND_FOR_REPOSITORY;
import static io.harness.helm.HelmConstants.CHARTS_YAML_KEY;
import static io.harness.helm.HelmConstants.HELM_CACHE_HOME_PLACEHOLDER;
import static io.harness.helm.HelmConstants.HELM_HOME_PATH_FLAG;
import static io.harness.helm.HelmConstants.HELM_PATH_PLACEHOLDER;
import static io.harness.helm.HelmConstants.PASSWORD;
import static io.harness.helm.HelmConstants.REPO_NAME;
import static io.harness.helm.HelmConstants.REPO_URL;
import static io.harness.helm.HelmConstants.USERNAME;
import static io.harness.helm.HelmConstants.V3Commands.HELM_CACHE_HOME;
import static io.harness.helm.HelmConstants.V3Commands.HELM_CACHE_HOME_PATH;
import static io.harness.helm.HelmConstants.V3Commands.HELM_REPO_FLAGS;
import static io.harness.helm.HelmConstants.VALUES_YAML;
import static io.harness.helm.HelmConstants.WORKING_DIR_BASE;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.chartmuseum.ChartMuseumServer;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.chartmuseum.NGChartMuseumService;
import io.harness.delegate.exception.ManifestCollectionException;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HelmClientException;
import io.harness.exception.HelmClientRuntimeException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmCommandFlagsUtils;
import io.harness.helm.HelmCommandTemplateFactory;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.utils.FieldWithPlainTextOrSecretValueHelper;

import software.wings.delegatetasks.ExceptionMessageSanitizer;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class HelmTaskHelperBase {
  public static final String RESOURCE_DIR_BASE = "./repository/helm/resources/";
  public static final String VERSION_KEY = "version:";
  public static final String NAME_KEY = "name:";

  @Inject private K8sGlobalConfigService k8sGlobalConfigService;
  @Inject private NGChartMuseumService ngChartMuseumService;
  @Inject private SecretDecryptionService decryptionService;

  public void initHelm(String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) throws IOException {
    String helmHomePath = getHelmHomePath(workingDirectory);
    createNewDirectoryAtPath(helmHomePath);

    // Helm init command would be blank for helmV3
    String helmInitCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.INIT, helmVersion)
                                 .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion));
    if (isNotBlank(helmHomePath) && isNotBlank(helmInitCommand)) {
      helmInitCommand = applyHelmHomePath(helmInitCommand, workingDirectory);
      log.info("Initing helm. Command " + helmInitCommand);

      ProcessResult processResult = executeCommand(Collections.emptyMap(), helmInitCommand, workingDirectory,
          "Initing helm Command " + helmInitCommand, timeoutInMillis, HelmCliCommandType.INIT);
      if (processResult.getExitValue() != 0) {
        throw new HelmClientException(
            "Failed to init helm. Executed command " + helmInitCommand + ". " + processResult.getOutput().getUTF8(),
            USER, HelmCliCommandType.INIT);
      }
    }
  }

  public void addRepo(String repoName, String repoDisplayName, String chartRepoUrl, String username, char[] password,
      String chartDirectory, HelmVersion helmVersion, long timeoutInMillis, String tempDir) {
    Map<String, String> environment = new HashMap<>();
    String repoAddCommand =
        getHttpRepoAddCommand(repoName, chartRepoUrl, username, password, chartDirectory, helmVersion);
    String repoAddCommandForLogging =
        getHttpRepoAddCommandForLogging(repoName, chartRepoUrl, username, password, chartDirectory, helmVersion);
    if (!isEmpty(tempDir)) {
      environment.putIfAbsent(HELM_CACHE_HOME,
          HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, tempDir));
      repoAddCommand = addRepoFlags(repoAddCommand, repoName, tempDir);
      repoAddCommandForLogging = addRepoFlags(repoAddCommandForLogging, repoName, tempDir);
    }
    log.info(repoAddCommandForLogging);
    log.info(ADD_COMMAND_FOR_REPOSITORY + repoDisplayName);

    ProcessResult processResult = executeCommand(environment, repoAddCommand, chartDirectory,
        "add helm repo. Executed command" + repoAddCommandForLogging, timeoutInMillis, HelmCliCommandType.REPO_ADD);
    if (processResult.getExitValue() != 0) {
      throw new HelmClientException("Failed to add helm repo. Executed command " + repoAddCommandForLogging + ". "
              + processResult.getOutput().getUTF8(),
          USER, HelmCliCommandType.REPO_ADD);
    }
  }

  public void addRepo(String repoName, String repoDisplayName, String chartRepoUrl, String username, char[] password,
      String chartDirectory, HelmVersion helmVersion, long timeoutInMillis, boolean useRepoFlags) {
    if (!useRepoFlags) {
      addRepo(repoName, repoDisplayName, chartRepoUrl, username, password, chartDirectory, helmVersion, timeoutInMillis,
          EMPTY);
      return;
    }
    String dir = Paths.get(RESOURCE_DIR_BASE, repoName, "cache").toAbsolutePath().normalize().toString();
    addRepo(
        repoName, repoDisplayName, chartRepoUrl, username, password, chartDirectory, helmVersion, timeoutInMillis, dir);
    updateRepo(repoName, chartDirectory, helmVersion, timeoutInMillis, dir);
  }

  private String getHttpRepoAddCommand(String repoName, String chartRepoUrl, String username, char[] password,
      String workingDirectory, HelmVersion helmVersion) {
    String addRepoCommand =
        getHttpRepoAddCommandWithoutPassword(repoName, chartRepoUrl, username, workingDirectory, helmVersion);

    return addRepoCommand.replace(PASSWORD, getPassword(password));
  }

  private String getHttpRepoAddCommandForLogging(String repoName, String chartRepoUrl, String username, char[] password,
      String workingDirectory, HelmVersion helmVersion) {
    String repoAddCommand =
        getHttpRepoAddCommandWithoutPassword(repoName, chartRepoUrl, username, workingDirectory, helmVersion);
    String evaluatedPassword = isEmpty(getPassword(password)) ? StringUtils.EMPTY : "--password *******";

    return repoAddCommand.replace(PASSWORD, evaluatedPassword);
  }

  private String getHttpRepoAddCommandWithoutPassword(
      String repoName, String chartRepoUrl, String username, String workingDirectory, HelmVersion helmVersion) {
    String command = HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_HTTP, helmVersion)
                         .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
                         .replace(REPO_NAME, repoName)
                         .replace(REPO_URL, chartRepoUrl)
                         .replace(USERNAME, getUsername(username));

    return applyHelmHomePath(command, workingDirectory);
  }

  public String applyHelmHomePath(String command, String workingDirectory) {
    if (isBlank(workingDirectory)) {
      return command.replace(HELM_HOME_PATH_FLAG, "");
    } else {
      String helmHomePath = getHelmHomePath(workingDirectory);
      return command.replace(HELM_HOME_PATH_FLAG, "--home " + helmHomePath);
    }
  }

  public String addRepoFlags(String command, String repoName, String tempDir) {
    if (isNotBlank(repoName)) {
      return command + HELM_REPO_FLAGS.replace(HELM_CACHE_HOME_PLACEHOLDER, tempDir).replace(REPO_NAME, repoName);
    }
    return command;
  }

  public String getHelmHomePath(String workingDirectory) {
    return Paths.get(workingDirectory, "helm").normalize().toAbsolutePath().toString();
  }

  public String getHelmPath(HelmVersion helmVersion) {
    return encloseWithQuotesIfNeeded(k8sGlobalConfigService.getHelmPath(helmVersion));
  }

  private String getUsername(String username) {
    return isBlank(username) ? "" : "--username " + username;
  }

  private String getPassword(char[] password) {
    if (password == null) {
      return "";
    }

    String passwordAsString = new String(password);
    return isBlank(passwordAsString) ? "" : "--password " + passwordAsString;
  }

  public ProcessResult executeCommand(Map<String, String> envVars, String command, String directoryPath,
      String errorMessage, long timeoutInMillis, HelmCliCommandType helmCliCommandType) {
    ProcessExecutor processExecutor = createProcessExecutor(command, directoryPath, timeoutInMillis, envVars);
    return executeCommand(processExecutor, errorMessage, helmCliCommandType);
  }

  private ProcessResult executeCommand(
      ProcessExecutor processExecutor, String errorMessage, HelmCliCommandType helmCliCommandType) {
    errorMessage = isEmpty(errorMessage) ? "" : errorMessage;
    try {
      return processExecutor.execute();
    } catch (IOException e) {
      // Not setting the cause here because it carries forward the commands which can contain passwords
      throw new HelmClientException(format("[IO exception] %s", errorMessage), USER, helmCliCommandType);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HelmClientException(format("[Interrupted] %s", errorMessage), USER, helmCliCommandType);
    } catch (TimeoutException | UncheckedTimeoutException e) {
      throw new HelmClientException(format("[Timed out] %s", errorMessage), USER, helmCliCommandType);
    }
  }

  public ProcessExecutor createProcessExecutor(
      String command, String directoryPath, long timeoutInMillis, Map<String, String> envVars) {
    return new ProcessExecutor()
        .directory(isNotBlank(directoryPath) ? new File(directoryPath) : null)
        .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
        .commandSplit(command)
        .environment(envVars)
        .readOutput(true);
  }

  public String createNewDirectoryAtPath(String directoryBase) throws IOException {
    String workingDirectory = Paths.get(directoryBase, convertBase64UuidToCanonicalForm(generateUuid()))
                                  .normalize()
                                  .toAbsolutePath()
                                  .toString();

    createDirectoryIfDoesNotExist(workingDirectory);
    waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

    return workingDirectory;
  }

  public void removeRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis) {
    removeRepo(repoName, workingDirectory, helmVersion, timeoutInMillis, false, EMPTY);
  }

  public void removeRepo(String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis,
      boolean useRepoFlags, String tempDir) {
    try {
      Map<String, String> environment = new HashMap<>();
      String repoRemoveCommand = getRepoRemoveCommand(repoName, workingDirectory, helmVersion);
      if (useRepoFlags) {
        environment.putIfAbsent(HELM_CACHE_HOME,
            HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, tempDir));
        repoRemoveCommand = addRepoFlags(repoRemoveCommand, repoName, tempDir);
      }
      ProcessResult processResult = executeCommand(environment, repoRemoveCommand, null,
          format("remove helm repo %s", repoName), timeoutInMillis, HelmCliCommandType.REPO_REMOVE);
      if (processResult.getExitValue() != 0) {
        log.warn("Failed to remove helm repo {}. {}", repoName, processResult.getOutput().getUTF8());
      }
    } catch (Exception ex) {
      log.warn(ExceptionUtils.getMessage(ex));
    }
  }

  private String getRepoRemoveCommand(String repoName, String workingDirectory, HelmVersion helmVersion) {
    String repoRemoveCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_REMOVE, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace(REPO_NAME, repoName);

    return applyHelmHomePath(repoRemoveCommand, workingDirectory);
  }

  public void cleanup(String workingDirectory) {
    try {
      log.info("Cleaning up directory " + workingDirectory);
      deleteDirectoryAndItsContentIfExists(workingDirectory);
    } catch (Exception ex) {
      log.warn("Exception in directory cleanup.", ex);
    }
  }

  public String getHelmFetchCommand(String chartName, String chartVersion, String repoName, String workingDirectory,
      HelmVersion helmVersion, HelmCommandFlag helmCommandFlag) {
    HelmCliCommandType commandType = HelmCliCommandType.FETCH;
    String helmFetchCommand = HelmCommandTemplateFactory.getHelmCommandTemplate(commandType, helmVersion)
                                  .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
                                  .replace("${CHART_NAME}", chartName)
                                  .replace("${CHART_VERSION}", getChartVersion(chartVersion));

    if (isNotBlank(repoName)) {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME, repoName);
    } else {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME + "/", "");
    }

    Map<HelmSubCommandType, String> commandFlagValueMap =
        helmCommandFlag != null ? helmCommandFlag.getValueMap() : null;
    helmFetchCommand = HelmCommandFlagsUtils.applyHelmCommandFlags(
        helmFetchCommand, commandType.name(), commandFlagValueMap, helmVersion);
    return applyHelmHomePath(helmFetchCommand, workingDirectory);
  }

  public void fetchChartFromRepo(String repoName, String repoDisplayName, String chartName, String chartVersion,
      String chartDirectory, HelmVersion helmVersion, HelmCommandFlag helmCommandFlag, long timeoutInMillis,
      boolean useRepoFlags) {
    String helmFetchCommand =
        getHelmFetchCommand(chartName, chartVersion, repoName, chartDirectory, helmVersion, helmCommandFlag);
    if (!useRepoFlags) {
      executeFetchChartFromRepo(chartName, chartDirectory, repoDisplayName, helmFetchCommand, timeoutInMillis);
      return;
    }
    String dir = Paths.get(RESOURCE_DIR_BASE, repoName, "cache").toAbsolutePath().normalize().toString();
    executeFetchChartFromRepoUseRepoFlag(
        chartName, chartDirectory, repoDisplayName, helmFetchCommand, timeoutInMillis, repoName, dir);
  }

  public void executeFetchChartFromRepoUseRepoFlag(String chartName, String chartDirectory, String repoDisplayName,
      String helmFetchCommand, long timeoutInMillis, String repoName, String dir) {
    Map<String, String> environment = new HashMap<>();
    environment.put(
        HELM_CACHE_HOME, HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, dir));
    helmFetchCommand = addRepoFlags(helmFetchCommand, repoName, dir);

    log.info(helmFetchCommand);

    ProcessResult processResult = executeCommand(environment, helmFetchCommand, chartDirectory,
        format("fetch chart %s", chartName), timeoutInMillis, HelmCliCommandType.FETCH);

    if (processResult.getExitValue() != 0) {
      StringBuilder builder = new StringBuilder().append("Failed to fetch chart \"").append(chartName).append("\" ");
      if (isNotBlank(repoDisplayName)) {
        builder.append(" from repo \"").append(repoDisplayName).append("\". ");
      }
      builder.append("Please check if the chart is present in the repo.");
      if (processResult.hasOutput()) {
        builder.append(" Details: ").append(processResult.outputUTF8());
      }
      throw new HelmClientException(builder.toString(), HelmCliCommandType.FETCH);
    }
  }

  public void executeFetchChartFromRepo(
      String chartName, String chartDirectory, String repoDisplayName, String helmFetchCommand, long timeoutInMillis) {
    log.info(helmFetchCommand);

    ProcessResult processResult = executeCommand(Collections.emptyMap(), helmFetchCommand, chartDirectory,
        format("fetch chart %s", chartName), timeoutInMillis, HelmCliCommandType.FETCH);
    if (processResult.getExitValue() != 0) {
      StringBuilder builder = new StringBuilder().append("Failed to fetch chart \"").append(chartName).append("\" ");

      if (isNotBlank(repoDisplayName)) {
        builder.append(" from repo \"").append(repoDisplayName).append("\". ");
      }
      builder.append("Please check if the chart is present in the repo.");
      if (processResult.hasOutput()) {
        builder.append(" Details: ").append(processResult.outputUTF8());
      }
      throw new HelmClientException(builder.toString(), HelmCliCommandType.FETCH);
    }
  }

  public void downloadChartFilesFromHttpRepo(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) {
    if (!(manifest.getStoreDelegateConfig() instanceof HttpHelmStoreDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("storeDelegateConfig", "Must be instance of HttpHelmStoreDelegateConfig"));
    }

    HttpHelmStoreDelegateConfig storeDelegateConfig = (HttpHelmStoreDelegateConfig) manifest.getStoreDelegateConfig();
    HttpHelmConnectorDTO httpHelmConnector = storeDelegateConfig.getHttpHelmConnector();

    String username = getHttpHelmUsername(httpHelmConnector);
    char[] password = getHttpHelmPassword(httpHelmConnector);
    addRepo(storeDelegateConfig.getRepoName(), storeDelegateConfig.getRepoDisplayName(),
        httpHelmConnector.getHelmRepoUrl(), username, password, destinationDirectory, manifest.getHelmVersion(),
        timeoutInMillis, false);
    fetchChartFromRepo(storeDelegateConfig.getRepoName(), storeDelegateConfig.getRepoDisplayName(),
        manifest.getChartName(), manifest.getChartVersion(), destinationDirectory, manifest.getHelmVersion(),
        manifest.getHelmCommandFlag(), timeoutInMillis, false);
  }

  public void downloadChartFilesUsingChartMuseum(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) throws Exception {
    String resourceDirectory = null;
    ChartMuseumServer chartMuseumServer = null;
    String repoName = null;
    String repoDisplayName = null;
    StoreDelegateConfig storeDelegateConfig = manifest.getStoreDelegateConfig();
    if (S3_HELM == storeDelegateConfig.getType()) {
      S3HelmStoreDelegateConfig s3StoreDelegateConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
      repoName = s3StoreDelegateConfig.getRepoName();
      repoDisplayName = s3StoreDelegateConfig.getRepoDisplayName();
    } else if (GCS_HELM == storeDelegateConfig.getType()) {
      GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
      repoName = gcsHelmStoreDelegateConfig.getRepoName();
      repoDisplayName = gcsHelmStoreDelegateConfig.getRepoDisplayName();
    }

    try {
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartMuseumServer =
          ngChartMuseumService.startChartMuseumServer(manifest.getStoreDelegateConfig(), resourceDirectory);

      addChartMuseumRepo(repoName, repoDisplayName, chartMuseumServer.getPort(), destinationDirectory,
          manifest.getHelmVersion(), timeoutInMillis);
      fetchChartFromRepo(repoName, repoDisplayName, manifest.getChartName(), manifest.getChartVersion(),
          destinationDirectory, manifest.getHelmVersion(), manifest.getHelmCommandFlag(), timeoutInMillis, false);

    } finally {
      if (chartMuseumServer != null) {
        ngChartMuseumService.stopChartMuseumServer(chartMuseumServer);
      }

      if (repoName != null) {
        removeRepo(repoName, destinationDirectory, manifest.getHelmVersion(), timeoutInMillis);
      }

      cleanup(resourceDirectory);
    }
  }

  public void addChartMuseumRepo(String repoName, String repoDisplayName, int port, String chartDirectory,
      HelmVersion helmVersion, long timeoutInMillis) {
    String repoAddCommand = getChartMuseumRepoAddCommand(repoName, port, chartDirectory, helmVersion);
    log.info(repoAddCommand);
    log.info(ADD_COMMAND_FOR_REPOSITORY + repoDisplayName);

    ProcessResult processResult = executeCommand(Collections.emptyMap(), repoAddCommand, chartDirectory,
        ADD_COMMAND_FOR_REPOSITORY + repoDisplayName, timeoutInMillis, HelmCliCommandType.REPO_ADD);
    if (processResult.getExitValue() != 0) {
      throw new HelmClientException(
          "Failed to add helm repo. Executed command " + repoAddCommand + ". " + processResult.getOutput().getUTF8(),
          USER, HelmCliCommandType.REPO_ADD);
    }
  }

  private String getChartMuseumRepoAddCommand(
      String repoName, int port, String workingDirectory, HelmVersion helmVersion) {
    String repoUrl = CHART_MUSEUM_SERVER_URL.replace("${PORT}", Integer.toString(port));

    String repoAddCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_ADD_CHART_MEUSEUM, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace(REPO_NAME, repoName)
            .replace(REPO_URL, repoUrl);

    return applyHelmHomePath(repoAddCommand, workingDirectory);
  }

  public void printHelmChartInfoWithVersionInExecutionLogs(String workingDirectory,
      HelmChartManifestDelegateConfig manifestDelegateConfig, LogCallback executionLogCallback) {
    if (isNotBlank(manifestDelegateConfig.getChartVersion())) {
      printHelmChartInfoInExecutionLogs(manifestDelegateConfig, executionLogCallback);
      return;
    }

    try {
      HelmChartInfo helmChartInfo = getHelmChartInfoFromChartsYamlFile(
          Paths.get(workingDirectory, manifestDelegateConfig.getChartName(), CHARTS_YAML_KEY).toString());

      HelmChartManifestDelegateConfig helmChartManifestDelegateConfigWithChartVersion =
          HelmChartManifestDelegateConfig.builder()
              .storeDelegateConfig(manifestDelegateConfig.getStoreDelegateConfig())
              .chartName(manifestDelegateConfig.getChartName())
              .helmVersion(manifestDelegateConfig.getHelmVersion())
              .chartVersion(helmChartInfo.getVersion())
              .helmCommandFlag(manifestDelegateConfig.getHelmCommandFlag())
              .build();
      printHelmChartInfoInExecutionLogs(helmChartManifestDelegateConfigWithChartVersion, executionLogCallback);
    } catch (Exception e) {
      log.info("Unable to fetch chart version", e);
    }
  }

  public void printHelmChartInfoInExecutionLogs(
      HelmChartManifestDelegateConfig manifestDelegateConfig, LogCallback executionLogCallback) {
    String repoDisplayName = "";
    String basePath = "";
    String chartName = manifestDelegateConfig.getChartName();
    String chartVersion = manifestDelegateConfig.getChartVersion();
    String chartBucket = "";
    String region = "";
    String chartRepoUrl = "";

    switch (manifestDelegateConfig.getStoreDelegateConfig().getType()) {
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig httpStoreDelegateConfig =
            (HttpHelmStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        repoDisplayName = httpStoreDelegateConfig.getRepoDisplayName();
        chartRepoUrl = httpStoreDelegateConfig.getHttpHelmConnector().getHelmRepoUrl();
        break;

      case S3_HELM:
        S3HelmStoreDelegateConfig s3HelmStoreDelegateConfig =
            (S3HelmStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        repoDisplayName = s3HelmStoreDelegateConfig.getRepoDisplayName();
        basePath = s3HelmStoreDelegateConfig.getFolderPath();
        chartBucket = s3HelmStoreDelegateConfig.getBucketName();
        region = s3HelmStoreDelegateConfig.getRegion();
        break;

      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig =
            (GcsHelmStoreDelegateConfig) manifestDelegateConfig.getStoreDelegateConfig();
        repoDisplayName = gcsHelmStoreDelegateConfig.getRepoDisplayName();
        basePath = gcsHelmStoreDelegateConfig.getFolderPath();
        chartBucket = gcsHelmStoreDelegateConfig.getBucketName();
        break;

      default:
    }

    if (isNotBlank(repoDisplayName)) {
      executionLogCallback.saveExecutionLog("Helm repository: " + repoDisplayName);
    }

    if (isNotBlank(basePath)) {
      executionLogCallback.saveExecutionLog("Base Path: " + basePath);
    }

    if (isNotBlank(chartName)) {
      executionLogCallback.saveExecutionLog("Chart name: " + chartName);
    }

    if (isNotBlank(chartVersion)) {
      executionLogCallback.saveExecutionLog("Chart version: " + chartVersion);
    }

    if (manifestDelegateConfig.getHelmVersion() != null) {
      executionLogCallback.saveExecutionLog("Helm version: " + manifestDelegateConfig.getHelmVersion());
    }

    if (isNotBlank(chartBucket)) {
      executionLogCallback.saveExecutionLog("Chart bucket: " + chartBucket);
    }

    if (isNotBlank(region)) {
      executionLogCallback.saveExecutionLog("Region: " + region);
    }

    if (isNotBlank(chartRepoUrl)) {
      executionLogCallback.saveExecutionLog("Repo url: " + chartRepoUrl);
    }
  }

  private String getChartVersion(String chartVersion) {
    return isBlank(chartVersion) ? StringUtils.EMPTY : "--version " + chartVersion;
  }

  public static String getChartDirectory(String parentDir, String chartName) {
    int lastIndex = chartName.lastIndexOf('/');
    if (lastIndex != -1) {
      return Paths.get(parentDir, chartName.substring(lastIndex + 1)).toString();
    }
    return Paths.get(parentDir, chartName).toString();
  }

  public String fetchValuesYamlFromChart(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig,
      long timeoutInMillis, LogCallback logCallback) throws Exception {
    String workingDirectory = createNewDirectoryAtPath(Paths.get(WORKING_DIR_BASE).toString());
    logCallback.saveExecutionLog(color("\nFetching values.yaml from helm chart repo", White, Bold));

    try {
      downloadHelmChartFiles(helmChartManifestDelegateConfig, workingDirectory, timeoutInMillis);
      printHelmChartInfoWithVersionInExecutionLogs(workingDirectory, helmChartManifestDelegateConfig, logCallback);

      String valuesFileContent =
          readValuesYamlFromChartFiles(workingDirectory, helmChartManifestDelegateConfig.getChartName());
      if (null == valuesFileContent) {
        logCallback.saveExecutionLog("No values.yaml found", WARN);
      } else {
        logCallback.saveExecutionLog("\nSuccessfully fetched values.yaml", INFO);
      }

      return valuesFileContent;
    } catch (HelmClientException ex) {
      String errorMsg = format("Failed to fetch values yaml from %s repo. ",
          helmChartManifestDelegateConfig.getStoreDelegateConfig().getType());
      logCallback.saveExecutionLog(errorMsg + ExceptionUtils.getMessage(ex), WARN);
      throw new HelmClientRuntimeException(ex);
    } catch (Exception ex) {
      String errorMsg = format("Failed to fetch values yaml from %s repo. ",
          helmChartManifestDelegateConfig.getStoreDelegateConfig().getType());
      logCallback.saveExecutionLog(errorMsg + ExceptionUtils.getMessage(ex), WARN);
      throw ex;
    } finally {
      cleanup(workingDirectory);
    }
  }

  private String readValuesYamlFromChartFiles(String workingDirectory, String chartName) throws Exception {
    return new String(Files.readAllBytes(Paths.get(getChartDirectory(workingDirectory, chartName), VALUES_YAML)),
        StandardCharsets.UTF_8);
  }

  private void downloadHelmChartFiles(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig,
      String destinationDirectory, long timeoutInMillis) throws Exception {
    StoreDelegateConfig helmStoreDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();
    initHelm(destinationDirectory, helmChartManifestDelegateConfig.getHelmVersion(), timeoutInMillis);

    if (HTTP_HELM == helmStoreDelegateConfig.getType()) {
      downloadChartFilesFromHttpRepo(helmChartManifestDelegateConfig, destinationDirectory, timeoutInMillis);
    } else {
      downloadChartFilesUsingChartMuseum(helmChartManifestDelegateConfig, destinationDirectory, timeoutInMillis);
    }
  }

  public String getHttpHelmUsername(final HttpHelmConnectorDTO httpHelmConnectorDTO) {
    if (httpHelmConnectorDTO.getAuth().getAuthType() == HttpHelmAuthType.ANONYMOUS) {
      return null;
    }

    HttpHelmUsernamePasswordDTO creds = (HttpHelmUsernamePasswordDTO) httpHelmConnectorDTO.getAuth().getCredentials();
    return FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
        creds.getUsername(), creds.getUsernameRef());
  }

  public char[] getHttpHelmPassword(final HttpHelmConnectorDTO httpHelmConnectorDTO) {
    if (httpHelmConnectorDTO.getAuth().getAuthType() == HttpHelmAuthType.ANONYMOUS) {
      return null;
    }

    HttpHelmUsernamePasswordDTO creds = (HttpHelmUsernamePasswordDTO) httpHelmConnectorDTO.getAuth().getCredentials();
    return creds.getPasswordRef().getDecryptedValue();
  }

  /**
   * Method to extract Helm Chart info like Chart version and Chart name from the downloaded Chart files.
   * @param chartYamlPath - Path of the Chart.yaml file
   * @return HelmChartInfo - This contains details about the Helm chart
   * @throws IOException
   */
  public HelmChartInfo getHelmChartInfoFromChartsYamlFile(String chartYamlPath) throws IOException {
    String chartVersion = null;
    String chartName = null;
    boolean versionFound = false;
    boolean nameFound = false;

    try (BufferedReader br =
             new BufferedReader(new InputStreamReader(new FileInputStream(chartYamlPath), StandardCharsets.UTF_8))) {
      String line;

      while ((line = br.readLine()) != null) {
        if (!versionFound && line.startsWith(VERSION_KEY)) {
          chartVersion = line.substring(VERSION_KEY.length() + 1);
          versionFound = true;
        }

        if (!nameFound && line.startsWith(NAME_KEY)) {
          chartName = line.substring(NAME_KEY.length() + 1);
          nameFound = true;
        }

        if (versionFound && nameFound) {
          break;
        }
      }
    }

    return HelmChartInfo.builder().version(chartVersion).name(chartName).build();
  }

  public List<String> fetchChartVersions(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig,
      long timeoutInMillis, String destinationDirectory) throws Exception {
    StoreDelegateConfig helmStoreDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();
    String workingDirectory = createDirectory(destinationDirectory);
    initHelm(workingDirectory, helmChartManifestDelegateConfig.getHelmVersion(), timeoutInMillis);

    if (HTTP_HELM == helmStoreDelegateConfig.getType()) {
      return fetchVersionsFromHttp(helmChartManifestDelegateConfig, workingDirectory, timeoutInMillis);
    } else {
      return fetchVersionsUsingChartMuseumServer(helmChartManifestDelegateConfig, workingDirectory, timeoutInMillis);
    }
  }

  String createDirectory(String directoryBase) throws IOException {
    String workingDirectory = Paths.get(directoryBase).normalize().toAbsolutePath().toString();

    createDirectoryIfDoesNotExist(workingDirectory);
    waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

    return workingDirectory;
  }

  private List<String> fetchVersionsFromHttp(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) throws IOException {
    if (!(manifest.getStoreDelegateConfig() instanceof HttpHelmStoreDelegateConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("storeDelegateConfig", "Must be instance of HttpHelmStoreDelegateConfig"));
    }

    HttpHelmStoreDelegateConfig storeDelegateConfig = (HttpHelmStoreDelegateConfig) manifest.getStoreDelegateConfig();
    HttpHelmConnectorDTO httpHelmConnector = storeDelegateConfig.getHttpHelmConnector();

    String username = getHttpHelmUsername(httpHelmConnector);
    char[] password = getHttpHelmPassword(httpHelmConnector);
    addRepo(storeDelegateConfig.getRepoName(), storeDelegateConfig.getRepoDisplayName(),
        httpHelmConnector.getHelmRepoUrl(), username, password, destinationDirectory, manifest.getHelmVersion(),
        timeoutInMillis, false);
    updateRepo(
        storeDelegateConfig.getRepoName(), destinationDirectory, manifest.getHelmVersion(), timeoutInMillis, EMPTY);

    ProcessResult processResult = executeCommand(Collections.emptyMap(),
        fetchHelmChartVersionsCommand(manifest.getHelmVersion(), manifest.getChartName(),
            storeDelegateConfig.getRepoName(), destinationDirectory),
        destinationDirectory, "Helm chart fetch versions command failed ", timeoutInMillis,
        HelmCliCommandType.FETCH_ALL_VERSIONS);

    String commandOutput = "";
    if (processResult != null && processResult.getOutput() != null) {
      commandOutput = processResult.getOutput().getString();
    }

    return parseHelmVersionsFromOutput(commandOutput, manifest);
  }

  public void updateRepo(
      String repoName, String workingDirectory, HelmVersion helmVersion, long timeoutInMillis, String repoDir) {
    try {
      String repoUpdateCommand = getRepoUpdateCommand(repoName, workingDirectory, helmVersion);
      Map<String, String> environment = new HashMap<>();

      if (!isEmpty(repoDir)) {
        environment.put(HELM_CACHE_HOME,
            HELM_CACHE_HOME_PATH.replace(REPO_NAME, repoName).replace(HELM_CACHE_HOME_PLACEHOLDER, repoDir));
        repoUpdateCommand = addRepoFlags(repoUpdateCommand, repoName, repoDir);
      }

      ProcessResult processResult = executeCommand(environment, repoUpdateCommand, workingDirectory,
          format("update helm repo %s", repoName), timeoutInMillis, HelmCliCommandType.REPO_UPDATE);

      log.info("Repo update command executed on delegate: {}", repoUpdateCommand);
      if (processResult.getExitValue() != 0) {
        log.warn("Failed to update helm repo {}. {}", repoName, processResult.getOutput().getUTF8());
      }
    } catch (Exception ex) {
      log.warn(ExceptionUtils.getMessage(ex));
    }
  }

  private String getRepoUpdateCommand(String repoName, String workingDirectory, HelmVersion helmVersion) {
    String repoUpdateCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.REPO_UPDATE, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace("KUBECONFIG=${KUBECONFIG_PATH}", "")
            .replace(REPO_NAME, repoName);

    return applyHelmHomePath(repoUpdateCommand, workingDirectory);
  }

  public String fetchHelmChartVersionsCommand(
      HelmVersion helmVersion, String chartName, String repoName, String workingDirectory) {
    String helmFetchCommand =
        HelmCommandTemplateFactory.getHelmCommandTemplate(HelmCliCommandType.FETCH_ALL_VERSIONS, helmVersion)
            .replace(HELM_PATH_PLACEHOLDER, getHelmPath(helmVersion))
            .replace("${CHART_NAME}", chartName);

    if (isNotBlank(repoName)) {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME, repoName);
    } else {
      helmFetchCommand = helmFetchCommand.replace(REPO_NAME + "/", "");
    }
    return applyHelmHomePath(helmFetchCommand, workingDirectory);
  }

  public String fetchHelmChartVersionsCommandWithRepoFlags(
      HelmVersion helmVersion, String chartName, String repoName, String workingDirectory, String tempDir) {
    return addRepoFlags(
        fetchHelmChartVersionsCommand(helmVersion, chartName, repoName, workingDirectory), repoName, tempDir);
  }

  private List<String> parseHelmVersionsFromOutput(String commandOutput, HelmChartManifestDelegateConfig manifestConfig)
      throws IOException {
    String errorMessage = "No chart with the given name found. Chart might be deleted at source";
    if (isEmpty(commandOutput) || commandOutput.contains("No results found")) {
      throw new InvalidRequestException(errorMessage);
    }

    CSVFormat csvFormat = CSVFormat.RFC4180.withFirstRecordAsHeader().withDelimiter('\t').withTrim();
    List<CSVRecord> records = CSVParser.parse(commandOutput, csvFormat).getRecords();
    if (isEmpty(records)) {
      throw new InvalidRequestException(errorMessage);
    }

    List<String> chartVersions =
        records.stream()
            .filter(record -> record.size() > 1 && matchesChartName(manifestConfig.getChartName(), record.get(0)))
            .map(record -> record.get(1))
            .collect(Collectors.toList());

    if (isEmpty(chartVersions)) {
      throw new InvalidRequestException(errorMessage);
    }

    return chartVersions;
  }

  private boolean matchesChartName(String chartName, String recordName) {
    return Arrays.asList(recordName.split("/")).contains(chartName);
  }

  private List<String> fetchVersionsUsingChartMuseumServer(
      HelmChartManifestDelegateConfig manifest, String destinationDirectory, long timeoutInMillis) throws Exception {
    String resourceDirectory = null;
    ChartMuseumServer chartMuseumServer = null;
    String repoName = null;
    String repoDisplayName = null;
    StoreDelegateConfig storeDelegateConfig = manifest.getStoreDelegateConfig();
    if (S3_HELM == storeDelegateConfig.getType()) {
      S3HelmStoreDelegateConfig s3StoreDelegateConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
      repoName = s3StoreDelegateConfig.getRepoName();
      repoDisplayName = s3StoreDelegateConfig.getRepoDisplayName();
    } else if (GCS_HELM == storeDelegateConfig.getType()) {
      GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
      repoName = gcsHelmStoreDelegateConfig.getRepoName();
      repoDisplayName = gcsHelmStoreDelegateConfig.getRepoDisplayName();
    }

    try {
      resourceDirectory = createNewDirectoryAtPath(RESOURCE_DIR_BASE);
      chartMuseumServer =
          ngChartMuseumService.startChartMuseumServer(manifest.getStoreDelegateConfig(), resourceDirectory);

      addChartMuseumRepo(repoName, repoDisplayName, chartMuseumServer.getPort(), destinationDirectory,
          manifest.getHelmVersion(), timeoutInMillis);
      ProcessResult processResult = executeCommand(Collections.emptyMap(),
          fetchHelmChartVersionsCommand(
              manifest.getHelmVersion(), manifest.getChartName(), repoName, destinationDirectory),
          destinationDirectory, "Helm chart fetch versions command failed ", timeoutInMillis,
          HelmCliCommandType.FETCH_ALL_VERSIONS);
      String commandOutput = processResult.getOutput().getUTF8();
      return parseHelmVersionsFromOutput(commandOutput, manifest);
    } finally {
      if (chartMuseumServer != null) {
        ngChartMuseumService.stopChartMuseumServer(chartMuseumServer);
      }
    }
  }

  public void cleanupAfterCollection(
      HelmChartManifestDelegateConfig config, String destinationDirectory, long timeoutInMillis) {
    String workingDirectory = Paths.get(destinationDirectory).toString();
    StoreDelegateConfig storeDelegateConfig = config.getStoreDelegateConfig();
    String repoName;
    switch (storeDelegateConfig.getType()) {
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig helmStoreConfig = (HttpHelmStoreDelegateConfig) storeDelegateConfig;
        repoName = helmStoreConfig.getRepoName();
        break;
      case S3_HELM:
        S3HelmStoreDelegateConfig s3StoreConfig = (S3HelmStoreDelegateConfig) storeDelegateConfig;
        repoName = s3StoreConfig.getRepoName();
        break;
      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsStoreConfig = (GcsHelmStoreDelegateConfig) storeDelegateConfig;
        repoName = gcsStoreConfig.getRepoName();
        break;
      default:
        throw new ManifestCollectionException("Manifest collection not supported for other helm repos");
    }
    removeRepo(repoName, workingDirectory, config.getHelmVersion(), timeoutInMillis);
    cleanup(workingDirectory);
  }

  public void decryptEncryptedDetails(HelmChartManifestDelegateConfig helmChartManifestDelegateConfig) {
    StoreDelegateConfig helmStoreDelegateConfig = helmChartManifestDelegateConfig.getStoreDelegateConfig();
    switch (helmStoreDelegateConfig.getType()) {
      case S3_HELM:
        S3HelmStoreDelegateConfig s3HelmStoreConfig = (S3HelmStoreDelegateConfig) helmStoreDelegateConfig;
        List<DecryptableEntity> s3DecryptableEntityList = s3HelmStoreConfig.getAwsConnector().getDecryptableEntities();
        if (isNotEmpty(s3DecryptableEntityList)) {
          for (DecryptableEntity entity : s3HelmStoreConfig.getAwsConnector().getDecryptableEntities()) {
            decryptionService.decrypt(entity, s3HelmStoreConfig.getEncryptedDataDetails());
            ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, s3HelmStoreConfig.getEncryptedDataDetails());
          }
        }
        break;
      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelmStoreDelegateConfig = (GcsHelmStoreDelegateConfig) helmStoreDelegateConfig;
        List<DecryptableEntity> gcsDecryptableEntityList =
            gcsHelmStoreDelegateConfig.getGcpConnector().getDecryptableEntities();
        if (isNotEmpty(gcsDecryptableEntityList)) {
          for (DecryptableEntity entity : gcsDecryptableEntityList) {
            decryptionService.decrypt(entity, gcsHelmStoreDelegateConfig.getEncryptedDataDetails());
            ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
                entity, gcsHelmStoreDelegateConfig.getEncryptedDataDetails());
          }
        }
        break;
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig httpHelmStoreConfig = (HttpHelmStoreDelegateConfig) helmStoreDelegateConfig;
        for (DecryptableEntity entity : httpHelmStoreConfig.getHttpHelmConnector().getDecryptableEntities()) {
          decryptionService.decrypt(entity, httpHelmStoreConfig.getEncryptedDataDetails());
          ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, httpHelmStoreConfig.getEncryptedDataDetails());
        }
        break;
      default:
        throw new InvalidRequestException(
            format("Store type: %s not supported for helm values fetch task NG", helmStoreDelegateConfig.getType()));
    }
  }
}