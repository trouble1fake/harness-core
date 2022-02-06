/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.NEXUS_REGISTRY_NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.validation.OneOfField;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

/**
 * This is Yaml POJO class which may contain expressions as well.
 * Used mainly for converter layer to store yaml.
 */
@OwnedBy(CDP)
@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(NEXUS_REGISTRY_NAME)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("nexusRegistryArtifactConfig")
@OneOfField(fields = {"tag", "tagRegex"})
@OneOfField(fields = {"repositoryPort", "artifactRepositoryUrl"})
@RecasterAlias("io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig")
public class NexusRegistryArtifactConfig implements ArtifactConfig, Visitable, WithConnectorRef {
  /**
   * Nexus registry connector.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorRef;
  /**
   * Repo name.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repository;
  /**
   * Images in repos need to be referenced via a path.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> imagePath;
  /**
   * Repo format.
   */
  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  ParameterField<String> repositoryFormat;
  /**
   * Repo port.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repositoryPort;
  /**
   * Docker repo server hostname.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> artifactRepositoryUrl;
  /**
   * Tag refers to exact tag number.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> tag;
  /**
   * Tag regex is used to get latest build from builds matching regex.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> tagRegex;
  /**
   * Identifier for artifact.
   */
  @EntityIdentifier String identifier;
  /** Whether this config corresponds to primary artifact.*/
  boolean primaryArtifact;

  // For Visitor Framework Impl
  String metadata;

  @Override
  public ArtifactSourceType getSourceType() {
    return ArtifactSourceType.NEXUS_REGISTRY;
  }

  @Override
  public String getUniqueHash() {
    List<String> valuesList = Arrays.asList(connectorRef.getValue(), imagePath.getValue());
    return ArtifactUtils.generateUniqueHashFromStringList(valuesList);
  }

  @Override
  public ArtifactConfig applyOverrides(ArtifactConfig overrideConfig) {
    NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) overrideConfig;
    NexusRegistryArtifactConfig resultantConfig = this;
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getConnectorRef())) {
      resultantConfig = resultantConfig.withConnectorRef(nexusRegistryArtifactConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getImagePath())) {
      resultantConfig = resultantConfig.withImagePath(nexusRegistryArtifactConfig.getImagePath());
    }
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getTag())) {
      resultantConfig = resultantConfig.withTag(nexusRegistryArtifactConfig.getTag());
    }
    if (!ParameterField.isNull(nexusRegistryArtifactConfig.getTagRegex())) {
      resultantConfig = resultantConfig.withTagRegex(nexusRegistryArtifactConfig.getTagRegex());
    }
    return resultantConfig;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
