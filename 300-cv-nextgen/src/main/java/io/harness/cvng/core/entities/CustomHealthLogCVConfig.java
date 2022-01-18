/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthDefinition;

import java.util.List;
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
@FieldNameConstants(innerTypeName = "CustomHealthCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class CustomHealthLogCVConfig extends LogCVConfig {
  List<CustomHealthDefinition> logDefinitions;

  @Override
  protected void validateParams() {}

  @Override
  public DataSourceType getType() {
    return null;
  }

  @Override
  public String getDataCollectionDsl() {
    return null;
  }

  @Override
  public String getHostCollectionDSL() {
    return null;
  }
}
