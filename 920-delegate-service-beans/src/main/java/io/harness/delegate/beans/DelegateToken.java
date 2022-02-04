/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static java.time.Duration.ofDays;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.NameAndValueAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@Entity(value = "delegateTokens", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "DelegateTokenKeys")
@OwnedBy(HarnessTeam.DEL)
@StoreIn(DbAliases.ALL)
public class DelegateToken implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, NameAndValueAccess {
  public static final Duration TTL = ofDays(30);

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .field(DelegateTokenKeys.accountId)
                 .field(DelegateTokenKeys.name)
                 .unique(true)
                 .name("byAccountAndName")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .field(DelegateTokenKeys.accountId)
                 .field(DelegateTokenKeys.status)
                 .name("byAccountAndStatus")
                 .build())
        .build();
  }

  @Id @NotNull private String uuid;
  @NotEmpty private String accountId;
  @NotEmpty private String name;
  private EmbeddedUser createdBy;
  private long createdAt;
  private DelegateTokenStatus status;
  private String value;
  private boolean isNg;
  private DelegateEntityOwner owner;

  @FdTtlIndex private Date validUntil;
}
