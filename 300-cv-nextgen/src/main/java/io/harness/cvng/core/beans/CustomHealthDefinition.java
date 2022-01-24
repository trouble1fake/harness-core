/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;

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
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "CustomHealthDefinitionKeys")
public class CustomHealthDefinition {
  HealthSourceQueryType queryType;
  String urlPath;
  String requestBody;
  CustomHealthMethod method;
  TimestampInfo startTimeInfo;
  TimestampInfo endTimeInfo;
}
