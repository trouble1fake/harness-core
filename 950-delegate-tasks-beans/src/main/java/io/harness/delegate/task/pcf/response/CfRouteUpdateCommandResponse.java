/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.response;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.task.pcf.CfCommandResponse;
import io.harness.logging.CommandExecutionStatus;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@OwnedBy(CDP)
public class CfRouteUpdateCommandResponse extends CfCommandResponse {
  private CfInBuiltVariablesUpdateValues updatedValues;

  @Builder
  public CfRouteUpdateCommandResponse(
      CommandExecutionStatus commandExecutionStatus, String output, CfInBuiltVariablesUpdateValues updateValues) {
    super(commandExecutionStatus, output);
    this.updatedValues = updateValues;
  }
}
