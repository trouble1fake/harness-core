/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;

import java.util.List;

@OwnedBy(CDP)
public interface ArtifactoryRegistryService {
  int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  /**
   * Gets builds.
   *
   * @param artifactoryConfig      the artifactory config
   * @param repositoryName         the repository name
   * @param imageName         the image name
   * @param repoFormat         the repositroy format (docker,....)
   * @param maxNumberOfBuilds the max number of builds
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String imageName, String repoFormat, int maxNumberOfBuilds);
  /**
   * Gets last successful build.
   *
   * @param artifactoryConfig       the artifactory config
   * @param repositoryName          the repository name
   * @param imageName               the image name
   * @param repoFormat              the repositroy format (docker,....)
   * @param tagRegex                the regular expression of tag value
   * @return the last successful build
   */
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(ArtifactoryConfigRequest artifactoryConfig,
      String repositoryName, String imageName, String repoFormat, String tagRegex);
  /**
   * Validates the Image Tag
   * @param artifactoryConfig       the artifactory config
   * @param repositoryName          the repository name
   * @param imageName               the image name
   * @param repoFormat              the repositroy format (docker,....)
   * @param tag                     the repositroy iamge tag value
   */
  BuildDetailsInternal verifyBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String imageName, String repoFormat, String tag);

  /**
   * Validate the credentials
   *
   * @param toArtifactoryInternalConfig the artifactory config
   * @return boolean validate
   */
  boolean validateCredentials(ArtifactoryConfigRequest toArtifactoryInternalConfig);
}
