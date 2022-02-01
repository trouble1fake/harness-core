package io.harness.cdng.artifact.resources.artifactory.mappers;

import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactoryResourceMapper {
  public List<ArtifactoryArtifactBuildDetailsDTO> toArtifactoryArtifactBuildDetailsDTO(
      List<BuildDetails> buildDetails) {
    return buildDetails.stream()
        .map(b
            -> ArtifactoryArtifactBuildDetailsDTO.builder()
                   .artifactName(b.getNumber())
                   .artifactPath(b.getArtifactPath())
                   .build())
        .collect(Collectors.toList());
  }
}
