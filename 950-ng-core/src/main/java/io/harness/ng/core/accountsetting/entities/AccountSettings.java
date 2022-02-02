/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.entities;

import io.harness.data.validator.Trimmed;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.accountsetting.dto.AccountSettingConfig;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "AccountSettingsKeys")
@Entity(value = "accountSettings", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("accountSettings")
@Persistent
@Builder
public class AccountSettings implements PersistentEntity, NGAccountAccess {
  @Trimmed @NotEmpty String accountIdentifier;
  @Trimmed String orgIdentifier;
  @Trimmed String projectIdentifier;
  @NotEmpty io.harness.encryption.Scope scope;
  @NotEmpty String fullyQualifiedIdentifier;
  AccountSettingType type;

  @Valid @NotNull AccountSettingConfig accountSettingConfig;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_Index")
                 .fields(Arrays.asList(AccountSettingsKeys.accountIdentifier, AccountSettingsKeys.orgIdentifier,
                     AccountSettingsKeys.projectIdentifier))
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_type_Index")
                 .fields(Arrays.asList(AccountSettingsKeys.accountIdentifier, AccountSettingsKeys.orgIdentifier,
                     AccountSettingsKeys.projectIdentifier, AccountSettingsKeys.type))
                 .build())
        .build();
  }
}
