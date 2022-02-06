/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.nexus.NexusRequest;

import java.util.List;

@OwnedBy(CDP)
public interface NexusRegistryService {
  int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  /**
   * Gets builds.
   *
   * @param nexusConfig      the nexus config
   * @param imageName         the image name
   * @param maxNumberOfBuilds the max number of builds
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds(NexusRequest nexusConfig, String repositoryName, Integer port, String imageName,
      String repoFormat, int maxNumberOfBuilds);

  /**
   * Gets the last successful build with input as tag regex.
   * @param nexusConfig the nexus config
   * @param imageName the image name
   * @param tagRegex tag regex
   * @return the last successful build
   */
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(NexusRequest nexusConfig, String repository, Integer port,
      String imageName, String repositoryFormat, String tagRegex);

  /**
   * Validates the Image Tag
   * @param nexusConfig the nexus config
   * @param imageName the image name
   */
  BuildDetailsInternal verifyBuildNumber(
      NexusRequest nexusConfig, String repository, Integer port, String imageName, String repositoryFormat, String tag);

  /**
   * Validate the credentials
   *
   * @param nexusConfig the nexus config
   * @return boolean validate
   */
  boolean validateCredentials(NexusRequest nexusConfig);
}
