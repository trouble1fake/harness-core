/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.nexus;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class NexusArtifactDelegateResponse extends ArtifactDelegateResponse {
  String repository;
  /** Images in repos need to be referenced via a path */
  String imagePath;
  String repositoryFormat;
  /** Tag refers to exact tag number */
  String tag;

  @Builder
  public NexusArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String repository, String imagePath, String repositoryFormat, String tag) {
    super(buildDetails, sourceType);
    this.repository = repository;
    this.imagePath = imagePath;
    this.repositoryFormat = repositoryFormat;
    this.tag = tag;
  }
}
