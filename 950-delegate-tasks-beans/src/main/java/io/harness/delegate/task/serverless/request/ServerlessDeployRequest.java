/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessCliVersion;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessManifest;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessDeployRequest implements ServerlessCommandRequest {
  String accountId;
  String appId;
  String activityId;
  ServerlessCommandType serverlessCommandType;
  String commandName;
  ServerlessCliVersion serverlessCliVersion;
  String workingDirectory;
  String region;
  String stage;
  ServerlessArtifactConfig serverlessArtifactConfig;
  ServerlessManifest serverlessManifest;
  //     ServerlessInfraConfig serverlessInfraConfig;
}
