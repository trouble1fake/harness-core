package io.harness.cdng.artifact.resources.artifactory.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRepoDetailsDTO;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface ArtifactoryResourceService {
  ArtifactoryRepoDetailsDTO getRepositories(
      String repositoryType, IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier);

  List<ArtifactoryArtifactBuildDetailsDTO> getBuildDetails(String repositoryName, String filePath, int maxVersions,
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier);
}
