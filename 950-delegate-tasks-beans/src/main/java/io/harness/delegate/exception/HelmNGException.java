/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DataException;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = false)
public class HelmNGException extends DataException {
  int prevReleaseVersion;
  boolean isInstallUpgrade;

  public HelmNGException(int prevReleaseVersion, Throwable cause, boolean isInstallUpgrade) {
    super(cause);
    this.prevReleaseVersion = prevReleaseVersion;
    this.isInstallUpgrade = isInstallUpgrade;
  }
}
