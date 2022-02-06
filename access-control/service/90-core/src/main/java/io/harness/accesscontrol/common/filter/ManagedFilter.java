/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.common.filter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Set;

@OwnedBy(HarnessTeam.PL)
public enum ManagedFilter {
  NO_FILTER,
  ONLY_MANAGED,
  ONLY_CUSTOM;

  public static ManagedFilter buildFromSet(Set<Boolean> managedFilterSet) {
    if (managedFilterSet.contains(Boolean.TRUE) && managedFilterSet.contains(Boolean.FALSE)) {
      return ManagedFilter.NO_FILTER;
    } else if (managedFilterSet.contains(Boolean.TRUE)) {
      return ManagedFilter.ONLY_MANAGED;
    } else if (managedFilterSet.contains(Boolean.FALSE)) {
      return ManagedFilter.ONLY_CUSTOM;
    }
    return ManagedFilter.NO_FILTER;
  }
}
