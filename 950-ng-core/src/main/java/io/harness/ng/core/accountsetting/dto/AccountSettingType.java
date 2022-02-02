/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.dto;

import io.harness.EntitySubtype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AccountSettingType implements EntitySubtype {
  @JsonProperty("Connector") CONNECTOR("Connector");

  private final String displayName;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static AccountSettingType getAccountSettingType(@JsonProperty("type") String displayName) {
    for (AccountSettingType connectorType : AccountSettingType.values()) {
      if (connectorType.displayName.equalsIgnoreCase(displayName)) {
        return connectorType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  AccountSettingType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  public static AccountSettingType fromString(final String s) {
    return AccountSettingType.getAccountSettingType(s);
  }
}
