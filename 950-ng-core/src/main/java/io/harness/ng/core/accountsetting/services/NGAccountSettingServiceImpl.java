/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.services;

import io.harness.ng.core.accountsetting.AccountSettingMapper;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingsInfoDTO;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.repositories.accountsetting.AccountSettingRepository;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class NGAccountSettingServiceImpl implements NGAccountSettingService {
  private AccountSettingMapper accountSettingMapper;
  private AccountSettingRepository accountSettingRepository;

  @Override
  public AccountSettingsDTO update(AccountSettingsDTO accountSettingsDTO, String accountIdentifier) {
    final AccountSettings accountSettings =
        accountSettingMapper.toAccountSetting(accountSettingsDTO, accountIdentifier);
    accountSettingRepository.upsert(accountSettings, accountIdentifier);
    return accountSettingsDTO;
  }

  @Override
  public AccountSettingsDTO create(AccountSettingsDTO accountSettingsDTO, String accountIdentifier) {
    final AccountSettings accountSettings =
        accountSettingMapper.toAccountSetting(accountSettingsDTO, accountIdentifier);
    final AccountSettings save = accountSettingRepository.save(accountSettings);
    return accountSettingsDTO;
  }

  @Override
  public List<AccountSettings> list(String accountId, String orgIdentifier, String projectIdentifier, String type) {
    return accountSettingRepository.findAll(accountId, orgIdentifier, projectIdentifier, type);
  }

  @Override
  public AccountSettingsDTO get(String accountId, String orgIdentifier, String projectIdentifier, String type) {
    final AccountSettings byScopeIdentifiersAndType =
        accountSettingRepository.findByScopeIdentifiersAndType(accountId, orgIdentifier, projectIdentifier, type);
    final AccountSettingsInfoDTO accountSettingsInfoDTO = accountSettingMapper.toDTO(byScopeIdentifiersAndType);
    return AccountSettingsDTO.builder().accountSettingsInfoDTO(accountSettingsInfoDTO).build();
  }
}
