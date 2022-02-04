/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.getChartDirectory;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.k8s.model.Kind.Namespace;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.k8s.beans.K8sHandlerConfig;
import io.harness.delegate.k8s.kustomize.KustomizeTaskHelper;
import io.harness.delegate.k8s.openshift.OpenShiftDelegateService;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.git.model.GitFile;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manifest.CustomManifestService;
import io.harness.service.ScmServiceClient;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.ExceptionMessageSanitizer;
import software.wings.delegatetasks.ScmFetchFilesHelper;
import software.wings.delegatetasks.helm.HelmTaskHelper;
import software.wings.exception.ShellScriptException;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.HelmHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sDeleteTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskResponse;
import software.wings.helpers.ext.kustomize.KustomizeConfig;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTaskHelper {
  @Inject protected DelegateLogService delegateLogService;
  @Inject private transient KubernetesContainerService kubernetesContainerService;
  @Inject private GitService gitService;
  @Inject private EncryptionService encryptionService;
  @Inject private HelmTaskHelper helmTaskHelper;
  @Inject private KustomizeTaskHelper kustomizeTaskHelper;
  @Inject private OpenShiftDelegateService openShiftDelegateService;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private HelmHelper helmHelper;
  @Inject private CustomManifestService customManifestService;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private ScmDelegateClient scmDelegateClient;
  @Inject private ScmServiceClient scmServiceClient;
  @Inject private ScmFetchFilesHelper scmFetchFilesHelper;

  public boolean doStatusCheckAllResourcesForHelm(Kubectl client, List<KubernetesResourceId> resourceIds, String ocPath,
      String workingDir, String namespace, String kubeconfigPath, ExecutionLogCallback executionLogCallback)
      throws Exception {
    return k8sTaskHelperBase.doStatusCheckForAllResources(client, resourceIds,
        K8sDelegateTaskParams.builder()
            .ocPath(ocPath)
            .workingDirectory(workingDir)
            .kubeconfigPath(kubeconfigPath)
            .build(),
        namespace, executionLogCallback, false);
  }

  private boolean writeManifestFilesToDirectory(
      List<ManifestFile> manifestFiles, String manifestFilesDirectory, ExecutionLogCallback executionLogCallback) {
    String directoryPath = Paths.get(manifestFilesDirectory).toString();

    try {
      for (int i = 0; i < manifestFiles.size(); i++) {
        ManifestFile manifestFile = manifestFiles.get(i);
        if (StringUtils.equals(values_filename, manifestFile.getFileName())) {
          continue;
        }

        Path filePath = Paths.get(directoryPath, manifestFile.getFileName());
        Path parent = filePath.getParent();
        if (parent == null) {
          throw new WingsException("Failed to create file at path " + filePath.toString());
        }

        createDirectoryIfDoesNotExist(parent.toString());
        FileIo.writeUtf8StringToFile(filePath.toString(), manifestFile.getFileContent());
      }

      return true;
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)),
          ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  public List<FileData> renderTemplate(K8sDelegateTaskParams k8sDelegateTaskParams,
      K8sDelegateManifestConfig k8sDelegateManifestConfig, String manifestFilesDirectory,
      List<String> manifestOverrideFiles, String releaseName, String namespace,
      ExecutionLogCallback executionLogCallback, K8sTaskParameters k8sTaskParameters) throws Exception {
    StoreType storeType = k8sDelegateManifestConfig.getManifestStoreTypes();
    long timeoutInMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(k8sTaskParameters.getTimeoutIntervalInMin());
    HelmCommandFlag helmCommandFlag = k8sDelegateManifestConfig.getHelmCommandFlag();

    switch (storeType) {
      case Local:
      case Remote:
        List<FileData> manifestFiles = k8sTaskHelperBase.readManifestFilesFromDirectory(manifestFilesDirectory);
        return k8sTaskHelperBase.renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, manifestOverrideFiles, executionLogCallback, timeoutInMillis);

      case HelmSourceRepo:
        return k8sTaskHelperBase.renderTemplateForHelm(k8sDelegateTaskParams.getHelmPath(), manifestFilesDirectory,
            manifestOverrideFiles, releaseName, namespace, executionLogCallback, k8sTaskParameters.getHelmVersion(),
            timeoutInMillis, helmCommandFlag);

      case HelmChartRepo:
        manifestFilesDirectory = Paths
                                     .get(getChartDirectory(manifestFilesDirectory,
                                         k8sDelegateManifestConfig.getHelmChartConfigParams().getChartName()))
                                     .toString();
        return k8sTaskHelperBase.renderTemplateForHelm(k8sDelegateTaskParams.getHelmPath(), manifestFilesDirectory,
            manifestOverrideFiles, releaseName, namespace, executionLogCallback, k8sTaskParameters.getHelmVersion(),
            timeoutInMillis, helmCommandFlag);

      case KustomizeSourceRepo:
        KustomizeConfig kustomizeConfig = k8sDelegateManifestConfig.getKustomizeConfig();
        String pluginRootDir = kustomizeConfig != null ? kustomizeConfig.getPluginRootDir() : null;
        String kustomizeDirPath = kustomizeConfig != null ? kustomizeConfig.getKustomizeDirPath() : null;

        if (k8sDelegateTaskParams.isUseLatestKustomizeVersion()) {
          String kustomizePath = manifestFilesDirectory + '/' + kustomizeDirPath;
          k8sTaskHelperBase.savingPatchesToDirectory(kustomizePath, manifestOverrideFiles, executionLogCallback);
        }
        return kustomizeTaskHelper.build(manifestFilesDirectory, k8sDelegateTaskParams.getKustomizeBinaryPath(),
            pluginRootDir, kustomizeDirPath, executionLogCallback);
      case OC_TEMPLATES:
        return openShiftDelegateService.processTemplatization(manifestFilesDirectory, k8sDelegateTaskParams.getOcPath(),
            k8sDelegateManifestConfig.getGitFileConfig().getFilePath(), executionLogCallback, manifestOverrideFiles);

      case CUSTOM:
        // Creating a new branch for check if custom manifest feature is enabled, can be merged with Local & Remote
        // once FF is removed
        if (k8sDelegateManifestConfig.isCustomManifestEnabled()) {
          List<FileData> customManifestFiles = k8sTaskHelperBase.readManifestFilesFromDirectory(manifestFilesDirectory);
          return k8sTaskHelperBase.renderManifestFilesForGoTemplate(
              k8sDelegateTaskParams, customManifestFiles, manifestOverrideFiles, executionLogCallback, timeoutInMillis);
        }

      // fallthrough to ignore branch if FF is not enabled
      case CUSTOM_OPENSHIFT_TEMPLATE:
        if (k8sDelegateManifestConfig.isCustomManifestEnabled()) {
          return openShiftDelegateService.processTemplatization(manifestFilesDirectory,
              k8sDelegateTaskParams.getOcPath(),
              k8sDelegateManifestConfig.getCustomManifestSource().getFilePaths().get(0), executionLogCallback,
              manifestOverrideFiles);
        }

        // fallthrough to ignore branch if FF is not enabled
      default:
        unhandled(storeType);
    }

    return new ArrayList<>();
  }

  public List<FileData> renderTemplateForGivenFiles(K8sDelegateTaskParams k8sDelegateTaskParams,
      K8sDelegateManifestConfig k8sDelegateManifestConfig, String manifestFilesDirectory,
      @NotEmpty List<String> filesList, List<String> manifestOverrideFiles, String releaseName, String namespace,
      ExecutionLogCallback executionLogCallback, K8sTaskParameters k8sTaskParameters, boolean skipRendering)
      throws Exception {
    StoreType storeType = k8sDelegateManifestConfig.getManifestStoreTypes();
    long timeoutInMillis = K8sTaskHelperBase.getTimeoutMillisFromMinutes(k8sTaskParameters.getTimeoutIntervalInMin());
    HelmCommandFlag helmCommandFlag = k8sDelegateManifestConfig.getHelmCommandFlag();

    switch (storeType) {
      case Local:
      case Remote:
        List<FileData> manifestFiles =
            k8sTaskHelperBase.readFilesFromDirectory(manifestFilesDirectory, filesList, executionLogCallback);
        if (skipRendering) {
          return manifestFiles;
        }
        return k8sTaskHelperBase.renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, manifestOverrideFiles, executionLogCallback, timeoutInMillis);

      case HelmSourceRepo:
        return k8sTaskHelperBase.renderTemplateForHelmChartFiles(k8sDelegateTaskParams.getHelmPath(),
            manifestFilesDirectory, filesList, manifestOverrideFiles, releaseName, namespace, executionLogCallback,
            k8sTaskParameters.getHelmVersion(), timeoutInMillis, helmCommandFlag);

      case HelmChartRepo:
        manifestFilesDirectory =
            Paths.get(manifestFilesDirectory, k8sDelegateManifestConfig.getHelmChartConfigParams().getChartName())
                .toString();
        return k8sTaskHelperBase.renderTemplateForHelmChartFiles(k8sDelegateTaskParams.getHelmPath(),
            manifestFilesDirectory, filesList, manifestOverrideFiles, releaseName, namespace, executionLogCallback,
            k8sTaskParameters.getHelmVersion(), timeoutInMillis, helmCommandFlag);
      case KustomizeSourceRepo:
        KustomizeConfig kustomizeConfig = k8sDelegateManifestConfig.getKustomizeConfig();
        String pluginRootDir = kustomizeConfig != null ? kustomizeConfig.getPluginRootDir() : null;
        return kustomizeTaskHelper.buildForApply(k8sDelegateTaskParams.getKustomizeBinaryPath(), pluginRootDir,
            manifestFilesDirectory, filesList, k8sTaskParameters.isUseLatestKustomizeVersion(), manifestOverrideFiles,
            executionLogCallback);

      default:
        unhandled(storeType);
    }

    return new ArrayList<>();
  }

  public List<KubernetesResource> getResourcesFromManifests(K8sDelegateTaskParams k8sDelegateTaskParams,
      K8sDelegateManifestConfig k8sDelegateManifestConfig, String manifestFilesDirectory,
      @NotEmpty List<String> filesList, List<String> valuesFiles, String releaseName, String namespace,
      ExecutionLogCallback executionLogCallback, K8sTaskParameters k8sTaskParameters, boolean skipRendering)
      throws Exception {
    List<FileData> manifestFiles =
        renderTemplateForGivenFiles(k8sDelegateTaskParams, k8sDelegateManifestConfig, manifestFilesDirectory, filesList,
            valuesFiles, releaseName, namespace, executionLogCallback, k8sTaskParameters, skipRendering);
    if (isEmpty(manifestFiles)) {
      return new ArrayList<>();
    }

    List<KubernetesResource> resources = k8sTaskHelperBase.readManifests(manifestFiles, executionLogCallback);
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, namespace);

    return resources;
  }

  private void printGitConfigInExecutionLogs(
      GitConfig gitConfig, GitFileConfig gitFileConfig, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog("\n" + color("Fetching manifest files", White, Bold));
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitConfig.getRepoUrl());
    if (gitFileConfig.isUseBranch()) {
      executionLogCallback.saveExecutionLog("Branch: " + gitFileConfig.getBranch());
    } else {
      executionLogCallback.saveExecutionLog("CommitId: " + gitFileConfig.getCommitId());
    }
    executionLogCallback.saveExecutionLog("\nFetching manifest files at path: "
        + (isBlank(gitFileConfig.getFilePath()) ? "." : gitFileConfig.getFilePath()));
  }

  private boolean downloadManifestFilesFromGit(K8sDelegateManifestConfig delegateManifestConfig,
      String manifestFilesDirectory, ExecutionLogCallback executionLogCallback) {
    String latestCommitSha = null;
    if (isBlank(delegateManifestConfig.getGitFileConfig().getFilePath())) {
      delegateManifestConfig.getGitFileConfig().setFilePath(StringUtils.EMPTY);
    }

    try {
      GitFileConfig gitFileConfig = delegateManifestConfig.getGitFileConfig();
      GitConfig gitConfig = delegateManifestConfig.getGitConfig();
      printGitConfigInExecutionLogs(gitConfig, gitFileConfig, executionLogCallback);
      encryptionService.decrypt(gitConfig, delegateManifestConfig.getEncryptedDataDetails(), false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          gitConfig, delegateManifestConfig.getEncryptedDataDetails());

      if (scmFetchFilesHelper.shouldUseScm(delegateManifestConfig.isOptimizedFilesFetch(), gitConfig)) {
        scmFetchFilesHelper.downloadFilesUsingScm(
            manifestFilesDirectory, gitFileConfig, gitConfig, executionLogCallback);
      } else {
        latestCommitSha = gitService.downloadFiles(
            gitConfig, gitFileConfig, manifestFilesDirectory, delegateManifestConfig.isShouldSaveManifest());
        if (delegateManifestConfig.isShouldSaveManifest()) {
          delegateManifestConfig.getGitFileConfig().setUseBranch(false);
          delegateManifestConfig.getGitFileConfig().setCommitId(latestCommitSha);
        }
      }
      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(k8sTaskHelperBase.getManifestFileNamesInLogFormat(manifestFilesDirectory));
      if (delegateManifestConfig.isShouldSaveManifest()) {
        executionLogCallback.saveExecutionLog(color(
            String.format(
                "Recorded Latest CommitId: %s and will use this Commit Id to fetch manifest from git throughout this workflow",
                latestCommitSha),
            White, Bold));
      }
      executionLogCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);

      return true;
    } catch (Exception e) {
      log.error("Failure in fetching files from git", ExceptionMessageSanitizer.sanitizeException(e));
      executionLogCallback.saveExecutionLog("Failed to download manifest files from git. "
              + ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)),
          ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  private boolean downloadManifestFilesFromCustomSource(K8sDelegateManifestConfig delegateManifestConfig,
      String manifestFilesDirectory, ExecutionLogCallback executionLogCallback) {
    try {
      customManifestService.downloadCustomSource(
          delegateManifestConfig.getCustomManifestSource(), manifestFilesDirectory, executionLogCallback);
      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(k8sTaskHelperBase.getManifestFileNamesInLogFormat(manifestFilesDirectory));
      executionLogCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    } catch (ShellScriptException e) {
      log.error("Failed to execute shell script", ExceptionMessageSanitizer.sanitizeException(e));
      executionLogCallback.saveExecutionLog(
          "Failed to execute custom manifest script. " + e.getMessage(), ERROR, CommandExecutionStatus.FAILURE);
      return false;
    } catch (IOException e) {
      log.error("Failed to get files from manifest directory", ExceptionMessageSanitizer.sanitizeException(e));
      executionLogCallback.saveExecutionLog(
          "Failed to get manifest files from custom source. " + ExceptionUtils.getMessage(e), ERROR,
          CommandExecutionStatus.FAILURE);
      return false;
    } catch (Exception e) {
      log.error("Failed to process custom manifest", ExceptionMessageSanitizer.sanitizeException(e));
      executionLogCallback.saveExecutionLog(
          "Failed to process custom manifest. " + ExceptionUtils.getMessage(e), ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  public boolean fetchManifestFilesAndWriteToDirectory(K8sDelegateManifestConfig delegateManifestConfig,
      String manifestFilesDirectory, ExecutionLogCallback executionLogCallback, long timeoutInMillis) {
    StoreType storeType = delegateManifestConfig.getManifestStoreTypes();
    switch (storeType) {
      case Local:
        return writeManifestFilesToDirectory(
            delegateManifestConfig.getManifestFiles(), manifestFilesDirectory, executionLogCallback);

      case OC_TEMPLATES:
      case Remote:
      case HelmSourceRepo:
      case KustomizeSourceRepo:
        return downloadManifestFilesFromGit(delegateManifestConfig, manifestFilesDirectory, executionLogCallback);

      case HelmChartRepo:
        return downloadFilesFromChartRepo(
            delegateManifestConfig, manifestFilesDirectory, executionLogCallback, timeoutInMillis);

      case CUSTOM:
      case CUSTOM_OPENSHIFT_TEMPLATE:
        if (delegateManifestConfig.isCustomManifestEnabled()) {
          return downloadManifestFilesFromCustomSource(
              delegateManifestConfig, manifestFilesDirectory, executionLogCallback);
        }

      // fallthrough to ignore branch if FF is not enabled
      default:
        unhandled(storeType);
    }

    return false;
  }

  public HelmChartInfo getHelmChartDetails(K8sDelegateManifestConfig delegateManifestConfig, String workingDirectory) {
    HelmChartInfo helmChartInfo = null;
    try {
      if (delegateManifestConfig != null) {
        StoreType manifestStoreType = delegateManifestConfig.getManifestStoreTypes();
        if (StoreType.HelmSourceRepo == manifestStoreType || StoreType.HelmChartRepo == manifestStoreType) {
          String chartName = Optional.ofNullable(delegateManifestConfig.getHelmChartConfigParams())
                                 .map(HelmChartConfigParams::getChartName)
                                 .orElse("");
          helmChartInfo =
              helmTaskHelper.getHelmChartInfoFromChartDirectory(Paths.get(workingDirectory, chartName).toString());
          if (delegateManifestConfig.getHelmChartConfigParams() != null) {
            helmChartInfo.setRepoUrl(
                helmHelper.getRepoUrlForHelmRepoConfig(delegateManifestConfig.getHelmChartConfigParams()));
          }
        }
      }
    } catch (IOException ex) {
      log.error("Error while fetching helm chart info", ExceptionMessageSanitizer.sanitizeException(ex));
    }

    return helmChartInfo;
  }

  public static List<ManifestFile> manifestFilesFromGitFetchFilesResult(
      GitFetchFilesResult gitFetchFilesResult, String prefixPath) {
    List<ManifestFile> manifestFiles = new ArrayList<>();

    if (isNotEmpty(gitFetchFilesResult.getFiles())) {
      List<GitFile> files = gitFetchFilesResult.getFiles();

      for (GitFile gitFile : files) {
        String filePath = K8sTaskHelperBase.getRelativePath(gitFile.getFilePath(), prefixPath);
        manifestFiles.add(ManifestFile.builder().fileName(filePath).fileContent(gitFile.getFileContent()).build());
      }
    }

    return manifestFiles;
  }

  public K8sTaskExecutionResponse getK8sTaskExecutionResponse(
      K8sTaskResponse k8sTaskResponse, CommandExecutionStatus commandExecutionStatus) {
    return K8sTaskExecutionResponse.builder()
        .k8sTaskResponse(k8sTaskResponse)
        .commandExecutionStatus(commandExecutionStatus)
        .build();
  }

  public ExecutionLogCallback getExecutionLogCallback(K8sTaskParameters request, String commandUnit) {
    return new ExecutionLogCallback(
        delegateLogService, request.getAccountId(), request.getAppId(), request.getActivityId(), commandUnit);
  }

  private boolean downloadFilesFromChartRepo(K8sDelegateManifestConfig delegateManifestConfig,
      String destinationDirectory, ExecutionLogCallback executionLogCallback, long timeoutInMillis) {
    HelmChartConfigParams helmChartConfigParams = delegateManifestConfig.getHelmChartConfigParams();

    try {
      executionLogCallback.saveExecutionLog(color(format("%nFetching files from helm chart repo"), White, Bold));
      helmTaskHelper.printHelmChartInfoInExecutionLogs(helmChartConfigParams, executionLogCallback);

      helmTaskHelper.downloadChartFiles(
          helmChartConfigParams, destinationDirectory, timeoutInMillis, delegateManifestConfig.getHelmCommandFlag());

      executionLogCallback.saveExecutionLog(color("Successfully fetched following files:", White, Bold));
      executionLogCallback.saveExecutionLog(k8sTaskHelperBase.getManifestFileNamesInLogFormat(destinationDirectory));
      executionLogCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);

      return true;
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(e)),
          ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
  }

  public List<KubernetesResourceId> getResourceIdsForDeletion(K8sDeleteTaskParameters k8sDeleteTaskParameters,
      KubernetesConfig kubernetesConfig, ExecutionLogCallback executionLogCallback) throws IOException {
    List<KubernetesResourceId> kubernetesResourceIds = k8sTaskHelperBase.fetchAllResourcesForRelease(
        k8sDeleteTaskParameters.getReleaseName(), kubernetesConfig, executionLogCallback);

    // If namespace deletion is NOT selected,remove all Namespace resources from deletion list
    if (!k8sDeleteTaskParameters.isDeleteNamespacesForRelease()) {
      kubernetesResourceIds =
          kubernetesResourceIds.stream()
              .filter(kubernetesResourceId -> !Namespace.name().equals(kubernetesResourceId.getKind()))
              .collect(toList());
    }

    return k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(kubernetesResourceIds);
  }

  public boolean restore(List<KubernetesResource> kubernetesResources, K8sClusterConfig clusterConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, K8sHandlerConfig k8sHandlerConfig,
      ExecutionLogCallback executionLogCallback) {
    try {
      executionLogCallback.saveExecutionLog("Restoring inherited resources: \n");
      executionLogCallback.saveExecutionLog(ManifestHelper.toYamlForLogs(kubernetesResources));
      k8sHandlerConfig.setKubernetesConfig(containerDeploymentDelegateHelper.getKubernetesConfig(clusterConfig, false));
      k8sHandlerConfig.setClient(
          Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath()));
      k8sHandlerConfig.setResources(kubernetesResources);
      executionLogCallback.saveExecutionLog("Done.. \n", INFO, SUCCESS);
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog("Failed to restore inherited resources: \n", ERROR, FAILURE);
      log.error("Exception while restoring inherited resources:", ExceptionMessageSanitizer.sanitizeException(e));
      return false;
    }
    return true;
  }
}
