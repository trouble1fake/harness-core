/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
public class ArtifactFile {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @NotNull ParameterField<String> name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @NotNull ParameterField<String> path;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  ParameterField<String> artifactPathExpression;
}
