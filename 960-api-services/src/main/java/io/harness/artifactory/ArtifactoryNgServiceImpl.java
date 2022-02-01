package io.harness.artifactory;

import static java.util.stream.Collectors.toList;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.docker;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.artifactory.client.model.impl.PackageTypeImpl;

@Singleton
@Slf4j
public class ArtifactoryNgServiceImpl implements ArtifactoryNgService {
  @Inject ArtifactoryClientImpl artifactoryClient;

  @Override
  public List<BuildDetails> getBuildDetails(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions) {
    return artifactoryClient.getBuildDetails(artifactoryConfig, repositoryName, artifactPath, maxVersions);
  }

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType) {
    return artifactoryClient.getRepositories(
        artifactoryConfig, Arrays.stream(PackageTypeImpl.values()).filter(type -> docker != type).collect(toList()));
  }
}
