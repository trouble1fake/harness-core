/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.variable.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.data.validator.Trimmed;
import io.harness.delegate.beans.variable.VariableType;
import io.harness.delegate.beans.variable.VariableType;
import io.harness.gitsync.persistance.GitSyncableEntity;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@FieldNameConstants(innerTypeName = "VariableKeys")
@Entity(value = "variables", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.NG_MANAGER)
@Document("variables")
@Persistent
@OwnedBy(HarnessTeam.DX)
public abstract class Variable implements PersistentEntity, NGAccountAccess, GitSyncableEntity {
    @Id @org.mongodb.morphia.annotations.Id String id;
    @NotEmpty @EntityIdentifier String identifier;
    @NotEmpty @NGEntityName String name;
    @NotEmpty io.harness.encryption.Scope scope;
    String description;
    @Trimmed @NotEmpty String accountIdentifier;
    @Trimmed String orgIdentifier;
    @Trimmed String projectIdentifier;
    @NotEmpty String fullyQualifiedIdentifier;
    @NotEmpty VariableType type;
    @NotNull @Singular @Size(max = 128) List<NGTag> tags;
    @CreatedBy private EmbeddedUser createdBy;
    @LastModifiedBy private EmbeddedUser lastUpdatedBy;
    @CreatedDate Long createdAt;
    @LastModifiedDate Long lastModifiedAt;
    @Version Long version;
    Long timeWhenVariableIsLastUpdated;
    Boolean deleted = Boolean.FALSE;
    String objectIdOfYaml;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) Boolean isEntityInvalid;
    String invalidYamlString; // TODO: remove this field after RenameInvalidYamlStringToYamlMigration runs
    String yaml;

    public void setEntityInvalid(boolean isEntityInvalid) {
        this.isEntityInvalid = isEntityInvalid;
    }

    public boolean isEntityInvalid() {
        return Boolean.TRUE.equals(isEntityInvalid);
    }

    @Override
    public String getAccountIdentifier() {
        return accountIdentifier;
    }

    public static final String VARIABLE_COLLECTION_NAME = "variables";

    @Override
    public String getUuid() {
        return getId();
    }

    public static List<MongoIndex> mongoIndexes() {
        return ImmutableList.<MongoIndex>builder()
                .add(CompoundMongoIndex.builder()
                        .name("accountId_orgId_projectId_name_Index")
                        .fields(Arrays.asList(VariableKeys.accountIdentifier, VariableKeys.orgIdentifier,
                                VariableKeys.projectIdentifier, VariableKeys.name))
                        .build())
                .add(CompoundMongoIndex.builder()
                        .name("fullyQualifiedIdentifier_deleted_Index")
                        .fields(Arrays.asList(VariableKeys.fullyQualifiedIdentifier, VariableKeys.deleted))
                        .build())
                .build();
    }
}
