/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthDefinition;
import io.harness.cvng.core.beans.CustomHealthDefinition.CustomHealthDefinitionKeys;
import io.harness.cvng.core.beans.CustomHealthLogDefinition;
import io.harness.cvng.core.beans.CustomHealthLogDefinition.CustomHealthLogDefinitionKeys;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "CustomHealthLogCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class CustomHealthLogCVConfig extends LogCVConfig {
  static String DSL;

  static {
    try {
      DSL = Resources.toString(
          DatadogLogCVConfig.class.getResource("datadog-log-fetch-data.datacollection"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  CustomHealthLogDefinition queryDefinition;

  @Override
  protected void validateParams() {
    checkNotNull(getQueryName(), generateErrorMessageFromParam(LogCVConfigKeys.queryName));
    checkNotNull(getQuery(), generateErrorMessageFromParam(LogCVConfigKeys.query));
    checkNotNull(queryDefinition, generateErrorMessageFromParam(CustomHealthLogCVConfigKeys.queryDefinition));
    checkNotNull(queryDefinition.getQueryValueJsonPath(),
        generateErrorMessageFromParam(CustomHealthLogDefinitionKeys.queryValueJsonPath));
    checkNotNull(queryDefinition.getTimestampJsonPath(),
        generateErrorMessageFromParam(CustomHealthLogDefinitionKeys.timestampJsonPath));
    checkNotNull(queryDefinition.getServiceInstanceJsonPath(),
        generateErrorMessageFromParam(CustomHealthLogDefinitionKeys.serviceInstanceJsonPath));

    CustomHealthDefinition customHealthDefinition = queryDefinition.getCustomHealthDefinition();

    checkNotNull(
        customHealthDefinition.getUrlPath(), generateErrorMessageFromParam(CustomHealthDefinitionKeys.urlPath));
    checkNotNull(customHealthDefinition.getMethod(), generateErrorMessageFromParam(CustomHealthDefinitionKeys.method));
    checkNotNull(customHealthDefinition.getStartTimeInfo(),
        generateErrorMessageFromParam(CustomHealthDefinitionKeys.startTimeInfo));
    checkNotNull(
        customHealthDefinition.getEndTimeInfo(), generateErrorMessageFromParam(CustomHealthDefinitionKeys.endTimeInfo));
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CUSTOM_HEALTH_LOG;
  }

  @Override
  public String getDataCollectionDsl() {
    return DSL;
  }

  @Override
  public String getHostCollectionDSL() {
    throw new RuntimeException("Not implemented");
  }
}
