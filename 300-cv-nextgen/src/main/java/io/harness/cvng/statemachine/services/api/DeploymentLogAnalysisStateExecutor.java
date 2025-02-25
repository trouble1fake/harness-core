/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;

public class DeploymentLogAnalysisStateExecutor extends LogAnalysisStateExecutor<DeploymentLogAnalysisState> {
  @Override
  protected String scheduleAnalysis(AnalysisInput analysisInput) {
    return logAnalysisService.scheduleDeploymentLogAnalysisTask(analysisInput);
  }

  @Override
  public void handleFinalStatuses(DeploymentLogAnalysisState analysisState) {
    logAnalysisService.logDeploymentVerificationProgress(analysisState.getInputs(), analysisState.getStatus());
  }
}
