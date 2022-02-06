/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.migration;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.ng.core.migration.DeleteCVSetupUsageEventsMigration;
import io.harness.ng.core.migration.NGAccountSettingsMigration;
import io.harness.ng.core.migration.NGDefaultOrgNameMigration;
import io.harness.ng.core.migration.NGDefaultSMNameMigration;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(DX)
public class NGCoreMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.MongoMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, NoopNGCoreMigration.class))
        .add(Pair.of(2, NoopNGCoreMigration.class))
        .add(Pair.of(3, NoopNGCoreMigration.class))
        .add(Pair.of(4, DeleteCVSetupUsageEventsMigration.class))
        .add(Pair.of(5, NGDefaultSMNameMigration.class))
        .add(Pair.of(6, NGDefaultOrgNameMigration.class))
        .add(Pair.of(7, NGAccountSettingsMigration.class))
        .build();
  }
}
