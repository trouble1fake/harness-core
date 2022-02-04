/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.dto;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@TypeAlias("io.harness.ng.core.accountsetting.dto.ConnectorSettings")
public class ConnectorSettings extends AccountSettingConfig {
  Boolean builtInSMDisabled;

  @Override
  public AccountSettingConfig getDefaultConfig() {
    return ConnectorSettings.builder().builtInSMDisabled(Boolean.FALSE).build();
  }
}
