/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.outcomes;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.ENGINE_OUTCOME_EXCEPTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

@OwnedBy(PIPELINE)
@SuppressWarnings("squid:CallToDeprecatedMethod")
public class OutcomeException extends WingsException {
  private static final String DETAILS_KEY = "details";

  public OutcomeException(String message) {
    super(message, null, ENGINE_OUTCOME_EXCEPTION, Level.ERROR, null, null);
    super.param(DETAILS_KEY, message);
  }

  public OutcomeException(String message, Throwable cause) {
    super(message, cause, ENGINE_OUTCOME_EXCEPTION, Level.ERROR, null, null);
    super.param(DETAILS_KEY, message);
  }
}
