/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.deployment;

import io.harness.azure.impl.SlotLogStreamer;

import software.wings.delegatetasks.azure.appservice.deployment.context.SlotDeploymentVerifierContext;

public class SlotDeploymentVerifier extends SlotStatusVerifier {
  private final SlotLogStreamer logStreamer;

  public SlotDeploymentVerifier(SlotDeploymentVerifierContext context) {
    super(context.getLogCallback(), context.getSlotName(), context.getAzureWebClient(),
        context.getAzureWebClientContext(), null);
    this.logStreamer = context.getLogStreamer();
  }

  @Override
  public boolean hasReachedSteadyState() {
    return logStreamer == null || logStreamer.operationCompleted();
  }
  @Override
  public String getSteadyState() {
    return null;
  }

  @Override
  public boolean operationFailed() {
    if (logStreamer == null) {
      return false;
    }
    return logStreamer.operationFailed();
  }

  @Override
  public String getErrorMessage() {
    return logStreamer.getErrorLog();
  }

  @Override
  public void stopPolling() {
    logStreamer.unsubscribe();
  }
}
