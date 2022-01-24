/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.exceptions;

import static io.harness.eraro.ErrorCode.INVALID_OVERLAY_INPUT_SET;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.pms.inputset.OverlayInputSetErrorWrapperDTOPMS;

@OwnedBy(HarnessTeam.PIPELINE)
public class InvalidOverlayInputSetException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public InvalidOverlayInputSetException(String message, OverlayInputSetErrorWrapperDTOPMS errorResponse) {
    super(message, null, INVALID_OVERLAY_INPUT_SET, Level.ERROR, null, null, errorResponse);
    super.param(MESSAGE_ARG, message);
  }
}
