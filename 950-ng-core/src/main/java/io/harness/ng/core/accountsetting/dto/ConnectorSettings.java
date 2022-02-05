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
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("io.harness.ng.core.accountsetting.dto.ConnectorSettings")
public class ConnectorSettings extends AccountSettingConfig {
  @Builder
  public ConnectorSettings(Boolean builtInSMDisabled) {
    this.builtInSMDisabled = builtInSMDisabled;
  }

  Boolean builtInSMDisabled;

  @Override
  public AccountSettingConfig getDefaultConfig() {
    return ConnectorSettings.builder().builtInSMDisabled(Boolean.FALSE).build();
  }
}
