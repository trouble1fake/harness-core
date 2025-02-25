/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.command.CommandUnitType.PCF_DUMMY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.CommandExecutionStatus;

import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDP)
@TargetModule(_870_CG_ORCHESTRATION)
public class PcfDummyCommandUnit extends AbstractCommandUnit {
  public PcfDummyCommandUnit(String name) {
    super(PCF_DUMMY);
    setName(name);
  }

  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    throw new NotImplementedException("Not implemented");
  }
}
