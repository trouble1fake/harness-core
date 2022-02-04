/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.MonitoredServiceType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "MonitoredServiceKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "monitoredServices", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
@StoreIn(DbAliases.CVNG)
public final class MonitoredService
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(MonitoredServiceKeys.accountId)
                 .field(MonitoredServiceKeys.orgIdentifier)
                 .field(MonitoredServiceKeys.projectIdentifier)
                 .field(MonitoredServiceKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_service_environment_idx")
                 .unique(true)
                 .field(MonitoredServiceKeys.accountId)
                 .field(MonitoredServiceKeys.orgIdentifier)
                 .field(MonitoredServiceKeys.projectIdentifier)
                 .field(MonitoredServiceKeys.serviceIdentifier)
                 .field(MonitoredServiceKeys.environmentIdentifier)
                 .build())
        .build();
  }
  @Id private String uuid;
  String identifier;
  String name;
  String desc;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String serviceIdentifier;
  @Deprecated String environmentIdentifier;
  List<String> environmentIdentifierList;
  MonitoredServiceType type;
  List<String> healthSourceIdentifiers;
  List<String> changeSourceIdentifiers;
  private long lastUpdatedAt;
  private long createdAt;
  private boolean enabled;

  @NotNull @Singular @Size(max = 128) List<NGTag> tags;

  public List<String> getHealthSourceIdentifiers() {
    if (healthSourceIdentifiers == null) {
      return new ArrayList<>();
    }
    return healthSourceIdentifiers;
  }

  public List<String> getChangeSourceIdentifiers() {
    if (changeSourceIdentifiers == null) {
      return new ArrayList<>();
    }
    return changeSourceIdentifiers;
  }
}
