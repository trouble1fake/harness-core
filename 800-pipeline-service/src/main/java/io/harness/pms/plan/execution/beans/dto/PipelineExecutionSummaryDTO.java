/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.execution.ExecutionErrorInfo;
import io.harness.pms.contracts.governance.GovernanceMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.bson.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("PipelineExecutionSummary")
@Schema(name = "PipelineExecutionSummary", description = "This is the view of the Pipeline Execution Summary")
public class PipelineExecutionSummaryDTO {
  String pipelineIdentifier;
  String planExecutionId;
  String name;

  ExecutionStatus status;

  List<NGTag> tags;

  ExecutionTriggerInfo executionTriggerInfo;
  ExecutionErrorInfo executionErrorInfo;
  GovernanceMetadata governanceMetadata;

  Map<String, Document> moduleInfo;
  Map<String, GraphLayoutNodeDTO> layoutNodeMap;
  List<String> modules;
  String startingNodeId;

  Long startTs;
  Long endTs;
  Long createdAt;

  boolean canRetry;
  boolean showRetryHistory;

  int runSequence;
  long successfulStagesCount;
  long runningStagesCount;
  long failedStagesCount;
  long totalStagesCount;
  EntityGitDetails gitDetails;

  boolean isStagesExecution;
  List<String> stagesExecuted;
  Map<String, String> stagesExecutedNames;
  boolean allowStageExecutions;
}
