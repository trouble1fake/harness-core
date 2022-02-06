/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public class NexusRegistryInvalidTagRuntimeRuntimeException extends NexusServerRuntimeException {
  public NexusRegistryInvalidTagRuntimeRuntimeException(String message) {
    super(message);
  }

  public NexusRegistryInvalidTagRuntimeRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
