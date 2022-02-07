/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.core;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
public interface ScopeService {
  long saveAll(@NotNull @Valid List<Scope> scopes);
  Scope getOrCreate(@NotNull @Valid Scope scope);
  boolean isPresent(@NotEmpty String identifier);
  Optional<Scope> deleteIfPresent(@NotEmpty String identifier);
  Scope buildScopeFromScopeIdentifier(@NotNull String identifier);

  boolean areScopeLevelsValid(@NotNull Set<String> scopeLevels);
  Set<String> getAllScopeLevels();
}
