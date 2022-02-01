package io.harness.artifactory;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.Map;

public interface ArtifactoryNgService {
  List<BuildDetails> getBuildDetails(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions);

  Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType);
}
