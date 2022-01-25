/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.nexus.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;

public interface NexusResourceService {
  NexusResponseDTO getBuildDetails(IdentifierRef nexusConnectorRef, String repositoryName, String imagePath,
      String repositoryFormat, String orgIdentifier, String projectIdentifier);

  NexusResponseDTO getLabels(IdentifierRef nexusConnectorRef, String repositoryName, String imagePath,
      String repositoryFormat, NexusRequestDTO nexusRequestDTO, String orgIdentifier, String projectIdentifier);

  NexusBuildDetailsDTO getSuccessfulBuild(IdentifierRef nexusConnectorRef, String repositoryName, String imagePath,
      String repositoryFormat, NexusRequestDTO nexusRequestDTO, String orgIdentifier, String projectIdentifier);

  boolean validateArtifactServer(IdentifierRef nexusConnectorRef, String orgIdentifier, String projectIdentifier);

  // In case of docker source here is docker image.
  boolean validateArtifactSource(String repositoryName, String imagePath, String repositoryFormat,
      IdentifierRef nexusConnectorRef, String orgIdentifier, String projectIdentifier);
}
