/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class NexusRegistryArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String repositoryName;
  String imagePath;
  String repositoryFormat;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.NEXUS_REGISTRY;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return NexusRegistryArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .repository(ParameterField.<String>builder().value(repositoryName).build())
        .imagePath(ParameterField.<String>builder().value(imagePath).build())
        .repositoryFormat(ParameterField.<String>builder().value(repositoryFormat).build())
        .build();
  }
}
