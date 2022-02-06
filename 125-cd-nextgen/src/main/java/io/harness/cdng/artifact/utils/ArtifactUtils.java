/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.NGLogCallback;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ArtifactUtils {
  public final String PRIMARY_ARTIFACT = "primary";
  public final String SIDECAR_ARTIFACT = "sidecars";

  public String getArtifactKey(ArtifactConfig artifactConfig) {
    return artifactConfig.isPrimaryArtifact() ? artifactConfig.getIdentifier()
                                              : SIDECAR_ARTIFACT + "." + artifactConfig.getIdentifier();
  }

  public List<ArtifactConfig> convertArtifactListIntoArtifacts(
      ArtifactListConfig artifactListConfig, NGLogCallback ngManagerLogCallback) {
    List<ArtifactConfig> artifacts = new LinkedList<>();
    if (artifactListConfig == null) {
      return artifacts;
    }
    if (artifactListConfig.getPrimary() != null) {
      artifacts.add(artifactListConfig.getPrimary().getSpec());
      saveLogs(ngManagerLogCallback,
          "Primary artifact details: \n"
              + getLogInfo(artifactListConfig.getPrimary().getSpec(), artifactListConfig.getPrimary().getSourceType()));
    }
    if (EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars())) {
      artifacts.addAll(
          artifactListConfig.getSidecars().stream().map(s -> s.getSidecar().getSpec()).collect(Collectors.toList()));
      saveLogs(ngManagerLogCallback,
          "Sidecars details: \n"
              + artifactListConfig.getSidecars()
                    .stream()
                    .map(s -> getLogInfo(s.getSidecar().getSpec(), s.getSidecar().getSourceType()))
                    .collect(Collectors.joining()));
    }
    return artifacts;
  }

  public void appendIfNecessary(StringBuilder keyBuilder, String value) {
    if (keyBuilder == null) {
      throw new InvalidRequestException("Key string builder cannot be null");
    }
    if (isNotEmpty(value)) {
      keyBuilder.append(NGConstants.STRING_CONNECTOR).append(value);
    }
  }

  // TODO(archit): Check whether string should be case sensitive or not.
  public String generateUniqueHashFromStringList(List<String> valuesList) {
    valuesList.sort(Comparator.nullsLast(String::compareTo));
    StringBuilder keyBuilder = new StringBuilder();
    valuesList.forEach(s -> appendIfNecessary(keyBuilder, s));
    return Hashing.sha256().hashString(keyBuilder.toString(), StandardCharsets.UTF_8).toString();
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }

  public String getLogInfo(ArtifactConfig artifactConfig, ArtifactSourceType sourceType) {
    if (sourceType == null) {
      return "";
    }

    String placeholder = " type: %s, image: %s, tag/tagRegex: %s, connectorRef: %s\n";
    switch (sourceType) {
      case DOCKER_REGISTRY:
        DockerHubArtifactConfig dockerHubArtifactConfig = (DockerHubArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, dockerHubArtifactConfig.getImagePath().getValue(),
            dockerHubArtifactConfig.getTag().getValue() != null ? dockerHubArtifactConfig.getTag().getValue()
                                                                : dockerHubArtifactConfig.getTagRegex().getValue(),
            dockerHubArtifactConfig.getConnectorRef().getValue());
      case GCR:
        GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, gcrArtifactConfig.getImagePath().getValue(),
            gcrArtifactConfig.getTag().getValue() != null ? gcrArtifactConfig.getTag().getValue()
                                                          : gcrArtifactConfig.getTagRegex().getValue(),
            gcrArtifactConfig.getConnectorRef().getValue());
      case ECR:
        EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, ecrArtifactConfig.getImagePath().getValue(),
            ecrArtifactConfig.getTag().getValue() != null ? ecrArtifactConfig.getTag().getValue()
                                                          : ecrArtifactConfig.getTagRegex().getValue(),
            ecrArtifactConfig.getConnectorRef().getValue());
      case NEXUS_REGISTRY:
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, nexusRegistryArtifactConfig.getImagePath().getValue(),
            nexusRegistryArtifactConfig.getTag().getValue() != null
                ? nexusRegistryArtifactConfig.getTag().getValue()
                : nexusRegistryArtifactConfig.getTagRegex().getValue(),
            nexusRegistryArtifactConfig.getConnectorRef().getValue());
      case ARTIFACTORY_REGISTRY:
        ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
            (ArtifactoryRegistryArtifactConfig) artifactConfig;
        return String.format(placeholder, sourceType, artifactoryRegistryArtifactConfig.getImagePath().getValue(),
            artifactoryRegistryArtifactConfig.getTag().getValue() != null
                ? artifactoryRegistryArtifactConfig.getTag().getValue()
                : artifactoryRegistryArtifactConfig.getTagRegex().getValue(),
            artifactoryRegistryArtifactConfig.getConnectorRef().getValue());
      default:
        throw new UnsupportedOperationException(String.format("Unknown Artifact Config type: [%s]", sourceType));
    }
  }
}
