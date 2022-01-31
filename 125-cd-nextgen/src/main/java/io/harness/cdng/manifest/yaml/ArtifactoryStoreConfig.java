/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.ARTIFACTORY)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("artifactoryStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.ArtifactoryStore")
public class ArtifactoryStoreConfig implements StoreConfig, Visitable, WithConnectorRef {
  @NotNull
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> connectorRef;
  @NotNull
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> repositoryPath;
  @NotNull
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> artifactName;
  @NotNull
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> version;

  @Override
  public String getKind() {
    return ManifestStoreType.ARTIFACTORY;
  }

  @Override
  public StoreConfig cloneInternal() {
    return ArtifactoryStoreConfig.builder()
        .connectorRef(connectorRef)
        .artifactName(artifactName)
        .repositoryPath(repositoryPath)
        .version(version)
        .build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    ArtifactoryStoreConfig ArtifactoryStore = (ArtifactoryStoreConfig) overrideConfig;
    ArtifactoryStoreConfig resultantArtifactoryStore = this;
    if (!ParameterField.isNull(ArtifactoryStore.getConnectorRef())) {
      resultantArtifactoryStore = resultantArtifactoryStore.withConnectorRef(ArtifactoryStore.getConnectorRef());
    }
    if (!ParameterField.isNull(ArtifactoryStore.getArtifactName())) {
      resultantArtifactoryStore = resultantArtifactoryStore.withArtifactName(ArtifactoryStore.getArtifactName());
    }
    if (!ParameterField.isNull(ArtifactoryStore.getRepositoryPath())) {
      resultantArtifactoryStore = resultantArtifactoryStore.withRepositoryPath(ArtifactoryStore.getRepositoryPath());
    }
    if (!ParameterField.isNull(ArtifactoryStore.getVersion())) {
      resultantArtifactoryStore = resultantArtifactoryStore.withVersion(ArtifactoryStore.getVersion());
    }
    return resultantArtifactoryStore;
  }
}
