/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.accountsetting;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.ng.core.accountsetting.entities.AccountSettings.AccountSettingsKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AccountSettingCustomRepositoryImpl implements AccountSettingCustomRepository {
  private MongoTemplate mongoTemplate;
  @Override
  public List<AccountSettings> findAll(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountId)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(projectIdentifier);
    if (type != null) {
      criteria = criteria.and(AccountSettingsKeys.type).is(type);
    }
    Query query = new Query(criteria);

    return mongoTemplate.find(query, AccountSettings.class);
  }

  @Override
  public AccountSettings upsert(AccountSettings accountSettings, String accountIdentifier) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(accountSettings.getOrgIdentifier())
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(accountSettings.getProjectIdentifier())
                            .and(AccountSettingsKeys.type)
                            .is(accountSettings.getType());
    Update update = update(AccountSettingsKeys.config, accountSettings.getConfig());

    UpdateResult upsert = mongoTemplate.upsert(new Query(criteria), update, AccountSettings.class);
    return accountSettings;
  }

  @Override
  public AccountSettings findByScopeIdentifiersAndType(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountId)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(AccountSettingsKeys.type)
                            .is(type);
    return mongoTemplate.findOne(new Query(criteria), AccountSettings.class);
  }

  @Override
  public AccountSettings updateAccountSetting(AccountSettings accountSettings, String accountIdentifier) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(accountSettings.getOrgIdentifier())
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(accountSettings.getProjectIdentifier())
                            .and(AccountSettingsKeys.type)
                            .is(accountSettings.getType());

    Update update = update(AccountSettingsKeys.config, accountSettings.getConfig());
    return mongoTemplate.findAndModify(new Query(criteria), update, AccountSettings.class);
  }
}
