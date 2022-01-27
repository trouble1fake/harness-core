/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.deployment;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.logging.LogCallback;

import software.wings.delegatetasks.azure.AzureServiceCallBack;
import software.wings.delegatetasks.azure.appservice.deployment.context.StatusVerifierContext;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class StartSlotStatusVerifier extends SlotStatusVerifier {
  public StartSlotStatusVerifier(LogCallback logCallback, String slotName, AzureWebClient azureWebClient,
      AzureWebClientContext azureWebClientContext, AzureServiceCallBack restCallBack) {
    super(logCallback, slotName, azureWebClient, azureWebClientContext, restCallBack);
  }

  public StartSlotStatusVerifier(StatusVerifierContext context) {
    super(context.getLogCallback(), context.getSlotName(), context.getAzureWebClient(),
        context.getAzureWebClientContext(), context.getRestCallBack());
  }

  @Override
  public String getSteadyState() {
    return SlotStatus.RUNNING.name();
  }
}
