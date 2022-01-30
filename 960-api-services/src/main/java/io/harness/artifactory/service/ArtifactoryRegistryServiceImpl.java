/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.context.MdcGlobalContextData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.exception.runtime.ArtifactoryServerRuntimeException;
import io.harness.exception.runtime.NexusRegistryInvalidTagRuntimeRuntimeException;
import io.harness.expression.RegexFunctor;
import io.harness.manage.GlobalContextManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ArtifactoryRegistryServiceImpl implements ArtifactoryRegistryService {
  @Inject ArtifactoryClientImpl artifactoryClient;
  private static final int MAX_NUMBER_OF_BUILDS = 250;

  @Override
  public List<BuildDetailsInternal> getBuilds(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String imageName, String repoFormat, int maxNumberOfBuilds) {
    List<BuildDetailsInternal> buildDetails = new ArrayList<>();
    try {
      return artifactoryClient.getArtifactsDetails(
          artifactoryConfig, repositoryName, imageName, repoFormat, maxNumberOfBuilds);
    } catch (ArtifactoryServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch tags for the image",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(ArtifactoryConfigRequest artifactoryConfig,
      String repositoryName, String imageName, String repoFormat, String tagRegex) {
    List<BuildDetailsInternal> builds =
        getBuilds(artifactoryConfig, repositoryName, imageName, repoFormat, MAX_NO_OF_TAGS_PER_IMAGE);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(Collectors.toList());
    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not get the last successful build",
          "There are probably no successful builds for this image & check if the tag filter regex is correct",
          new InvalidArtifactServerException("There are no builds for this repository: " + repositoryName
                  + " image: " + imageName + " and tagRegex: " + tagRegex,
              USER));
    }
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String imageName, String repoFormat, String tag) {
    return getBuildNumber(artifactoryConfig, repositoryName, imageName, repoFormat, tag);
  }

  private BuildDetailsInternal getBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repository,
      String imageName, String repositoryFormat, String tag) {
    List<BuildDetailsInternal> builds =
        getBuilds(artifactoryConfig, repository, imageName, repositoryFormat, MAX_NO_OF_TAGS_PER_IMAGE);
    builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());

    if (builds.size() != 1) {
      Map<String, String> imageDataMap = new HashMap<>();
      imageDataMap.put(ExceptionMetadataKeys.IMAGE_NAME.name(), imageName);
      imageDataMap.put(ExceptionMetadataKeys.IMAGE_TAG.name(), tag);
      imageDataMap.put(ExceptionMetadataKeys.URL.name(), builds.get(0).getBuildUrl());
      MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
      GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      throw new NexusRegistryInvalidTagRuntimeRuntimeException("Could not find tag [" + tag
          + "] for Artifactory repository " + repository + "(" + repositoryFormat + ") image [" + imageName
          + "] on registry [" + artifactoryConfig.getArtifactoryUrl() + "]");
    }
    return builds.get(0);
  }

  @Override
  public boolean validateCredentials(ArtifactoryConfigRequest artifactoryConfig) {
    return artifactoryClient.validateArtifactServer(artifactoryConfig);
  }
}
