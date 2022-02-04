/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.ExecutionEngineDispatcher;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class RetryHelper {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private OrchestrationEngine engine;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;

  // Just ignoring step parameters for now as this is not supported will revisit this when we need to support it
  public void retryNodeExecution(
      String nodeExecutionId, StepParameters parameters, String interruptId, InterruptConfig interruptConfig) {
    NodeExecution nodeExecution = Preconditions.checkNotNull(nodeExecutionService.get(nodeExecutionId));
    PlanNode node = nodeExecution.getNode();
    String newUuid = generateUuid();

    Ambiance oldAmbiance = nodeExecution.getAmbiance();
    NodeExecution updatedRetriedNode = updateRetriedNodeMetadata(nodeExecution);

    Level currentLevel = AmbianceUtils.obtainCurrentLevel(oldAmbiance);
    Ambiance ambiance = AmbianceUtils.cloneForFinish(oldAmbiance);
    int newRetryIndex = currentLevel != null ? currentLevel.getRetryIndex() + 1 : 0;
    ambiance = ambiance.toBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(newUuid, newRetryIndex, node)).build();
    NodeExecution newNodeExecution = cloneForRetry(updatedRetriedNode, newUuid, ambiance, interruptConfig, interruptId);
    NodeExecution savedNodeExecution = nodeExecutionService.save(newNodeExecution);

    nodeExecutionService.updateRelationShipsForRetryNode(updatedRetriedNode.getUuid(), savedNodeExecution.getUuid());
    nodeExecutionService.markRetried(updatedRetriedNode.getUuid());
    executorService.submit(ExecutionEngineDispatcher.builder().ambiance(ambiance).orchestrationEngine(engine).build());
  }

  private NodeExecution updateRetriedNodeMetadata(NodeExecution nodeExecution) {
    NodeExecution updatedNodeExecution = updateRetriedNodeStatusIfInterventionWaiting(nodeExecution);
    if (updatedNodeExecution != null && updatedNodeExecution.getEndTs() == null) {
      updatedNodeExecution = nodeExecutionService.update(
          updatedNodeExecution.getUuid(), ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()));
    }
    return updatedNodeExecution == null ? nodeExecution : updatedNodeExecution;
  }

  // Update the status of older retried node to true status from interventionWaiting if retry is on intervention waiting
  // node.
  private NodeExecution updateRetriedNodeStatusIfInterventionWaiting(NodeExecution nodeExecution) {
    if (nodeExecution.getStatus() == Status.INTERVENTION_WAITING
        && nodeExecution.getAdviserResponse().hasInterventionWaitAdvise()) {
      InterventionWaitAdvise interventionWaitAdvise = nodeExecution.getAdviserResponse().getInterventionWaitAdvise();
      NodeExecution updatedNodeExecution =
          nodeExecutionService.updateStatusWithOps(nodeExecution.getUuid(), interventionWaitAdvise.getFromStatus(),
              ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()), EnumSet.noneOf(Status.class));
      if (updatedNodeExecution == null) {
        log.warn("Cannot conclude node execution. Status update failed From :{}, To:{}", nodeExecution.getStatus(),
            interventionWaitAdvise.getFromStatus());
      }
      return updatedNodeExecution;
    }
    return nodeExecution;
  }

  @VisibleForTesting
  NodeExecution cloneForRetry(NodeExecution nodeExecution, String newUuid, Ambiance ambiance,
      InterruptConfig interruptConfig, String interruptId) {
    List<String> retryIds = isEmpty(nodeExecution.getRetryIds()) ? new LinkedList<>() : nodeExecution.getRetryIds();
    retryIds.add(nodeExecution.getUuid());
    InterruptConfig newInterruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(interruptConfig.getIssuedBy())
            .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().setRetryId(nodeExecution.getUuid()).build())
            .build();
    InterruptEffect interruptEffect = InterruptEffect.builder()
                                          .interruptType(InterruptType.RETRY)
                                          .tookEffectAt(System.currentTimeMillis())
                                          .interruptId(interruptId)
                                          .interruptConfig(newInterruptConfig)
                                          .build();

    List<InterruptEffect> interruptHistories =
        isEmpty(nodeExecution.getInterruptHistories()) ? new LinkedList<>() : nodeExecution.getInterruptHistories();
    interruptHistories.add(interruptEffect);
    return NodeExecution.builder()
        .uuid(newUuid)
        .ambiance(ambiance)
        .planNode(nodeExecution.getNode())
        .levelCount(ambiance.getLevelsCount())
        .mode(null)
        .startTs(AmbianceUtils.getCurrentLevelStartTs(ambiance))
        .endTs(null)
        .initialWaitDuration(null)
        .resolvedStepParameters(null)
        .resolvedParams(nodeExecution.getResolvedParams())
        .notifyId(nodeExecution.getNotifyId())
        .parentId(nodeExecution.getParentId())
        .nextId(nodeExecution.getNextId())
        .previousId(nodeExecution.getPreviousId())
        .lastUpdatedAt(null)
        .version(null)
        .executableResponses(new ArrayList<>())
        .interruptHistories(interruptHistories)
        .failureInfo(null)
        .status(Status.QUEUED)
        .timeoutInstanceIds(new ArrayList<>())
        .timeoutDetails(null)
        .retryIds(retryIds)
        .oldRetry(false)
        .originalNodeExecutionId(nodeExecution.getOriginalNodeExecutionId())
        .module(nodeExecution.module())
        .name(nodeExecution.name())
        .skipGraphType(nodeExecution.skipGraphType())
        .identifier(nodeExecution.identifier())
        .stepType(nodeExecution.stepType())
        .nodeId(nodeExecution.nodeId())
        .build();
  }
}
