/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.dto.ConnectorSettings;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class AccountSettingMapperTest extends NGCoreTestBase {
  @InjectMocks AccountSettingMapper accountSettingMapper;

  AccountSettingResponseDTO accountSettingResponseDTO;
  AccountSettingResponseDTO updatedAccountSettingResponseDTO;
  AccountSettingsDTO updatedAccountSettingDTO;
  AccountSettingsDTO accountSettingsDTO;
  AccountSettings accountSettings;
  String accountIdentifier = "mockedAccountId";

  @Before
  public void setUp() throws Exception {
    accountSettingsDTO = AccountSettingsDTO.builder()
                             .accountIdentifier(accountIdentifier)
                             .type(AccountSettingType.CONNECTOR)
                             .config(ConnectorSettings.builder().builtInSMDisabled(false).build())
                             .build();

    updatedAccountSettingDTO = AccountSettingsDTO.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .type(AccountSettingType.CONNECTOR)
                                   .config(ConnectorSettings.builder().builtInSMDisabled(true).build())
                                   .build();

    updatedAccountSettingResponseDTO =
        AccountSettingResponseDTO.builder().accountSettings(updatedAccountSettingDTO).build();
    accountSettingResponseDTO = AccountSettingResponseDTO.builder().accountSettings(accountSettingsDTO).build();
    accountSettings = AccountSettings.builder()
                          .accountIdentifier(accountIdentifier)
                          .type(AccountSettingType.CONNECTOR)
                          .config(ConnectorSettings.builder().builtInSMDisabled(false).build())
                          .build();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void toAccountSetting() {
    final AccountSettings accountSettings =
        accountSettingMapper.toAccountSetting(accountSettingsDTO, accountIdentifier);

    assertThat(accountSettings).isNotNull();
    assertThat(accountSettings.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(accountSettings.getType()).isEqualTo(AccountSettingType.CONNECTOR);
    assertThat(accountSettings.getConfig()).isEqualTo(accountSettingsDTO.getConfig());
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void toDTO() {
    final AccountSettingsDTO accountSettingsDTO = accountSettingMapper.toDTO(accountSettings);
    assertThat(accountSettingsDTO).isNotNull();
    assertThat(accountSettingsDTO.getConfig()).isEqualTo(accountSettings.getConfig());
    assertThat(accountSettingsDTO.getAccountIdentifier()).isEqualTo(accountSettings.getAccountIdentifier());
    assertThat(accountSettingsDTO.getType()).isEqualTo(accountSettings.getType());
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void toDTOIsNull() {
    final AccountSettingsDTO accountSettings = accountSettingMapper.toDTO(null);
    assertThat(accountSettings).isNotNull();
    assertThat(accountSettings.getConfig()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void toResponseDTO() {
    final AccountSettingResponseDTO accountSettingResponseDTO = accountSettingMapper.toResponseDTO(accountSettings);
    assertThat(accountSettingResponseDTO).isNotNull();
    assertThat(accountSettingResponseDTO.getAccountSettings()).isNotNull();
    assertThat(accountSettingResponseDTO.getAccountSettings().getAccountIdentifier())
        .isEqualTo(accountSettings.getAccountIdentifier());
    assertThat(accountSettingResponseDTO.getAccountSettings().getType()).isEqualTo(accountSettings.getType());
  }
}
