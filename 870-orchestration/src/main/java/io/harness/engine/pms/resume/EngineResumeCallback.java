/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.ResponseDataMapper;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
public class EngineResumeCallback implements OldNotifyCallback {
  @Inject OrchestrationEngine orchestrationEngine;
  @Inject ResponseDataMapper responseDataMapper;

  Ambiance ambiance;

  @Override
  public void notify(Map<String, ResponseData> response) {
    notifyWithError(response, false);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notifyWithError(response, true);
  }

  private void notifyWithError(Map<String, ResponseData> response, boolean asyncError) {
    Map<String, ByteString> byteStringMap = responseDataMapper.toResponseDataProto(response);
    orchestrationEngine.resumeNodeExecution(ambiance, byteStringMap, false);
  }
}
