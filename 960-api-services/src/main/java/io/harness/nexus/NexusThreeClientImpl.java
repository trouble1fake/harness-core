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
import static io.harness.nexus.NexusHelper.isSuccessful;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
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
  private static final int MAX_PAGES = 10;
  private static final List<String> IGNORE_EXTENSIONS = Lists.newArrayList("pom", "sha1", "sha256", "sha512", "md5");

  public NexusThreeRestClient getNexusThreeClient(NexusRequest nexusConfig) {
    return NexusHelper.getRetrofit(nexusConfig, JacksonConverterFactory.create()).create(NexusThreeRestClient.class);
  }

  //  public List<String> getPackageNames(
  //      NexusRequest nexusConfig, String repository, String repositoryFormat, List<String> images) throws IOException
  //      {
  //    log.info("Retrieving packageNames for repository {} repositoryFormat {}", repository, repositoryFormat);
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<Nexus3ComponentResponse> response;
  //    boolean hasMoreResults = true;
  //    String continuationToken = null;
  //    while (hasMoreResults) {
  //      hasMoreResults = false;
  //      if (nexusConfig.isHasCredentials()) {
  //        response = nexusThreeRestClient
  //                       .search(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
  //                           repository, continuationToken)
  //                       .execute();
  //      } else {
  //        response = nexusThreeRestClient.search(repository, continuationToken).execute();
  //      }
  //      Set<String> packages = null;
  //      if (isSuccessful(response)) {
  //        if (response.body() != null) {
  //          if (isNotEmpty(response.body().getItems())) {
  //            if (repositoryFormat.equals(RepositoryFormat.nuget.name())
  //                || repositoryFormat.equals(RepositoryFormat.npm.name())) {
  //              packages = response.body()
  //                             .getItems()
  //                             .stream()
  //                             .map(Nexus3ComponentResponse.Component::getName)
  //                             .collect(Collectors.toSet());
  //            }
  //            if (isNotEmpty(packages)) {
  //              for (String p : packages) {
  //                if (!images.contains(p)) {
  //                  images.add(p);
  //                }
  //              }
  //            }
  //          }
  //          if (response.body().getContinuationToken() != null) {
  //            continuationToken = response.body().getContinuationToken();
  //            hasMoreResults = true;
  //          }
  //        } else {
  //          throw new InvalidArtifactServerException("Failed to fetch the package names", WingsException.USER);
  //        }
  //      }
  //    }
  //    return images;
  //  }
  //
  //  public List<String> getGroupIds(
  //      NexusRequest nexusConfig, String repository, String repositoryFormat, List<String> images) throws IOException
  //      {
  //    log.info("Retrieving groups for repository {} repositoryFormat {}", repository, repositoryFormat);
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<Nexus3ComponentResponse> response;
  //    boolean hasMoreResults = true;
  //    String continuationToken = null;
  //    while (hasMoreResults) {
  //      hasMoreResults = false;
  //      if (nexusConfig.isHasCredentials()) {
  //        response = nexusThreeRestClient
  //                       .search(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
  //                           repository, continuationToken)
  //                       .execute();
  //      } else {
  //        response = nexusThreeRestClient.search(repository, continuationToken).execute();
  //      }
  //      Set<String> packages = null;
  //      if (isSuccessful(response)) {
  //        if (response.body() != null) {
  //          if (isNotEmpty(response.body().getItems())) {
  //            if (repositoryFormat.equals(RepositoryFormat.maven.name())) {
  //              packages = response.body()
  //                             .getItems()
  //                             .stream()
  //                             .map(Nexus3ComponentResponse.Component::getGroup)
  //                             .collect(Collectors.toSet());
  //            }
  //            if (isNotEmpty(packages)) {
  //              for (String p : packages) {
  //                if (!images.contains(p)) {
  //                  images.add(p);
  //                }
  //              }
  //            }
  //          }
  //          if (response.body().getContinuationToken() != null) {
  //            continuationToken = response.body().getContinuationToken();
  //            hasMoreResults = true;
  //          }
  //        } else {
  //          throw new InvalidArtifactServerException("Failed to fetch the groupIds", WingsException.USER);
  //        }
  //      }
  //    }
  //    return images;
  //  }
  //
  //  public List<String> getDockerImages(NexusRequest nexusConfig, String repository, List<String> images)
  //      throws IOException {
  //    log.info("Retrieving docker images for repository {} from url {}", repository, nexusConfig.getNexusUrl());
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<DockerImageResponse> response;
  //    if (nexusConfig.isHasCredentials()) {
  //      response =
  //          nexusThreeRestClient
  //              .getDockerImages(
  //                  Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repository)
  //              .execute();
  //    } else {
  //      response = nexusThreeRestClient.getDockerImages(repository).execute();
  //    }
  //    if (isSuccessful(response)) {
  //      if (response.body() != null && response.body().getRepositories() != null) {
  //        images.addAll(response.body().getRepositories().stream().collect(toList()));
  //        log.info("Retrieving docker images for repository {} from url {} success. Images are {}", repository,
  //            nexusConfig.getNexusUrl(), images);
  //      }
  //    } else {
  //      log.warn("Failed to fetch the docker images as request is not success");
  //      throw new InvalidArtifactServerException("Failed to fetch the docker images", WingsException.USER);
  //    }
  //    log.info("No images found for repository {}", repository);
  //    return images;
  //  }
  //
  //  public List<BuildDetailsInternal> getPackageVersions(
  //      NexusRequest nexusConfig, String repositoryName, String packageName) throws IOException {
  //    log.info("Retrieving package versions for repository {} package {} ", repositoryName, packageName);
  //    List<String> versions = new ArrayList<>();
  //    Map<String, Asset> versionToArtifactUrls = new HashMap<>();
  //    Map<String, List<ArtifactFileMetadata>> versionToArtifactDownloadUrls = new HashMap<>();
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<Nexus3ComponentResponse> response;
  //    boolean hasMoreResults = true;
  //    String continuationToken = null;
  //    while (hasMoreResults) {
  //      hasMoreResults = false;
  //      if (nexusConfig.isHasCredentials()) {
  //        response =
  //            nexusThreeRestClient
  //                .getPackageVersions(Credentials.basic(nexusConfig.getUsername(), new
  //                String(nexusConfig.getPassword())),
  //                    repositoryName, packageName, continuationToken)
  //                .execute();
  //
  //      } else {
  //        response = nexusThreeRestClient.getPackageVersions(repositoryName, packageName,
  //        continuationToken).execute();
  //      }
  //
  //      if (isSuccessful(response)) {
  //        if (response.body() != null) {
  //          if (isNotEmpty(response.body().getItems())) {
  //            for (Nexus3ComponentResponse.Component component : response.body().getItems()) {
  //              String version = component.getVersion();
  //              versions.add(version); // todo: add limit if results are returned in descending order of lastUpdatedTs
  //
  //              if (isNotEmpty(component.getAssets())) {
  //                Asset asset = component.getAssets().get(0);
  //                if (!asset.getRepository().equals(repositoryName)) {
  //                  // For nuget, Eg. repository/nuget-hosted-group-repo/NuGet.Sample.Package/1.0.0.0
  //                  // For npm,  Eg. repository/harness-npm-group/npm-app1/-/npm-app1-1.0.0.tgz
  //                  String artifactUrl = asset.getDownloadUrl().replace(asset.getRepository(), repositoryName);
  //                  // Update the asset with modified URL
  //                  asset.setDownloadUrl(artifactUrl);
  //                }
  //                versionToArtifactUrls.put(version, asset);
  //              }
  //              // for each version - get all assets and store download urls in metadata
  //              versionToArtifactDownloadUrls.put(version, getDownloadUrlsForPackageVersion(component));
  //            }
  //          }
  //          if (response.body().getContinuationToken() != null) {
  //            continuationToken = response.body().getContinuationToken();
  //            hasMoreResults = true;
  //          }
  //        }
  //      } else {
  //        throw new InvalidArtifactServerException(
  //            "Failed to fetch the versions for package [" + packageName + "]", WingsException.USER);
  //      }
  //    }
  //    log.info("Versions come from nexus server {}", versions);
  //    versions = versions.stream().sorted(new AlphanumComparator()).collect(toList());
  //    log.info("After sorting alphanumerically versions {}", versions);
  //
  //    return versions.stream()
  //        .map(version -> {
  //          Map<String, String> metadata = new HashMap<>();
  //          metadata.put(Artifact.ArtifactMetadataKeys.repositoryName, repositoryName);
  //          metadata.put(Artifact.ArtifactMetadataKeys.nexusPackageName, packageName);
  //          metadata.put(Artifact.ArtifactMetadataKeys.version, version);
  //          String url = null;
  //          if (versionToArtifactUrls.get(version) != null) {
  //            url = (versionToArtifactUrls.get(version)).getDownloadUrl();
  //            metadata.put(Artifact.ArtifactMetadataKeys.url, url);
  //            metadata.put(Artifact.ArtifactMetadataKeys.artifactPath,
  //            (versionToArtifactUrls.get(version)).getPath());
  //          }
  //          return BuildDetailsInternal.builder()
  //              .number(version)
  //              .revision(version)
  //              .buildUrl(url)
  //              .metadata(metadata)
  //              .uiDisplayName("Version# " + version)
  //              .artifactDownloadMetadata(versionToArtifactDownloadUrls.get(version))
  //              .build();
  //        })
  //        .collect(toList());
  //  }
  //
  //  private List<ArtifactFileMetadata> getDownloadUrlsForPackageVersion(Nexus3ComponentResponse.Component component) {
  //    List<Asset> assets = component.getAssets();
  //    List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
  //    if (isNotEmpty(assets)) {
  //      for (Asset asset : assets) {
  //        String artifactUrl = asset.getDownloadUrl();
  //        String artifactName;
  //        if (RepositoryFormat.nuget.name().equals(component.getFormat())) {
  //          artifactName = component.getName() + "-" + component.getVersion() + ".nupkg";
  //        } else {
  //          artifactName = artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1);
  //        }
  //        if (IGNORE_EXTENSIONS.stream().anyMatch(artifactName::endsWith)) {
  //          continue;
  //        }
  //        artifactFileMetadata.add(ArtifactFileMetadata.builder().fileName(artifactName).url(artifactUrl).build());
  //      }
  //    }
  //    return artifactFileMetadata;
  //  }
  //
  //  public List<BuildDetailsInternal> getDockerTags(
  //      NexusRequest nexusConfig, String repositoryName, String imgName, int dockerPort) throws IOException {
  //    String repoKey = repositoryName;
  //    String imageName = imgName;
  //    String repoName = ArtifactUtilities.getNexusRepositoryName(
  //        nexusConfig.getNexusUrl(), String.valueOf(dockerPort), nexusConfig.getNexusUrl(), imageName);
  //    log.info("Retrieving docker tags for repository {} imageName {} ", repoKey, imageName);
  //    List<BuildDetailsInternal> buildDetailsInternals = new ArrayList<>();
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<DockerImageTagResponse> response;
  //
  //    if (nexusConfig.isHasCredentials()) {
  //      response = nexusThreeRestClient
  //                     .getDockerTags(Credentials.basic(nexusConfig.getUsername(), new
  //                     String(nexusConfig.getPassword())),
  //                         repoKey, imageName)
  //                     .execute();
  //
  //    } else {
  //      response = nexusThreeRestClient.getDockerTags(repoKey, imageName).execute();
  //    }
  //
  //    if (isSuccessful(response)) {
  //      if (response.body() != null && response.body().getTags() != null) {
  //        buildDetailsInternals = response.body()
  //                                    .getTags()
  //                                    .stream()
  //                                    .map(tag -> {
  //                                      Map<String, String> metadata = new HashMap();
  //                                      metadata.put(Artifact.ArtifactMetadataKeys.image, repoName + ":" + tag);
  //                                      metadata.put(Artifact.ArtifactMetadataKeys.tag, tag);
  //                                      return BuildDetailsInternal.builder()
  //                                          .number(tag)
  //                                          .metadata(metadata)
  //                                          .uiDisplayName("Tag# " + tag)
  //                                          .build();
  //                                    })
  //                                    .collect(toList());
  //        // Sorting at build tag for docker artifacts.
  //        return buildDetailsInternals.stream().sorted(new
  //        BuildDetailsInternalComparatorAscending()).collect(toList());
  //      }
  //    } else {
  //      throw new InvalidArtifactServerException(
  //          "Failed to fetch the docker tags of image [" + imageName + "]", WingsException.USER);
  //    }
  //    log.info("No tags found for image name {}", imageName);
  //    return buildDetailsInternals;
  //  }
  //
  //  public Pair<String, InputStream> downloadArtifact(NexusRequest nexusConfig,
  //      ArtifactStreamAttributes artifactStreamAttributes, Map<String, String> artifactMetadata, String delegateId,
  //      String taskId, String accountId, ListNotifyResponseData notifyResponseData) throws IOException {
  //    String repositoryFormat = artifactStreamAttributes.getRepositoryFormat();
  //    if (repositoryFormat != null) {
  //      if (repositoryFormat.equals(RepositoryFormat.nuget.name())
  //          || repositoryFormat.equals(RepositoryFormat.npm.name())) {
  //        final String version = artifactMetadata.get(Artifact.ArtifactMetadataKeys.buildNo);
  //        final String packageName = artifactMetadata.get(Artifact.ArtifactMetadataKeys.nexusPackageName);
  //        final String repoName = artifactMetadata.get(Artifact.ArtifactMetadataKeys.repositoryName);
  //        log.info("Downloading version {} of package {} from repository {}", version, packageName, repoName);
  //        NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //        Response<Nexus3AssetResponse> response;
  //        if (nexusConfig.isHasCredentials()) {
  //          response = nexusThreeRestClient
  //                         .getAsset(Credentials.basic(nexusConfig.getUsername(), new
  //                         String(nexusConfig.getPassword())),
  //                             repoName, packageName, version)
  //                         .execute();
  //        } else {
  //          response = nexusThreeRestClient.getAsset(repoName, packageName, version).execute();
  //        }
  //
  //        if (isSuccessful(response)) {
  //          if (response.body() != null) {
  //            if (isNotEmpty(response.body().getItems())) {
  //              response.body().getItems().forEach(item -> {
  //                String url = item.getDownloadUrl();
  //                String artifactName = url.substring(url.lastIndexOf('/') + 1);
  //                if (IGNORE_EXTENSIONS.stream().anyMatch(artifactName::endsWith)) {
  //                  return;
  //                }
  //                downloadArtifactByUrl(
  //                    nexusConfig, delegateId, taskId, accountId, notifyResponseData, artifactName, url);
  //              });
  //            }
  //          } else {
  //            throw new InvalidArtifactServerException(
  //                "Unable to find package [" + packageName + "] version [" + version + "]", WingsException.USER);
  //          }
  //        } else {
  //          throw new InvalidArtifactServerException(
  //              "Failed to download package [" + packageName + "] version [" + version + "]", WingsException.USER);
  //        }
  //      } else if (repositoryFormat.equals(RepositoryFormat.maven.name())) {
  //        final String version = artifactMetadata.get(Artifact.ArtifactMetadataKeys.buildNo);
  //        final String groupId = artifactStreamAttributes.getGroupId();
  //        final String artifactName = artifactStreamAttributes.getArtifactName();
  //        final String repoName = artifactStreamAttributes.getJobName();
  //        final String extension = artifactStreamAttributes.getExtension();
  //        final String classifier = artifactStreamAttributes.getClassifier();
  //
  //        log.info("Downloading version {} of groupId: {} artifactId: {} from repository {}", version, groupId,
  //            artifactName, repoName);
  //        NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //        Response<Nexus3AssetResponse> response = getNexus3MavenAssets(
  //            nexusConfig, version, groupId, artifactName, repoName, extension, classifier, nexusThreeRestClient);
  //
  //        if (isSuccessful(response)) {
  //          if (response.body() != null) {
  //            if (isNotEmpty(response.body().getItems())) {
  //              response.body().getItems().forEach(item -> {
  //                String url = item.getDownloadUrl();
  //                String artifactFileName = url.substring(url.lastIndexOf('/') + 1);
  //                if (IGNORE_EXTENSIONS.stream().anyMatch(artifactName::endsWith)) {
  //                  return;
  //                }
  //                downloadArtifactByUrl(
  //                    nexusConfig, delegateId, taskId, accountId, notifyResponseData, artifactFileName, url);
  //              });
  //            }
  //          } else {
  //            throw new InvalidArtifactServerException("Unable to find artifact for groupId [" + groupId
  //                    + "] artifactId [" + artifactName + "] version [" + version + "]",
  //                WingsException.USER);
  //          }
  //        } else {
  //          throw new InvalidArtifactServerException("Failed to download artifact for groupId [" + groupId
  //                  + "] artifactId [" + artifactName + "]version [" + version + "]",
  //              WingsException.USER);
  //        }
  //      }
  //    }
  //    return null;
  //  }
  //
  //  @SuppressWarnings({"squid:S00107"})
  //  private Response<Nexus3AssetResponse> getNexus3MavenAssets(NexusRequest nexusConfig, String version, String
  //  groupId,
  //      String artifactName, String repoName, String extension, String classifier,
  //      NexusThreeRestClient nexusThreeRestClient) throws IOException {
  //    Response<Nexus3AssetResponse> response;
  //    if (nexusConfig.isHasCredentials()) {
  //      if (isNotEmpty(extension) || isNotEmpty(classifier)) {
  //        response = nexusThreeRestClient
  //                       .getMavenAssetWithExtensionAndClassifier(
  //                           Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
  //                           repoName, groupId, artifactName, version, extension, classifier)
  //                       .execute();
  //      } else {
  //        response =
  //            nexusThreeRestClient
  //                .getMavenAsset(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
  //                    repoName, groupId, artifactName, version)
  //                .execute();
  //      }
  //    } else {
  //      if (isNotEmpty(extension) || isNotEmpty(classifier)) {
  //        response = nexusThreeRestClient
  //                       .getMavenAssetWithExtensionAndClassifier(
  //                           repoName, groupId, artifactName, version, extension, classifier)
  //                       .execute();
  //      } else {
  //        response = nexusThreeRestClient.getMavenAsset(repoName, groupId, artifactName, version).execute();
  //      }
  //    }
  //    return response;
  //  }
  //
  //  private void downloadArtifactByUrl(NexusRequest nexusConfig, String delegateId, String taskId, String accountId,
  //      ListNotifyResponseData notifyResponseData, String artifactName, String artifactUrl) {
  //    Pair<String, InputStream> pair = downloadArtifactByUrl(nexusConfig, artifactName, artifactUrl);
  //    try {
  //      artifactCollectionTaskHelper.addDataToResponse(
  //          pair, artifactUrl, notifyResponseData, delegateId, taskId, accountId);
  //    } catch (IOException e) {
  //      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
  //    }
  //  }
  //
  //  public List<String> getArtifactNames(NexusRequest nexusConfig, String repoId, String path) throws IOException {
  //    log.info("Retrieving Artifact Names");
  //    List<String> artifactNames = new ArrayList<>();
  //    log.info("Retrieving artifact names for repository {}", repoId);
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<Nexus3ComponentResponse> response;
  //    boolean hasMoreResults = true;
  //    String continuationToken = null;
  //    while (hasMoreResults) {
  //      hasMoreResults = false;
  //      if (nexusConfig.isHasCredentials()) {
  //        response =
  //            nexusThreeRestClient
  //                .getArtifactNames(Credentials.basic(nexusConfig.getUsername(), new
  //                String(nexusConfig.getPassword())),
  //                    repoId, path, continuationToken)
  //                .execute();
  //      } else {
  //        response = nexusThreeRestClient.getArtifactNames(repoId, path, continuationToken).execute();
  //      }
  //      Set<String> packages = null;
  //      if (isSuccessful(response)) {
  //        if (response.body() != null) {
  //          if (isNotEmpty(response.body().getItems())) {
  //            packages = response.body()
  //                           .getItems()
  //                           .stream()
  //                           .map(Nexus3ComponentResponse.Component::getName)
  //                           .collect(Collectors.toSet());
  //          }
  //          if (isNotEmpty(packages)) {
  //            for (String p : packages) {
  //              if (!artifactNames.contains(p)) {
  //                artifactNames.add(p);
  //              }
  //            }
  //          }
  //          if (response.body().getContinuationToken() != null) {
  //            continuationToken = response.body().getContinuationToken();
  //            hasMoreResults = true;
  //          }
  //        }
  //      } else {
  //        throw new InvalidArtifactServerException("Failed to fetch the groupIds", WingsException.USER);
  //      }
  //    }
  //    log.info("Retrieving Artifact Names success");
  //    return artifactNames;
  //  }
  //
  //  public List<BuildDetailsInternal> getVersions(NexusRequest nexusConfig, String repoId, String groupId, String
  //  artifactName,
  //      String extension, String classifier) throws IOException {
  //    log.info("Retrieving versions for repoId {} groupId {} and artifactName {}", repoId, groupId, artifactName);
  //    List<String> versions = new ArrayList<>();
  //    Map<String, String> versionToArtifactUrls = new HashMap<>();
  //    Map<String, List<ArtifactFileMetadata>> versionToArtifactDownloadUrls = new HashMap<>();
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<Nexus3ComponentResponse> response;
  //    boolean hasMoreResults = true;
  //    String continuationToken = null;
  //    int page = 0;
  //    while (hasMoreResults && page < MAX_PAGES) {
  //      page++;
  //      hasMoreResults = false;
  //      if (nexusConfig.isHasCredentials()) {
  //        response = nexusThreeRestClient
  //                       .getArtifactVersions(
  //                           Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
  //                           repoId, groupId, artifactName, continuationToken)
  //                       .execute();
  //      } else {
  //        response = nexusThreeRestClient.getArtifactVersions(repoId, groupId, artifactName,
  //        continuationToken).execute();
  //      }
  //      if (isSuccessful(response)) {
  //        if (response.body() != null) {
  //          if (isNotEmpty(response.body().getItems())) {
  //            for (Nexus3ComponentResponse.Component component : response.body().getItems()) {
  //              String version = component.getVersion();
  //              versions.add(version);
  //              List<ArtifactFileMetadata> artifactFileMetadata = getArtifactMetadata(component.getAssets(), repoId);
  //
  //              if (isNotEmpty(artifactFileMetadata)) {
  //                versionToArtifactUrls.put(version, getArtifactDownloadUrl(artifactFileMetadata, extension,
  //                classifier));
  //              }
  //              versionToArtifactDownloadUrls.put(version, artifactFileMetadata);
  //            }
  //          }
  //          if (response.body().getContinuationToken() != null) {
  //            continuationToken = response.body().getContinuationToken();
  //            hasMoreResults = true;
  //          }
  //        }
  //      } else {
  //        throw new InvalidArtifactServerException(
  //            "Failed to fetch the versions for groupId [" + groupId + "] and artifactId [" + artifactName + "]",
  //            WingsException.USER);
  //      }
  //    }
  //    return nexusHelper.constructBuildDetails(repoId, groupId, artifactName, versions, versionToArtifactUrls,
  //        versionToArtifactDownloadUrls, extension, classifier);
  //  }
  //
  //  private String getArtifactDownloadUrl(
  //      List<ArtifactFileMetadata> artifactFileMetadata, String extension, String classifier) {
  //    String defaultUrl = artifactFileMetadata.get(0).getUrl();
  //    String url = null;
  //    if (StringUtils.isNoneBlank(extension, classifier)) {
  //      url = artifactFileMetadata.stream()
  //                .filter(meta -> meta.getFileName().endsWith(extension) && meta.getFileName().contains(classifier))
  //                .map(ArtifactFileMetadata::getUrl)
  //                .findFirst()
  //                .orElse(null);
  //    }
  //    return StringUtils.isNotBlank(url) ? url : defaultUrl;
  //  }
  //
  //  private List<ArtifactFileMetadata> getArtifactMetadata(List<Asset> assets, String repoId) {
  //    List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
  //    if (isEmpty(assets)) {
  //      return artifactFileMetadata;
  //    }
  //    for (Asset item : assets) {
  //      String url = item.getDownloadUrl();
  //      String artifactFileName = url.substring(url.lastIndexOf('/') + 1);
  //      if (IGNORE_EXTENSIONS.stream().anyMatch(artifactFileName::endsWith)) {
  //        continue;
  //      }
  //      if (!item.getRepository().equals(repoId)) {
  //        url = url.replace(item.getRepository(), repoId);
  //      }
  //      artifactFileMetadata.add(ArtifactFileMetadata.builder().fileName(artifactFileName).url(url).build());
  //    }
  //    return artifactFileMetadata;
  //  }
  //
  //  public boolean existsVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
  //      String extension, String classifier) throws IOException {
  //    log.info("Retrieving versions for repoId {} groupId {} and artifactName {}", repoId, groupId, artifactName);
  //    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
  //    Response<Nexus3ComponentResponse> response;
  //    boolean hasMoreResults = true;
  //    String continuationToken = null;
  //    int page = 0;
  //    while (hasMoreResults && page < MAX_PAGES) {
  //      page++;
  //      hasMoreResults = false;
  //      if (nexusConfig.isHasCredentials()) {
  //        response = nexusThreeRestClient
  //                       .getArtifactVersionsWithExtensionAndClassifier(
  //                           Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
  //                           repoId, groupId, artifactName, extension, classifier, continuationToken)
  //                       .execute();
  //      } else {
  //        response = nexusThreeRestClient
  //                       .getArtifactVersionsWithExtensionAndClassifier(
  //                           repoId, groupId, artifactName, extension, classifier, continuationToken)
  //                       .execute();
  //      }
  //      if (isSuccessful(response) && response.body() != null) {
  //        if (isEmpty(response.body().getItems())) {
  //          throw new ArtifactServerException(
  //              "No versions found matching the provided extension/ classifier", null, WingsException.USER);
  //        }
  //        if (response.body().getContinuationToken() != null) {
  //          continuationToken = response.body().getContinuationToken();
  //          hasMoreResults = true;
  //        }
  //      }
  //    }
  //    return true;
  //  }
  //
  //  @SuppressWarnings({"squid:S3510"})
  //  public Pair<String, InputStream> downloadArtifactByUrl(
  //      NexusRequest nexusConfig, String artifactName, String artifactUrl) {
  //    try {
  //      if (nexusConfig.isHasCredentials()) {
  //        Authenticator.setDefault(new NexusThreeClientImpl().MyAuthenticator(
  //            nexusConfig.getUsername(), new String(nexusConfig.getPassword())));
  //      }
  //      URL url = new URL(artifactUrl);
  //      URLConnection conn = url.openConnection();
  //      if (conn instanceof HttpsURLConnection) {
  //        HttpsURLConnection conn1 = (HttpsURLConnection) url.openConnection();
  //        conn1.setHostnameVerifier((hostname, session) -> true);
  //        conn1.setSSLSocketFactory(Http.getSslContext().getSocketFactory());
  //        return ImmutablePair.of(artifactName, conn1.getInputStream());
  //      } else {
  //        return ImmutablePair.of(artifactName, conn.getInputStream());
  //      }
  //    } catch (IOException ex) {
  //      throw new InvalidRequestException(ExceptionUtils.getMessage(ex), ex);
  //    }
  //  }
  //
  //  public long getFileSize(NexusRequest nexusConfig, String artifactName, String artifactUrl) {
  //    log.info("Getting file size for artifact at path {}", artifactUrl);
  //    long size;
  //    Pair<String, InputStream> pair = downloadArtifactByUrl(nexusConfig, artifactName, artifactUrl);
  //    if (pair == null) {
  //      throw new InvalidArtifactServerException(format("Failed to get file size for artifact: [%s]", artifactUrl));
  //    }
  //    try {
  //      size = StreamUtils.getInputStreamSize(pair.getRight());
  //      pair.getRight().close();
  //    } catch (IOException e) {
  //      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
  //    }
  //    log.info(format("Computed file size: [%d] bytes for artifact Path: [%s]", size, artifactUrl));
  //    return size;
  //  }
  //
  //  static class MyAuthenticator extends Authenticator {
  //    private String username, password;
  //
  //    MyAuthenticator(String user, String pass) {
  //      username = user;
  //      password = pass;
  //    }
  //
  //    @Override
  //    protected PasswordAuthentication getPasswordAuthentication() {
  //      return new PasswordAuthentication(username, password.toCharArray());
  //    }
  //  }

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

    List<BuildDetailsInternal> versions = new ArrayList<>();
    if (isSuccessful(response)) {
      if (response.body() != null) {
        if (isNotEmpty(response.body().getItems())) {
          for (Nexus3ComponentResponse.Component component : response.body().getItems()) {
            List<ArtifactFileMetadataInternal> artifactFileMetadataInternals =
                getArtifactMetadata(component.getAssets(), repoName);
            String versionDownloadUrl = null;
            String imagePath = null;
            if (isNotEmpty(artifactFileMetadataInternals)) {
              versionDownloadUrl = getArtifactDownloadUrl(artifactFileMetadataInternals, null, null);
              imagePath = getArtifactImagePath(artifactFileMetadataInternals, null, null);
            }

            BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder()
                                                            .number(component.getVersion())
                                                            .buildUrl(versionDownloadUrl)
                                                            .artifactPath(imagePath)
                                                            .build();

            versions.add(buildDetailsInternal);
          }
          //          response.body().getItems().stream().forEach(component ->
          //          versions.add(BuildDetailsInternal.builder()
          //                  .number(component.getVersion())
          //                  .artifactPath(component.getAssets().f)
          //                  .uiDisplayName(component.getName())
          //                  .build()
          //          ));
          //        log.info("Retrieving {} components success", repositoryFormat);
          //        final Map<String, String> repositories;
          //        if (repositoryFormat == null) {
          //          repositories =
          //                  response.body().stream().collect(Collectors.toMap(Nexus3Repository::getName,
          //                  Nexus3Repository::getName));
          //        } else {
          //          final String filterBy = repositoryFormat.equals(RepositoryFormat.maven.name()) ? "maven2" :
          //          repositoryFormat; repositories = response.body()
          //                  .stream()
          //                  .filter(o -> o.getFormat().equals(filterBy))
          //                  .collect(Collectors.toMap(Nexus3Repository::getName, Nexus3Repository::getName));
          //        }
          //        log.info("Retrieved repositories are {}", repositories.values());
          //        return repositories;
        }
      } else {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the connector details - URL & credentials are correct",
            "No components were found for the connector",
            new InvalidArtifactServerException("Failed to fetch the components", WingsException.USER));
      }
    }
    if (versions.isEmpty()) {
      log.info("No components found returning empty map");
    }
    return versions;
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

  //  private Map<String, ArtifactFileMetadataInternal> getArtifactMetadataMap(List<Asset> assets, String repoId) {
  //    Map<String, ArtifactFileMetadataInternal> artifactFileMetadataInternals = new HashMap<>();
  //    if (isEmpty(assets)) {
  //      return artifactFileMetadataInternals;
  //    }
  //    for (Asset item : assets) {
  //      String url = item.getDownloadUrl();
  //      String artifactFileName = url.substring(url.lastIndexOf('/') + 1);
  //      if (IGNORE_EXTENSIONS.stream().anyMatch(artifactFileName::endsWith)) {
  //        continue;
  //      }
  //      if (!item.getRepository().equals(repoId)) {
  //        url = url.replace(item.getRepository(), repoId);
  //      }
  //      artifactFileMetadataInternals.put(item.)add(ArtifactFileMetadataInternal.builder().fileName(artifactFileName).url(url).build());
  //    }
  //    return artifactFileMetadataInternals;
  //  }

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

    List<BuildDetailsInternal> components = new ArrayList<>();
    if (isSuccessful(response)) {
      if (response.body() != null) {
        if (isNotEmpty(response.body().getItems())) {
          for (Nexus3ComponentResponse.Component component : response.body().getItems()) {
            List<ArtifactFileMetadataInternal> artifactFileMetadataInternals =
                getArtifactMetadata(component.getAssets(), repository);
            String versionDownloadUrl = null;
            String imagePath = null;
            if (isNotEmpty(artifactFileMetadataInternals)) {
              versionDownloadUrl = getArtifactDownloadUrl(artifactFileMetadataInternals, null, null);
              imagePath = getArtifactImagePath(artifactFileMetadataInternals, null, null);
            }

            String domainName = Http.getDomainWithPort(nexusConfig.getNexusUrl());
            Map<String, String> metadata = new HashMap<>();
            metadata.put(ArtifactMetadataKeys.IMAGE, domainName + ":" + port + "/" + imageName + ":" + tag);

            BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder()
                                                            .number(component.getVersion())
                                                            .metadata(metadata)
                                                            .buildUrl(versionDownloadUrl)
                                                            .artifactPath(imagePath)
                                                            .build();

            components.add(buildDetailsInternal);
          }
        }
      } else {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the connector details - URL & credentials are correct",
            "No components were found for the connector",
            new InvalidArtifactServerException("Failed to fetch the components", WingsException.USER));
      }
    }
    if (components.isEmpty()) {
      log.info("No components found returning empty map");
    }
    return components;
  }
}
