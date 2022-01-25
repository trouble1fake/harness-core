/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;

import lombok.Data;

@Data
@OwnedBy(HarnessTeam.CDP)
public class NexusServerRuntimeException extends RuntimeException {
  private String message;
  Throwable cause;
  ErrorCode code = ErrorCode.INVALID_CREDENTIAL;

  public NexusServerRuntimeException(String message) {
    this.message = message;
  }

  public NexusServerRuntimeException(String message, ErrorCode code) {
    this.message = message;
    this.code = code;
  }

  public NexusServerRuntimeException(String message, Throwable cause) {
    this.message = message;
    this.cause = cause;
  }

  public NexusServerRuntimeException(String message, Throwable cause, ErrorCode code) {
    this.message = message;
    this.cause = cause;
    this.code = code;
  }
}
