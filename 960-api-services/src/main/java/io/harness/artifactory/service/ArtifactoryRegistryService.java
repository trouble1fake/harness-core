/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.nexus.NexusRequest;

import java.util.List;

@OwnedBy(CDP)
public interface ArtifactoryRegistryService {
  int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  List<BuildDetailsInternal> getVersions(NexusRequest nexusConfig, String repoName);

  //  /**
  //   * Gets builds.
  //   *
  //   * @param dockerConfig      the docker config
  //   * @param imageName         the image name
  //   * @param maxNumberOfBuilds the max number of builds
  //   * @return the builds
  //   */
  List<BuildDetailsInternal> getBuilds(NexusRequest nexusConfig, String imageName, int maxNumberOfBuilds);
  //
  //  /**
  //   * Gets labels.
  //   *
  //   * @param dockerConfig the docker config
  //   * @param imageName    the image name
  //   * @param tags         the image tags to find labels of
  //   * @return the builds
  //   */
  //  List<Map<String, String>> getLabels(DockerInternalConfig dockerConfig, String imageName, List<String> tags);
  //
  //  /**
  //   * Gets last successful build.
  //   *
  //   * @param dockerConfig the docker config
  //   * @param imageName    the image name
  //   * @return the last successful build
  //   */
  //  BuildDetailsInternal getLastSuccessfulBuild(DockerInternalConfig dockerConfig, String imageName);
  //
  //  /**
  //   * Gets the last successful build with input as tag regex.
  //   * @param dockerConfig the docker config
  //   * @param imageName the image name
  //   * @param tagRegex tag regex
  //   * @return the last successful build
  //   */
  //  BuildDetailsInternal getLastSuccessfulBuildFromRegex(
  //      DockerInternalConfig dockerConfig, String imageName, String tagRegex);
  //
  //  /**
  //   * Validates the Image
  //   * @param dockerConfig the docker config
  //   * @param imageName the image name
  //   */
  //  boolean verifyImageName(DockerInternalConfig dockerConfig, String imageName);
  //
  //  /**
  //   * Validates the Image Tag
  //   * @param dockerConfig the docker config
  //   * @param imageName the image name
  //   */
  //  BuildDetailsInternal verifyBuildNumber(DockerInternalConfig dockerConfig, String imageName, String tag);
  //
  //  /**
  //   * Validate the credentials
  //   *
  //   * @param dockerConfig the docker config
  //   * @return boolean validate
  //   */
  //  boolean validateCredentials(DockerInternalConfig dockerConfig);
}
