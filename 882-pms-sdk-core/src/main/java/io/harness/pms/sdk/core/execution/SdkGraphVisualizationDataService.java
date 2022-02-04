/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.steps.executables.StepDetailsInfo;

@OwnedBy(HarnessTeam.PIPELINE)
public interface SdkGraphVisualizationDataService {
  void publishStepDetailInformation(Ambiance ambiance, StepDetailsInfo stepDetailsInfo, String name);
  void publishStepDetailInformation(
      Ambiance ambiance, StepDetailsInfo stepDetailsInfo, String name, StepCategory category);
}
