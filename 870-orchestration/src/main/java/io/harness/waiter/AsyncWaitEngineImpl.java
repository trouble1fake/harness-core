/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;

@OwnedBy(HarnessTeam.PIPELINE)
@TargetModule(HarnessModule._882_PMS_SDK_CORE)
public class AsyncWaitEngineImpl implements AsyncWaitEngine {
  private final WaitNotifyEngine waitNotifyEngine;
  private final String publisherName;

  public AsyncWaitEngineImpl(WaitNotifyEngine waitNotifyEngine, String publisherName) {
    this.waitNotifyEngine = waitNotifyEngine;
    this.publisherName = publisherName;
  }

  @Override
  public void waitForAllOn(NotifyCallback notifyCallback, ProgressCallback progressCallback, String... correlationIds) {
    waitNotifyEngine.waitForAllOn(publisherName, notifyCallback, progressCallback, correlationIds);
  }
}
