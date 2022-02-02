/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting;

import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.entities.AccountSettings;

public class AccountSettingMapper {
  public AccountSettings toAccountSetting(AccountSettingsDTO accountSettingsDTO, String accountIdentifier) {
    return AccountSettings.builder()
        .accountIdentifier(accountSettingsDTO.getAccountIdentifier())
        .projectIdentifier(accountSettingsDTO.getProjectIdentifier())
        .orgIdentifier(accountSettingsDTO.getOrgIdentifier())
        .type(accountSettingsDTO.getType())
        .config(accountSettingsDTO.getConfig())
        .build();
  }

  public AccountSettingResponseDTO toDTO(AccountSettings accountSettings) {
    return AccountSettingResponseDTO.builder()
        .accountSettings(AccountSettingsDTO.builder()
                             .accountIdentifier(accountSettings.getAccountIdentifier())
                             .orgIdentifier(accountSettings.getOrgIdentifier())
                             .projectIdentifier(accountSettings.getProjectIdentifier())
                             .type(accountSettings.getType())
                             .config(accountSettings.getConfig())
                             .build())
        .createdAt(accountSettings.getCreatedAt())
        .lastModifiedAt(accountSettings.getLastModifiedAt())
        .build();
  }
}
