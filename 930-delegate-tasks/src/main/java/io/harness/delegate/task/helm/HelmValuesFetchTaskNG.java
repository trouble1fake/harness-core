/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class HelmValuesFetchTaskNG extends AbstractDelegateRunnableTask {
  @Inject private HelmTaskHelperBase helmTaskHelperBase;

  public HelmValuesFetchTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("This method is deprecated. Use run(TaskParameters) instead.");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    HelmValuesFetchRequest helmValuesFetchRequest = (HelmValuesFetchRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    log.info(format("Running HelmValuesFetchTaskNG for account %s", helmValuesFetchRequest.getAccountId()));

    LogCallback logCallback = getLogCallback(commandUnitsProgress);
    HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
        helmValuesFetchRequest.getHelmChartManifestDelegateConfig();
    try {
      helmTaskHelperBase.decryptEncryptedDetails(helmChartManifestDelegateConfig);

      String valuesFileContent = helmTaskHelperBase.fetchValuesYamlFromChart(
          helmChartManifestDelegateConfig, helmValuesFetchRequest.getTimeout(), logCallback);

      if (helmValuesFetchRequest.isCloseLogStream()) {
        logCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);
      }
      return HelmValuesFetchResponse.builder()
          .commandExecutionStatus(SUCCESS)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .valuesFileContent(valuesFileContent)
          .build();
    } catch (Exception e) {
      String exceptionMsg = e.getMessage() == null ? ExceptionUtils.getMessage(e) : e.getMessage();
      String msg = "HelmValuesFetchTaskNG execution failed with exception " + exceptionMsg;
      log.error(msg, e);
      logCallback.saveExecutionLog(msg, INFO, FAILURE);
      throw new TaskNGDataException(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), e);
    }
  }

  public LogCallback getLogCallback(CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(
        getLogStreamingTaskClient(), K8sCommandUnitConstants.FetchFiles, true, commandUnitsProgress);
  }
}
