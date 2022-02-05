/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.account.AccountClient;
import io.harness.migration.NGMigration;
import io.harness.ng.core.accountsetting.AccountSettingsHelper;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.RestClientUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

public class NGAccountSettingsMigration implements NGMigration {
  @Inject AccountSettingsHelper accountSettingsHelper;
  @Inject AccountClient accountClient;
  @Override
  public void migrate() {
    List<AccountDTO> accountDTOList = RestClientUtils.getResponse(accountClient.getAllAccounts());
    final List<String> accountIdsToBeInserted = accountDTOList.stream()
                                                    .filter(AccountDTO::isNextGenEnabled)
                                                    .map(AccountDTO::getIdentifier)
                                                    .collect(Collectors.toList());

    if (accountIdsToBeInserted.size() != 0) {
      for (String accountId : accountIdsToBeInserted) {
        accountSettingsHelper.setUpDefaultAccountSettings(accountId);
      }
    }
  }
}
