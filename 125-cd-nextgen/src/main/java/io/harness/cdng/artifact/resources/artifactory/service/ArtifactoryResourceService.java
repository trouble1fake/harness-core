/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;

public interface ArtifactoryResourceService {
  ArtifactoryResponseDTO getBuildDetails(IdentifierRef artifactoryConnectorRef, String repository, String imagePath,
      String repositoryFormat, String dockerRepositoryServer, String orgIdentifier, String projectIdentifier);

  ArtifactoryBuildDetailsDTO getSuccessfulBuild(IdentifierRef artifactoryConnectorRef, String repository,
      String imagePath, String repositoryFormat, String dockerRepositoryServer,
      ArtifactoryRequestDTO artifactoryRequestDTO, String orgIdentifier, String projectIdentifier);

  boolean validateArtifactServer(IdentifierRef artifactoryConnectorRef, String orgIdentifier, String projectIdentifier);
}
