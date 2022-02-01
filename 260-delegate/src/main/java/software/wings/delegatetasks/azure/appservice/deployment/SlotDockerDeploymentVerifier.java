/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.deployment;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.azure.impl.AzureLogStreamer;

import software.wings.delegatetasks.azure.appservice.deployment.context.SlotDockerDeploymentVerifierContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SlotDockerDeploymentVerifier extends SlotStatusVerifier {
  private AzureLogStreamer logStreamer;

  public SlotDockerDeploymentVerifier(SlotDockerDeploymentVerifierContext context) {
    super(context.getLogCallback(), context.getSlotName(), context.getAzureWebClient(),
        context.getAzureWebClientContext(), null);
    initializeLogStreamer();
  }

  private void initializeLogStreamer() {
    try {
      this.logStreamer = new AzureLogStreamer(azureWebClientContext, azureWebClient, slotName, logCallback, true);
      ExecutorService executorService = Executors.newFixedThreadPool(1);
      executorService.submit(logStreamer);
      executorService.shutdown();
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          color(
              String.format(
                  "Failed to stream the deployment logs from slot - [%s] due to %n [%s]. %nPlease verify the status of deployment manually",
                  slotName, ex.getMessage()),
              White, Bold),
          INFO, SUCCESS);
    }
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
    return logStreamer == null ? "Failed to initialize the log streaming" : logStreamer.getErrorLog();
  }

  @Override
  public void stopPolling() {
    logStreamer.unsubscribe();
  }
}
