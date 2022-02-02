/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.accountsetting;

import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.entities.AccountSettings;

import java.util.List;

public interface AccountSettingCustomRepository {
  AccountSettings save(AccountSettings objectToSave);

  List<AccountSettings> findAll(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type);

  AccountSettings upsert(AccountSettings accountSettings, String accountIdentifier);

  AccountSettings findByScopeIdentifiersAndType(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type);
}
