/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.preference.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public class NoOpAccessControlPreferenceServiceImpl implements AccessControlPreferenceService {
  @Override
  public boolean isAccessControlEnabled(String accountIdentifier) {
    return true;
  }

  @Override
  public boolean upsertAccessControlEnabled(String accountIdentifier, boolean enabled) {
    return true;
  }
}
