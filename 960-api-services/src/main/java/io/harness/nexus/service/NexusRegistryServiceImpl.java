/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.context.MdcGlobalContextData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.exception.runtime.NexusRegistryInvalidTagRuntimeRuntimeException;
import io.harness.exception.runtime.NexusServerRuntimeException;
import io.harness.expression.RegexFunctor;
import io.harness.manage.GlobalContextManager;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class NexusRegistryServiceImpl implements NexusRegistryService {
  private static final int MAX_NUMBER_OF_BUILDS = 250;
  @Inject NexusClientImpl nexusClient;

  @Override
  public List<BuildDetailsInternal> getBuilds(NexusRequest nexusConfig, String repositoryName, Integer port,
      String imageName, String repoFormat, int maxNumberOfBuilds) {
    List<BuildDetailsInternal> buildDetails;
    try {
      buildDetails = nexusClient.getArtifactsVersions(nexusConfig, repositoryName, port, imageName, repoFormat);
    } catch (NexusServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch tags for the image",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
    return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());
  }

  private List<BuildDetailsInternal> getBuildDetails(NexusRequest nexusConfig, String repository, Integer port,
      String imageName, String repositoryFormat, String tag) throws IOException {
    List<BuildDetailsInternal> buildDetails;
    try {
      buildDetails = nexusClient.getBuildDetails(nexusConfig, repository, port, imageName, repositoryFormat, tag);
    } catch (NexusServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch tags for the image",
          "Check if the image exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
    return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(NexusRequest nexusConfig, String repository, Integer port,
      String imageName, String repositoryFormat, String tagRegex) {
    List<BuildDetailsInternal> builds =
        getBuilds(nexusConfig, repository, port, imageName, repositoryFormat, MAX_NUMBER_OF_BUILDS);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(Collectors.toList());

    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not get the last successful build",
          "There are probably no successful builds for this image & check if the tag filter regex is correct",
          new InvalidArtifactServerException("There are no builds for this repository: " + repository
                  + " image: " + imageName + " and tagRegex: " + tagRegex,
              USER));
    }
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(NexusRequest nexusConfig, String repository, Integer port,
      String imageName, String repositoryFormat, String tag) {
    return getBuildNumber(nexusConfig, repository, port, imageName, repositoryFormat, tag);
  }

  @Override
  public boolean validateCredentials(NexusRequest nexusConfig) {
    return nexusClient.isRunning(nexusConfig);
  }

  private BuildDetailsInternal getBuildNumber(NexusRequest nexusConfig, String repository, Integer port,
      String imageName, String repositoryFormat, String tag) {
    try {
      List<BuildDetailsInternal> builds =
          getBuildDetails(nexusConfig, repository, port, imageName, repositoryFormat, tag);
      builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());

      if (builds.size() != 1) {
        Map<String, String> imageDataMap = new HashMap<>();
        imageDataMap.put(ExceptionMetadataKeys.IMAGE_NAME.name(), imageName);
        imageDataMap.put(ExceptionMetadataKeys.IMAGE_TAG.name(), tag);
        imageDataMap.put(ExceptionMetadataKeys.URL.name(), builds.get(0).getBuildUrl());
        MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
        GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
        throw new NexusRegistryInvalidTagRuntimeRuntimeException("Could not find tag [" + tag
            + "] for Nexus repository " + repository + "(" + repositoryFormat + ") image [" + imageName
            + "] on registry [" + nexusConfig.getNexusUrl() + "]");
      }
      return builds.get(0);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the given tag for the image",
          "The tag provided for the image may be incorrect.",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }
}
