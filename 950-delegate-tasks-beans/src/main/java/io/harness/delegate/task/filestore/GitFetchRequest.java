/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.filestore;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder

// I think that this class is used as a Task to retrieve files from (in the case of the gitFetchRequest) git.
// Hopefully this will be used to retrieve the files from artifactory as a Task. Also hopefully this will be invoked
// when we need to download files from artifactory.
public class GitFetchRequest implements ActivityAccess, TaskParameters, ExecutionCapabilityDemander {
  private List<io.harness.delegate.task.filestore.FileStoreFetchFilesConfig> artifactoryFetchFilesConfigs;
  private String executionLogName;
  private String accountId;
  private String activityId;
  @Builder.Default private boolean shouldOpenLogStream = true;
  private boolean closeLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    // TODO: Check with Tudor about this capabities too
    //        for (FileStoreFetchFilesConfig config : artifactoryFetchFilesConfigs) {
    //            ArtifactoryStoreDelegateConfig storeConfig = config.getStoreDelegateConfig();
    //            capabilities.addAll(ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(
    //                    storeConfig.getArtifactoryConnector(), maskingEvaluator));
    //            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
    //                    storeConfig.getEncryptedDataDetails(), maskingEvaluator));
    //        }
    return capabilities;
  }
}
