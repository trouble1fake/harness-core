/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.DELEGATE_ERROR_HANDLER_EXCEPTION;

import io.harness.eraro.Level;

public class DelegateErrorHandlerException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public DelegateErrorHandlerException(String message) {
    super(message, null, DELEGATE_ERROR_HANDLER_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public DelegateErrorHandlerException(String message, Throwable cause) {
    super(message, cause, DELEGATE_ERROR_HANDLER_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
