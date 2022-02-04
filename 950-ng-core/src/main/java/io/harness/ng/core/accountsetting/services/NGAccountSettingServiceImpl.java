/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.services;

import static java.lang.String.format;

import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.accountsetting.AccountSettingMapper;
import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.repositories.accountsetting.AccountSettingRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class NGAccountSettingServiceImpl implements NGAccountSettingService {
  private AccountSettingMapper accountSettingMapper;
  private AccountSettingRepository accountSettingRepository;

  @Override
  public AccountSettingResponseDTO update(AccountSettingsDTO accountSettingsDTO, String accountIdentifier) {
    final AccountSettings accountSettings =
        accountSettingMapper.toAccountSetting(accountSettingsDTO, accountIdentifier);
    accountSettings.setLastModifiedAt(System.currentTimeMillis());

    AccountSettings updatedAccountSetting = null;
    try {
      updatedAccountSetting = accountSettingRepository.updateAccountSetting(accountSettings, accountIdentifier);
    } catch (WingsException ex) {
      throw new InvalidRequestException(
          format("Unable to save settings for account [%s] org [%s] project [%s] already exists",
              accountSettings.getAccountIdentifier(), accountSettings.getOrgIdentifier(),
              accountSettings.getProjectIdentifier()));
    }
    return getResponse(updatedAccountSetting);
  }

  @Override
  public AccountSettingResponseDTO create(AccountSettingsDTO accountSettingsDTO, String accountIdentifier) {
    final AccountSettings accountSettings =
        accountSettingMapper.toAccountSetting(accountSettingsDTO, accountIdentifier);
    accountSettings.setLastModifiedAt(System.currentTimeMillis());
    AccountSettings savedAccountSetting = null;
    try {
      savedAccountSetting = accountSettingRepository.save(accountSettings);
      savedAccountSetting.setConfig(accountSettings.getConfig());
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          format("Account Setting already created for account [%s] org [%s] project [%s] already exists",
              accountSettings.getAccountIdentifier(), accountSettings.getOrgIdentifier(),
              accountSettings.getProjectIdentifier()));
    }
    return getResponse(savedAccountSetting);
  }

  @Override
  public List<AccountSettingsDTO> list(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    final List<AccountSettings> accountSettingList =
        accountSettingRepository.findAll(accountId, orgIdentifier, projectIdentifier, type);
    return getListResponse(accountSettingList);
  }

  private List<AccountSettingsDTO> getListResponse(List<AccountSettings> accountSettingList) {
    return accountSettingList.stream().map(accountSettingMapper::toDTO).collect(Collectors.toList());
  }

  @Override
  public AccountSettingResponseDTO get(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    final AccountSettings byScopeIdentifiersAndType =
        accountSettingRepository.findByScopeIdentifiersAndType(accountId, orgIdentifier, projectIdentifier, type);
    return getResponse(byScopeIdentifiersAndType);
  }

  private AccountSettingResponseDTO getResponse(AccountSettings accountSettings) {
    return accountSettingMapper.toResponseDTO(accountSettings);
  }
}
