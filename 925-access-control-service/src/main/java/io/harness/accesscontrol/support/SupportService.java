/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.support;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Optional;
import java.util.Set;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
public interface SupportService {
  SupportPreference fetchSupportPreference(@NotEmpty String accountIdentifier);
  SupportPreference syncSupportPreferenceFromRemote(@NotEmpty String accountIdentifier);
  Optional<SupportPreference> deleteSupportPreferenceIfPresent(@NotEmpty String accountIdentifier);
  Set<String> fetchSupportUsers();
}
