/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DataCollectionTaskDTO {
  String uuid;
  String accountId;
  String verificationTaskId;
  DataCollectionInfo dataCollectionInfo;
  Instant startTime;
  Instant endTime;

  @Value
  @Builder
  public static class DataCollectionTaskResult {
    String dataCollectionTaskId;
    DataCollectionExecutionStatus status;
    String exception;
    String stacktrace;
  }
}
