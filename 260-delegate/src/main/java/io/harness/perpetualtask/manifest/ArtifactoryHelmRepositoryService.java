/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.manifest;

import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;

import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.delegatetasks.ExceptionMessageSanitizer;
import software.wings.helpers.ext.artifactory.ArtifactoryService;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ArtifactoryHelmRepositoryService implements ManifestRepositoryService {
  @Inject ArtifactoryService artifactoryService;
  @Inject EncryptionService encryptionService;

  @Override
  public List<HelmChart> collectManifests(ManifestCollectionParams params) throws Exception {
    HelmChartCollectionParams helmChartCollectionParams = (HelmChartCollectionParams) params;
    HttpHelmRepoConfig helmRepoConfig =
        (HttpHelmRepoConfig) helmChartCollectionParams.getHelmChartConfigParams().getHelmRepoConfig();

    String baseUrl = helmRepoConfig.getChartRepoUrl().split("/artifactory/", 2)[0] + "/artifactory/";
    String repoName = helmRepoConfig.getChartRepoUrl().split("/artifactory/", 2)[1];
    if (repoName.endsWith("/")) {
      repoName = repoName.substring(0, repoName.length() - 1);
    }
    encryptionService.decrypt(
        helmRepoConfig, helmChartCollectionParams.getHelmChartConfigParams().getEncryptedDataDetails(), false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        helmRepoConfig, helmChartCollectionParams.getHelmChartConfigParams().getEncryptedDataDetails());
    ArtifactoryConfigRequest request =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(baseUrl)
            .username(helmRepoConfig.getUsername())
            .password(helmRepoConfig.getPassword())
            .hasCredentials(helmRepoConfig.getUsername() != null || helmRepoConfig.getPassword() != null)
            .build();

    artifactoryService.checkIfValidHelmRepository(request, repoName);

    List<BuildDetails> buildDetails = artifactoryService.getFilePaths(
        request, repoName, helmChartCollectionParams.getHelmChartConfigParams().getChartName(), "helm", 50);
    if (buildDetails == null) {
      return new ArrayList<>();
    }
    return buildDetails.stream()
        .map(build
            -> HelmChart.builder()
                   .appId(helmChartCollectionParams.getAppId())
                   .accountId(helmChartCollectionParams.getAccountId())
                   .applicationManifestId(helmChartCollectionParams.getAppManifestId())
                   .serviceId(helmChartCollectionParams.getServiceId())
                   .name(helmChartCollectionParams.getHelmChartConfigParams().getChartName())
                   .version(build.getNumber())
                   .displayName(build.getUiDisplayName())
                   .build())
        .collect(Collectors.toList());
  }

  // No cleanup action needed after collection
  @Override
  public void cleanup(ManifestCollectionParams params) throws Exception {}
}
