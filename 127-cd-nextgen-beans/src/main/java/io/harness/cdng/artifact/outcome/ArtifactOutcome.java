/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WithIdentifier;
import io.harness.cdng.artifact.WithArtifactSummary;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "artifactType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DockerArtifactOutcome.class, name = "Dockerhub")
  , @JsonSubTypes.Type(value = GcrArtifactOutcome.class, name = "Gcr"),
      @JsonSubTypes.Type(value = EcrArtifactOutcome.class, name = "Ecr"),
      @JsonSubTypes.Type(value = NexusArtifactOutcome.class, name = "NexusRegistry"),
      @JsonSubTypes.Type(value = ArtifactoryArtifactOutcome.class, name = "ArtifactoryRegistry")
})
@OwnedBy(HarnessTeam.CDP)
public interface ArtifactOutcome extends Outcome, WithIdentifier, WithArtifactSummary {
  boolean isPrimaryArtifact();
  String getArtifactType();
  String getIdentifier();
  String getTag();
}
