/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.SCHEMA_VALIDATION_FAILED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

@OwnedBy(PIPELINE)
public class JsonSchemaValidationException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public JsonSchemaValidationException(String message) {
    super(message, null, SCHEMA_VALIDATION_FAILED, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public JsonSchemaValidationException(String message, Throwable cause) {
    super(message, cause, SCHEMA_VALIDATION_FAILED, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
