/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.changeSource;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "ChangeSourceKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "changeSources")
@OwnedBy(HarnessTeam.CV)
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.CVNG)
public abstract class ChangeSource
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_index")
                 .field(ChangeSourceKeys.accountId)
                 .field(ChangeSourceKeys.orgIdentifier)
                 .field(ChangeSourceKeys.projectIdentifier)
                 .field(ChangeSourceKeys.envIdentifier)
                 .field(ChangeSourceKeys.serviceIdentifier)
                 .field(ChangeSourceKeys.identifier)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("demo_generation_index")
                 .field(ChangeSourceKeys.isConfiguredForDemo)
                 .field(ChangeSourceKeys.demoDataGenerationIteration)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("data_collection_iteration")
                 .field(ChangeSourceKeys.type)
                 .field(ChangeSourceKeys.dataCollectionTaskIteration)
                 .build())
        .build();
  }

  @Id String uuid;
  long createdAt;
  long lastUpdatedAt;

  @NotNull String accountId;
  @NotNull String name;
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;

  @NotNull String serviceIdentifier;
  @NotNull String envIdentifier;

  @NotNull String identifier;
  @NotNull ChangeSourceType type;

  boolean enabled;
  boolean isConfiguredForDemo;

  @FdIndex String dataCollectionTaskId;
  Long demoDataGenerationIteration;
  Long dataCollectionTaskIteration;

  @Getter(AccessLevel.NONE) boolean dataCollectionRequired;

  public boolean isDataCollectionRequired() {
    return false;
  }

  public abstract static class UpdatableChangeSourceEntity<T extends ChangeSource, D extends ChangeSource>
      implements UpdatableEntity<T, D> {
    protected void setCommonOperations(UpdateOperations<T> updateOperations, D changeSource) {
      updateOperations.set(ChangeSourceKeys.accountId, changeSource.getAccountId())
          .set(ChangeSourceKeys.orgIdentifier, changeSource.getOrgIdentifier())
          .set(ChangeSourceKeys.projectIdentifier, changeSource.getProjectIdentifier())
          .set(ChangeSourceKeys.serviceIdentifier, changeSource.getServiceIdentifier())
          .set(ChangeSourceKeys.envIdentifier, changeSource.getEnvIdentifier())
          .set(ChangeSourceKeys.identifier, changeSource.getIdentifier())
          .set(ChangeSourceKeys.type, changeSource.getType())
          .set(ChangeSourceKeys.name, changeSource.getName())
          .set(ChangeSourceKeys.enabled, changeSource.isEnabled());
    }
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (ChangeSourceKeys.dataCollectionTaskIteration.equals(fieldName)) {
      this.dataCollectionTaskIteration = nextIteration;
      return;
    } else if (ChangeSourceKeys.demoDataGenerationIteration.equals(fieldName)) {
      this.demoDataGenerationIteration = nextIteration;
      return;
    } else {
      throw new IllegalArgumentException("Invalid fieldName " + fieldName);
    }
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (ChangeSourceKeys.dataCollectionTaskIteration.equals(fieldName)) {
      return this.dataCollectionTaskIteration;
    } else if (ChangeSourceKeys.demoDataGenerationIteration.equals(fieldName)) {
      return this.demoDataGenerationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public boolean isEligibleForDemo() {
    return this.identifier.endsWith("_dev");
  }

  public boolean shouldGenerateAutoDemoEvents() {
    return !getIdentifier().contains("noautoevents");
  }
}
