/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.nexus.NexusHelper.getBaseUrl;
import static io.harness.nexus.NexusHelper.isSuccessful;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.delegate.beans.artifact.ArtifactFileMetadataInternal;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.nexus.model.Asset;
import io.harness.nexus.model.Nexus3ComponentResponse;
import io.harness.nexus.model.Nexus3Repository;

import software.wings.utils.RepositoryFormat;

import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class NexusThreeClientImpl {
  private static final List<String> IGNORE_EXTENSIONS = Lists.newArrayList("pom", "sha1", "sha256", "sha512", "md5");

  public NexusThreeRestClient getNexusThreeClient(NexusRequest nexusConfig) {
    return NexusHelper.getRetrofit(nexusConfig, JacksonConverterFactory.create()).create(NexusThreeRestClient.class);
  }

  public Map<String, String> getRepositories(NexusRequest nexusConfig, String repositoryFormat) throws IOException {
    log.info("Retrieving repositories");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    Response<List<Nexus3Repository>> response;
    if (nexusConfig.isHasCredentials()) {
      response =
          nexusThreeRestClient
              .listRepositories(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())))
              .execute();
    } else {
      response = nexusThreeRestClient.listRepositories().execute();
    }

    if (isSuccessful(response)) {
      if (isNotEmpty(response.body())) {
        log.info("Retrieving {} repositories success", repositoryFormat);
        final Map<String, String> repositories;
        if (repositoryFormat == null) {
          repositories =
              response.body().stream().collect(Collectors.toMap(Nexus3Repository::getName, Nexus3Repository::getName));
        } else {
          final String filterBy = repositoryFormat.equals(RepositoryFormat.maven.name()) ? "maven2" : repositoryFormat;
          repositories = response.body()
                             .stream()
                             .filter(o -> o.getFormat().equals(filterBy))
                             .collect(Collectors.toMap(Nexus3Repository::getName, Nexus3Repository::getName));
        }
        log.info("Retrieved repositories are {}", repositories.values());
        return repositories;
      } else {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the connector details - URL & credentials are correct",
            "No repositories were found for the connector",
            new InvalidArtifactServerException("Failed to fetch the repositories", WingsException.USER));
      }
    }
    log.info("No repositories found returning empty map");
    return emptyMap();
  }

  public boolean isServerValid(NexusRequest nexusConfig) throws IOException {
    log.info("Validate if nexus is running by retrieving repositories");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    Response<List<Nexus3Repository>> response;
    if (nexusConfig.isHasCredentials()) {
      response =
          nexusThreeRestClient
              .listRepositories(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())))
              .execute();
    } else {
      response = nexusThreeRestClient.listRepositories().execute();
    }
    if (response == null) {
      return false;
    }

    if (response.code() == 404) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if the Nexus URL & Nexus version are correct. Nexus URLs are different for different Nexus versions",
          "The Nexus URL or the version for the connector is incorrect",
          new InvalidArtifactServerException("Invalid Nexus connector details"));
    }
    return NexusHelper.isSuccessful(response);
  }

  public List<BuildDetailsInternal> getArtifactsVersions(
      NexusRequest nexusConfig, String repoName, Integer port, String imageName, String repoFormat) throws IOException {
    log.info("Retrieving artifacts versions");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    Response<Nexus3ComponentResponse> response;
    if (nexusConfig.isHasCredentials()) {
      response = nexusThreeRestClient
                     .search(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                         repoName, imageName, repoFormat, null)
                     .execute();
    } else {
      response = nexusThreeRestClient.search(repoName, imageName, repoFormat, null).execute();
    }

    return processComponentResponse(nexusConfig, repoName, port, imageName, repoFormat, null, response);
  }

  private List<ArtifactFileMetadataInternal> getArtifactMetadata(List<Asset> assets, String repoId) {
    List<ArtifactFileMetadataInternal> artifactFileMetadataInternals = new ArrayList<>();
    if (isEmpty(assets)) {
      return artifactFileMetadataInternals;
    }
    for (Asset item : assets) {
      String url = item.getDownloadUrl();
      String artifactFileName = url.substring(url.lastIndexOf('/') + 1);
      String imagePath = item.getPath();
      if (IGNORE_EXTENSIONS.stream().anyMatch(artifactFileName::endsWith)) {
        continue;
      }
      if (!item.getRepository().equals(repoId)) {
        url = url.replace(item.getRepository(), repoId);
      }
      artifactFileMetadataInternals.add(
          ArtifactFileMetadataInternal.builder().fileName(artifactFileName).imagePath(imagePath).url(url).build());
    }
    return artifactFileMetadataInternals;
  }

  private String getArtifactDownloadUrl(
      List<ArtifactFileMetadataInternal> artifactFileMetadataInternals, String extension, String classifier) {
    String defaultUrl = artifactFileMetadataInternals.get(0).getUrl();
    String url = null;
    if (StringUtils.isNoneBlank(extension, classifier)) {
      url = artifactFileMetadataInternals.stream()
                .filter(meta -> meta.getFileName().endsWith(extension) && meta.getFileName().contains(classifier))
                .map(ArtifactFileMetadataInternal::getUrl)
                .findFirst()
                .orElse(null);
    }
    return StringUtils.isNotBlank(url) ? url : defaultUrl;
  }

  private String getArtifactImagePath(
      List<ArtifactFileMetadataInternal> artifactFileMetadataInternals, String extension, String classifier) {
    String defaultImagePath = artifactFileMetadataInternals.get(0).getImagePath();
    String imagePath = null;
    if (StringUtils.isNoneBlank(extension, classifier)) {
      imagePath = artifactFileMetadataInternals.stream()
                      .filter(meta -> meta.getFileName().endsWith(extension) && meta.getFileName().contains(classifier))
                      .map(ArtifactFileMetadataInternal::getImagePath)
                      .findFirst()
                      .orElse(null);
    }
    return StringUtils.isNotBlank(imagePath) ? imagePath : defaultImagePath;
  }

  public List<BuildDetailsInternal> getBuildDetails(NexusRequest nexusConfig, String repository, Integer port,
      String imageName, String repositoryFormat, String tag) throws IOException {
    log.info("Retrieving artifact details");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    Response<Nexus3ComponentResponse> response;
    if (nexusConfig.isHasCredentials()) {
      response = nexusThreeRestClient
                     .getArtifact(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                         repository, imageName, repositoryFormat, tag, null)
                     .execute();
    } else {
      response = nexusThreeRestClient.getArtifact(repository, imageName, repositoryFormat, tag, null, null).execute();
    }

    return processComponentResponse(nexusConfig, repository, port, imageName, repositoryFormat, tag, response);
  }

  private List<BuildDetailsInternal> processComponentResponse(NexusRequest nexusConfig, String repository, Integer port,
      String imageName, String repositoryFormat, String tag, Response<Nexus3ComponentResponse> response) {
    if (isSuccessful(response)) {
      if (response.body() != null) {
        List<BuildDetailsInternal> components = new ArrayList<>();
        if (isNotEmpty(response.body().getItems())) {
          for (Nexus3ComponentResponse.Component component : response.body().getItems()) {
            List<ArtifactFileMetadataInternal> artifactFileMetadataInternals =
                getArtifactMetadata(component.getAssets(), repository);
            String versionDownloadUrl = null;
            String imagePath = null;
            String actualTag = isEmpty(tag) ? component.getVersion() : tag;
            if (isNotEmpty(artifactFileMetadataInternals)) {
              versionDownloadUrl = getArtifactDownloadUrl(artifactFileMetadataInternals, null, null);
              imagePath = getArtifactImagePath(artifactFileMetadataInternals, null, null);
            }

            String tagUrl = getBaseUrl(nexusConfig) + repository + "/" + imageName + "/";
            String repoName = ArtifactUtilities.getNexusRepositoryName(
                nexusConfig.getNexusUrl(), String.valueOf(port), nexusConfig.getDockerRepositoryServer(), imageName);
            log.info("Retrieving docker tags for repository {} imageName {} ", repository, imageName);
            String domainName = Http.getDomainWithPort(nexusConfig.getNexusUrl());
            Map<String, String> metadata = new HashMap<>();
            metadata.put(ArtifactMetadataKeys.IMAGE, repoName + ":" + actualTag);
            metadata.put(ArtifactMetadataKeys.TAG, actualTag);

            BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder()
                                                            .number(component.getVersion())
                                                            .metadata(metadata)
                                                            .buildUrl(versionDownloadUrl)
                                                            .artifactPath(imagePath)
                                                            .build();

            components.add(buildDetailsInternal);
          }
        } else {
          log.info("No components found returning empty map");
        }
        return components;
      } else {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the connector details - URL & credentials are correct",
            "No components were found for the connector",
            new InvalidArtifactServerException("Failed to fetch the components", WingsException.USER));
      }
    }

    log.error(response.errorBody().toString());
    throw NestedExceptionUtils.hintWithExplanationException(
        "Request to get artifact details has failed with code (" + response.code() + ")",
        response.errorBody().toString().length() < 500 ? "Error response: " + response.errorBody().toString()
                                                       : "Check logs for more details.",
        new InvalidArtifactServerException("Failed to fetch the components", WingsException.USER));
  }
}
