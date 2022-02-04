/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp;

import static io.harness.azure.model.AzureConstants.SAVE_EXISTING_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.START_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.STOP_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.SUCCESS_REQUEST;
import static io.harness.azure.model.AzureConstants.UPDATE_DEPLOYMENT_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.azure.model.AzureConstants.UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConstants;

import lombok.Getter;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public enum AppServiceDeploymentProgress {
  SAVE_CONFIGURATION(SAVE_EXISTING_CONFIGURATIONS),
  STOP_SLOT(STOP_DEPLOYMENT_SLOT),
  UPDATE_SLOT_CONFIGURATIONS(UPDATE_DEPLOYMENT_SLOT_CONFIGURATION_SETTINGS),
  UPDATE_SLOT_CONTAINER_SETTINGS(UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS),
  DEPLOY_DOCKER_IMAGE(AzureConstants.DEPLOY_DOCKER_IMAGE),
  DEPLOY_ARTIFACT(AzureConstants.DEPLOY_ARTIFACT),
  START_SLOT(START_DEPLOYMENT_SLOT),
  UPDATE_TRAFFIC_PERCENT(SLOT_TRAFFIC_PERCENTAGE),
  SWAP_SLOT(SLOT_SWAP),
  DEPLOYMENT_COMPLETE(SUCCESS_REQUEST);

  @Getter private final String stepName;

  AppServiceDeploymentProgress(String stepName) {
    this.stepName = stepName;
  }
}
