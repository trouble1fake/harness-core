/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.timeout.TimeoutDetails;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "NodeExecutionKeys")
@Entity(value = "nodeExecutions", noClassnameStored = true)
@Document("nodeExecutions")
@TypeAlias("nodeExecution")
@StoreIn(DbAliases.PMS)
public class NodeExecution implements PersistentEntity, UuidAccess, PmsNodeExecution {
  public static final long TTL_MONTHS = 6;

  // Immutable
  @Wither @Id @org.mongodb.morphia.annotations.Id String uuid;
  @NotNull Ambiance ambiance;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) Node planNode;
  @NotNull ExecutionMode mode;
  @Wither @FdIndex @CreatedDate Long createdAt;
  private Long startTs;
  private Long endTs;
  private Duration initialWaitDuration;
  private Integer levelCount;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());

  // Resolved StepParameters stored just before invoking step.
  @Deprecated Map<String, Object> resolvedStepParameters;
  @Deprecated PmsStepParameters resolvedInputs;

  PmsStepParameters resolvedParams;

  // For Wait Notify
  String notifyId;

  // Relationships
  String parentId;
  String nextId;
  String previousId;

  // Mutable
  @Wither @LastModifiedDate Long lastUpdatedAt;
  Status status;
  @Wither @Version Long version;

  @Singular List<ExecutableResponse> executableResponses;
  @Singular List<InterruptEffect> interruptHistories;
  FailureInfo failureInfo;
  SkipInfo skipInfo;
  NodeRunInfo nodeRunInfo;

  // Retries
  @Singular List<String> retryIds;
  boolean oldRetry;

  // Timeout
  List<String> timeoutInstanceIds;
  TimeoutDetails timeoutDetails;

  // Todo: Move unitProgress and progressData to another collection
  @Singular @Deprecated List<UnitProgress> unitProgresses;
  Map<String, Object> progressData;

  AdviserResponse adviserResponse;
  // Timeouts for advisers
  List<String> adviserTimeoutInstanceIds;
  TimeoutDetails adviserTimeoutDetails;

  // If this is a retry node then this field is populated
  String originalNodeExecutionId;

  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) SkipType skipGraphType;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) String module;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) String name;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) StepType stepType;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) String nodeId;
  @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) String identifier;

  public ExecutableResponse obtainLatestExecutableResponse() {
    if (isEmpty(executableResponses)) {
      return null;
    }
    return executableResponses.get(executableResponses.size() - 1);
  }

  @Override
  public String nodeId() {
    if (isNotEmpty(nodeId)) {
      return nodeId;
    }
    return AmbianceUtils.obtainCurrentSetupId(ambiance);
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.valueOf(AmbianceUtils.obtainNodeType(ambiance));
  }

  // For this release keeping this to fetched from plan node clear from next release
  // This should be changed from getting it via current level
  public SkipType skipGraphType() {
    if (isNotEmpty(module)) {
      return skipGraphType;
    }
    return planNode.getSkipGraphType();
  }

  // For this release keeping this to fetched from plan node clear from next release
  // This should be changed from getting it via current level
  public String module() {
    if (isNotEmpty(module)) {
      return module;
    }
    return planNode.getServiceName();
  }

  public String name() {
    if (isNotEmpty(name)) {
      return name;
    }
    return planNode.getName();
  }

  public String getPlanExecutionId() {
    return ambiance.getPlanExecutionId();
  }

  public StepType stepType() {
    if (stepType != null) {
      return stepType;
    }
    return AmbianceUtils.getCurrentStepType(ambiance);
  }

  public String identifier() {
    if (isNotEmpty(identifier)) {
      return identifier;
    }
    return AmbianceUtils.obtainStepIdentifier(ambiance);
  }

  @UtilityClass
  public static class NodeExecutionKeys {
    public static final String id = "_id";
    public static final String planExecutionId = NodeExecutionKeys.ambiance + "."
        + "planExecutionId";

    public static final String planNodeId = NodeExecutionKeys.planNode + "."
        + "uuid";

    public static final String planNodeIdentifier = NodeExecutionKeys.planNode + "."
        + "identifier";

    public static final String planNodeStepCategory = NodeExecutionKeys.planNode + "."
        + "stepType"
        + "."
        + "stepCategory";

    public static final String stageFqn = NodeExecutionKeys.planNode + "."
        + "stageFqn";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_planNodeId_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.planNodeId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_planNodeIdentifier_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.planNodeIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_oldRetry_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_notifyId_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.notifyId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_status_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_parentId_status_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.parentId)
                 .field(NodeExecutionKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("parentId_status_idx")
                 .field(NodeExecutionKeys.parentId)
                 .field(NodeExecutionKeys.status)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_mode_status_oldRetry_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.mode)
                 .field(NodeExecutionKeys.status)
                 .field(NodeExecutionKeys.oldRetry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_stepCategory_planNodeIdentifier_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.planNodeStepCategory)
                 .field(NodeExecutionKeys.planNodeIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("planExecutionId_stageFqn_idx")
                 .field(NodeExecutionKeys.planExecutionId)
                 .field(NodeExecutionKeys.stageFqn)
                 .build())
        .add(CompoundMongoIndex.builder().name("previous_id_idx").field(NodeExecutionKeys.previousId).build())
        .build();
  }

  public ByteString getResolvedStepParametersBytes() {
    if (this.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE)) {
      String originalExId = originalNodeExecutionId;
      if (originalExId == null) {
        originalExId = ((IdentityPlanNode) this.getNode()).getOriginalNodeExecutionId();
      }
      // TODO: Remove this after one month
      IdentityStepParameters build = IdentityStepParameters.builder().originalNodeExecutionId(originalExId).build();
      return ByteString.copyFromUtf8(emptyIfNull(RecastOrchestrationUtils.toJson(build)));
    }
    String resolvedStepParams = RecastOrchestrationUtils.toJson(this.getResolvedStepParameters());
    return ByteString.copyFromUtf8(emptyIfNull(resolvedStepParams));
  }

  public PmsStepParameters getPmsStepParameters() {
    return PmsStepParameters.parse(resolvedInputs);
  }

  public OrchestrationMap getPmsProgressData() {
    return OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(progressData);
  }

  public PmsStepParameters getResolvedStepParameters() {
    if (resolvedStepParameters != null) {
      return PmsStepParameters.parse(
          OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedStepParameters));
    }
    return resolvedParams;
  }

  public <T extends Node> T getNode() {
    return (T) planNode;
  }
}
