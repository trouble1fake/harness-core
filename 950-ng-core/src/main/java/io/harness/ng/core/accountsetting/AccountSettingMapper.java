/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting;

import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingsInfoDTO;
import io.harness.ng.core.accountsetting.entities.AccountSettings;

public class AccountSettingMapper {
  public AccountSettings toAccountSetting(AccountSettingsDTO accountSettingsDTO, String accountIdentifier) {
    final AccountSettingsInfoDTO accountSettingsInfoDTO = accountSettingsDTO.getAccountSettingsInfoDTO();
    return AccountSettings.builder()
        .accountIdentifier(accountSettingsInfoDTO.getAccountIdentifier())
        .projectIdentifier(accountSettingsInfoDTO.getProjectIdentifier())
        .orgIdentifier(accountSettingsInfoDTO.getOrgIdentifier())
        .type(accountSettingsInfoDTO.getType())
        .accountSettingConfig(accountSettingsInfoDTO.getAccountSettingConfig())
        .build();
  }

  public AccountSettingsInfoDTO toDTO(AccountSettings accountSettings) {
    return AccountSettingsInfoDTO.builder()
        .accountIdentifier(accountSettings.getAccountIdentifier())
        .orgIdentifier(accountSettings.getOrgIdentifier())
        .projectIdentifier(accountSettings.getProjectIdentifier())
        .type(accountSettings.getType())
        .accountSettingConfig(accountSettings.getAccountSettingConfig())
        .build();
  }
}
