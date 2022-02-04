/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting;

import io.harness.ng.core.accountsetting.dto.AccountSettingConfig;
import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.ConnectorSettings;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@Singleton
public class AccountSettingsHelper {
  @Inject NGAccountSettingService ngAccountSettingService;
  @Inject MongoTemplate mongoTemplate;
  @Inject AccountSettingConfig accountSettingConfig;

  public boolean getIsBuiltInSMDisabled(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    final AccountSettingResponseDTO accountSettingResponseDTO =
        ngAccountSettingService.get(accountIdentifier, orgIdentifier, projectIdentifier, type);
    final ConnectorSettings config = (ConnectorSettings) accountSettingResponseDTO.getAccountSettings().getConfig();
    if (config == null || config.getBuiltInSMDisabled() == null) {
      return false;
    }
    return config.getBuiltInSMDisabled();
  }

  public void setUpDefaultAccountSettings(String accountIdentifier) {
    final List<AccountSettings> accountSettings = new ArrayList<>();
    try {
      for (AccountSettingType accountSettingType : AccountSettingType.values()) {
        AccountSettings accountSetting = AccountSettings.builder()
                                             .accountIdentifier(accountIdentifier)
                                             .type(accountSettingType)
                                             .config(accountSettingConfig.getDefaultConfig())
                                             .build();
        accountSettings.add(accountSetting);
      }

      mongoTemplate.insertAll(accountSettings);
    } catch (Exception ex) {
      log.error(String.format("Failed to create default account settings"), ex);
    }
  }
}
