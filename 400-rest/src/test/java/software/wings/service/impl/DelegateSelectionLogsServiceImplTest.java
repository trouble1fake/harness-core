/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.WingsBaseTest;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("io.harness.beans.Cd1SetupFields")
@BreakDependencyOn("software.wings.WingsBaseTest")
@BreakDependencyOn("software.wings.beans.Application")
@BreakDependencyOn("software.wings.beans.Environment")
@BreakDependencyOn("software.wings.beans.Service")
@BreakDependencyOn("software.wings.dl.WingsPersistence")
public class DelegateSelectionLogsServiceImplTest extends WingsBaseTest {}
