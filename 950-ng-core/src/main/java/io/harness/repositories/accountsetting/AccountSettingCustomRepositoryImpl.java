/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.accountsetting;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.entities.AccountSettings;

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
  public AccountSettings save(AccountSettings objectToSave) {
    return mongoTemplate.save(objectToSave);
  }

  @Override
  public List<AccountSettings> findAll(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    Criteria criteria = Criteria.where(AccountSettings.AccountSettingsKeys.accountIdentifier)
                            .is(accountId)
                            .and(AccountSettings.AccountSettingsKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(AccountSettings.AccountSettingsKeys.projectIdentifier)
                            .is(projectIdentifier);
    if (isNotEmpty(String.valueOf(type))) {
      criteria = criteria.and(AccountSettings.AccountSettingsKeys.type).is(type);
    }
    Query query = new Query(criteria);

    return mongoTemplate.find(query, AccountSettings.class);
  }

  @Override
  public AccountSettings upsert(AccountSettings accountSettings, String accountIdentifier) {
    Criteria criteria = Criteria.where(AccountSettings.AccountSettingsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(AccountSettings.AccountSettingsKeys.orgIdentifier)
                            .is(accountSettings.getOrgIdentifier())
                            .and(AccountSettings.AccountSettingsKeys.projectIdentifier)
                            .is(accountSettings.getProjectIdentifier())
                            .and(AccountSettings.AccountSettingsKeys.type)
                            .is(accountSettings.getType());
    Update update = update(AccountSettings.AccountSettingsKeys.config, accountSettings.getConfig());

    UpdateResult upsert = mongoTemplate.upsert(new Query(criteria), update, AccountSettings.class);
    //    upsert.
    return accountSettings;
  }

  @Override
  public AccountSettings findByScopeIdentifiersAndType(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    Criteria criteria = Criteria.where(AccountSettings.AccountSettingsKeys.accountIdentifier)
                            .is(accountId)
                            .and(AccountSettings.AccountSettingsKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(AccountSettings.AccountSettingsKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(AccountSettings.AccountSettingsKeys.type)
                            .is(type);
    return mongoTemplate.findOne(new Query(criteria), AccountSettings.class);
  }
}
