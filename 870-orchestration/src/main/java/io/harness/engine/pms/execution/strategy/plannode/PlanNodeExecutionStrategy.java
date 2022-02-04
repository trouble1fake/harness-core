/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plannode;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.RUNNING;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.facilitation.FacilitationHelper;
import io.harness.engine.facilitation.RunPreFacilitationChecker;
import io.harness.engine.facilitation.SkipPreFacilitationChecker;
import io.harness.engine.facilitation.facilitator.publisher.FacilitateEventPublisher;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.pms.advise.AdviseHandlerFactory;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.execution.strategy.EndNodeExecutionHelper;
import io.harness.engine.pms.execution.strategy.NodeExecutionStrategy;
import io.harness.engine.pms.resume.NodeResumeHelper;
import io.harness.engine.pms.start.NodeStartHelper;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionMetadata;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.logging.AutoLogContext;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.expression.PmsEngineExpressionService;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.utils.OrchestrationMapBackwardCompatibilityUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanNodeExecutionStrategy
    implements NodeExecutionStrategy<PlanNode, NodeExecution, NodeExecutionMetadata> {
  @Inject private Injector injector;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private FacilitationHelper facilitationHelper;
  @Inject private ExceptionManager exceptionManager;
  @Inject private EndNodeExecutionHelper endNodeExecutionHelper;
  @Inject private NodeAdviseHelper nodeAdviseHelper;
  @Inject private FacilitateEventPublisher facilitateEventPublisher;
  @Inject private NodeStartHelper startHelper;
  @Inject private InterruptService interruptService;
  @Inject private AdviseHandlerFactory adviseHandlerFactory;
  @Inject private NodeResumeHelper resumeHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PmsOutcomeService outcomeService;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject PmsGraphStepDetailsService pmsGraphStepDetailsService;

  @Override
  public NodeExecution triggerNode(Ambiance ambiance, PlanNode node, NodeExecutionMetadata metadata) {
    String uuid = generateUuid();
    NodeExecution previousNodeExecution = null;
    if (AmbianceUtils.obtainCurrentRuntimeId(ambiance) != null) {
      previousNodeExecution = nodeExecutionService.update(AmbianceUtils.obtainCurrentRuntimeId(ambiance),
          ops -> ops.set(NodeExecutionKeys.nextId, uuid).set(NodeExecutionKeys.endTs, System.currentTimeMillis()));
    }
    Ambiance cloned = AmbianceUtils.cloneForFinish(ambiance, PmsLevelUtils.buildLevelFromNode(uuid, node));
    boolean skipUnresolvedExpressionsCheck = node.isSkipUnresolvedExpressionsCheck();
    log.info("Starting to Resolve step parameters and Inputs");
    Object resolvedStepParameters =
        pmsEngineExpressionService.resolve(ambiance, node.getStepParameters(), skipUnresolvedExpressionsCheck);
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(uuid)
            .planNode(node)
            .ambiance(cloned)
            .levelCount(cloned.getLevelsCount())
            .status(Status.QUEUED)
            .notifyId(previousNodeExecution == null ? null : previousNodeExecution.getNotifyId())
            .parentId(previousNodeExecution == null ? null : previousNodeExecution.getParentId())
            .previousId(previousNodeExecution == null ? null : previousNodeExecution.getUuid())
            .unitProgresses(new ArrayList<>())
            .startTs(AmbianceUtils.getCurrentLevelStartTs(cloned))
            .module(node.getServiceName())
            .resolvedParams(PmsStepParameters.parse(
                OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedStepParameters)))
            .name(node.getName())
            .skipGraphType(node.getSkipGraphType())
            .identifier(node.getIdentifier())
            .stepType(node.getStepType())
            .nodeId(node.getUuid())
            .build();
    NodeExecution save = nodeExecutionService.save(nodeExecution);
    // TODO: Should add to an execution queue rather than submitting straight to thread pool
    executorService.submit(() -> startExecution(cloned));
    return save;
  }

  @Override
  public void startExecution(Ambiance ambiance) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String nodeId = AmbianceUtils.obtainCurrentSetupId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      PlanNode planNode = planService.fetchNode(ambiance.getPlanId(), nodeId);
      ExecutionCheck check = performPreFacilitationChecks(ambiance, planNode);
      if (!check.isProceed()) {
        log.info("Not Proceeding with  Execution. Reason : {}", check.getReason());
        return;
      }
      log.info("Proceeding with  Execution. Reason : {}", check.getReason());

      resolveParameters(ambiance, planNode);

      if (facilitationHelper.customFacilitatorPresent(planNode)) {
        facilitateEventPublisher.publishEvent(ambiance, planNode);
        return;
      }
      FacilitatorResponseProto facilitatorResponseProto =
          facilitationHelper.calculateFacilitatorResponse(ambiance, planNode);
      processFacilitationResponse(ambiance, facilitatorResponseProto);
    } catch (Exception exception) {
      log.error("Exception Occurred in facilitateAndStartStep NodeExecutionId : {}, PlanExecutionId: {}",
          nodeExecutionId, ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void processFacilitationResponse(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      nodeExecutionService.updateV2(
          nodeExecutionId, ops -> ops.set(NodeExecutionKeys.mode, facilitatorResponse.getExecutionMode()));
      ExecutionCheck check = interruptService.checkInterruptsPreInvocation(
          ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance));
      if (!check.isProceed()) {
        log.info("Not Proceeding with Execution : {}", check.getReason());
        return;
      }
      startHelper.startNode(ambiance, facilitatorResponse);
    } catch (Exception exception) {
      log.error("Exception Occurred while processing facilitation response NodeExecutionId : {}, PlanExecutionId: {}",
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void processStartEventResponse(Ambiance ambiance, ExecutableResponse executableResponse) {}

  @Override
  public void resumeNodeExecution(Ambiance ambiance, Map<String, ByteString> response, boolean asyncError) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForResume);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      if (!StatusUtils.resumableStatuses().contains(nodeExecution.getStatus())) {
        log.warn("NodeExecution is no longer in RESUMABLE state Uuid: {} Status {} ", nodeExecution.getUuid(),
            nodeExecution.getStatus());
        return;
      }
      if (nodeExecution.getStatus() != RUNNING) {
        log.info("Marking the nodeExecution with id {} as RUNNING", nodeExecutionId);
        nodeExecution = Preconditions.checkNotNull(nodeExecutionService.updateStatusWithOpsV2(
            nodeExecutionId, RUNNING, null, EnumSet.noneOf(Status.class), NodeProjectionUtils.fieldsForResume));
      } else {
        log.warn("NodeExecution with id {} is already in Running status", nodeExecutionId);
      }
      resumeHelper.resume(nodeExecution, response, asyncError);
    } catch (Exception exception) {
      log.error("Exception Occurred in handling resume with nodeExecutionId {} planExecutionId {}", nodeExecutionId,
          ambiance.getPlanExecutionId(), exception);
      handleError(ambiance, exception);
    }
  }

  @Override
  public void concludeExecution(
      Ambiance ambiance, Status toStatus, Status fromStatus, EnumSet<Status> overrideStatusSet) {
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(ambiance));
    PlanNode node = planService.fetchNode(ambiance.getPlanId(), level.getSetupId());
    if (isEmpty(node.getAdviserObtainments())) {
      NodeExecution updatedNodeExecution =
          nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus, null, overrideStatusSet);
      if (updatedNodeExecution == null) {
        log.warn("Cannot conclude node execution. Status update failed To:{}", toStatus);
        return;
      }
      endNodeExecution(updatedNodeExecution.getAmbiance());
      return;
    }
    NodeExecution updatedNodeExecution = nodeExecutionService.updateStatusWithOps(level.getRuntimeId(), toStatus,
        ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()), overrideStatusSet);
    if (updatedNodeExecution == null) {
      log.warn("Cannot conclude node execution. Status update failed To:{}", toStatus);
      return;
    }
    nodeAdviseHelper.queueAdvisingEvent(updatedNodeExecution, node, fromStatus);
  }

  @Override
  public void processStepResponse(Ambiance ambiance, StepResponseProto stepResponse) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      handleStepResponseInternal(ambiance, stepResponse);
    } catch (Exception ex) {
      log.error("Exception Occurred in handleStepResponse NodeExecutionId : {}, PlanExecutionId: {}",
          AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId(), ex);
      handleError(ambiance, ex);
    }
  }

  @Override
  public void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      if (adviserResponse.getType() == AdviseType.UNKNOWN) {
        log.warn("Got null advise for node execution with id {}", nodeExecutionId);
        endNodeExecution(ambiance);
        return;
      }
      log.info("Starting to handle Adviser Response of type: {}", adviserResponse.getType());
      NodeExecution updatedNodeExecution = nodeExecutionService.update(
          nodeExecutionId, ops -> ops.set(NodeExecutionKeys.adviserResponse, adviserResponse));
      AdviserResponseHandler adviserResponseHandler = adviseHandlerFactory.obtainHandler(adviserResponse.getType());
      adviserResponseHandler.handleAdvise(updatedNodeExecution, adviserResponse);
    }
  }

  @Override
  public void endNodeExecution(Ambiance ambiance) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution nodeExecution = nodeExecutionService.update(
        nodeExecutionId, ops -> ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis()));
    if (isNotEmpty(nodeExecution.getNotifyId())) {
      Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
      StepResponseNotifyData responseData = StepResponseNotifyData.builder()
                                                .nodeUuid(level.getSetupId())
                                                .stepOutcomeRefs(outcomeService.fetchOutcomeRefs(nodeExecutionId))
                                                .failureInfo(nodeExecution.getFailureInfo())
                                                .identifier(level.getIdentifier())
                                                .group(level.getGroup())
                                                .status(nodeExecution.getStatus())
                                                .adviserResponse(nodeExecution.getAdviserResponse())
                                                .build();
      waitNotifyEngine.doneWith(nodeExecution.getNotifyId(), responseData);
    } else {
      log.info("Ending Execution");
      orchestrationEngine.endNodeExecution(AmbianceUtils.cloneForFinish(ambiance));
    }
  }

  @VisibleForTesting
  void handleStepResponseInternal(@NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse) {
    PlanNode planNode = planService.fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    if (isEmpty(planNode.getAdviserObtainments())) {
      log.info("No Advisers for the node Ending Execution");
      endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(ambiance, stepResponse);
      return;
    }
    // TODO: find a way to remove this and pass old status as parameter
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), NodeProjectionUtils.withStatus);
    NodeExecution updatedNodeExecution = endNodeExecutionHelper.handleStepResponsePreAdviser(ambiance, stepResponse);
    if (updatedNodeExecution == null) {
      return;
    }

    nodeAdviseHelper.queueAdvisingEvent(updatedNodeExecution, planNode, nodeExecution.getStatus());
  }

  @VisibleForTesting
  void resolveParameters(Ambiance ambiance, PlanNode node) {
    String nodeExecutionId = Objects.requireNonNull(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    boolean skipUnresolvedExpressionsCheck = node.isSkipUnresolvedExpressionsCheck();
    log.info("Starting to Resolve step parameters and Inputs");
    Object resolvedStepInputs =
        pmsEngineExpressionService.resolve(ambiance, node.getStepInputs(), skipUnresolvedExpressionsCheck);
    log.info("Step Parameters and Inputs Resolution complete");

    pmsGraphStepDetailsService.addStepInputs(nodeExecutionId, ambiance.getPlanExecutionId(),
        PmsStepParameters.parse(
            OrchestrationMapBackwardCompatibilityUtils.extractToOrchestrationMap(resolvedStepInputs)));
  }

  private ExecutionCheck performPreFacilitationChecks(Ambiance ambiance, PlanNode planNode) {
    // Ignore facilitation checks if node is retried

    if (AmbianceUtils.isRetry(ambiance)) {
      return ExecutionCheck.builder().proceed(true).reason("Node is retried.").build();
    }
    RunPreFacilitationChecker rChecker = injector.getInstance(RunPreFacilitationChecker.class);
    SkipPreFacilitationChecker sChecker = injector.getInstance(SkipPreFacilitationChecker.class);
    rChecker.setNextChecker(sChecker);
    return rChecker.check(ambiance, planNode);
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {
    try {
      StepResponseProto.Builder builder = StepResponseProto.newBuilder().setStatus(Status.FAILED);
      List<ResponseMessage> responseMessages = exceptionManager.buildResponseFromException(exception);
      if (isNotEmpty(responseMessages)) {
        builder.setFailureInfo(EngineExceptionUtils.transformResponseMessagesToFailureInfo(responseMessages));
      }
      handleStepResponseInternal(ambiance, builder.build());
    } catch (Exception ex) {
      // Smile if you see irony in this
      log.error("This is very BAD!!!. Exception Occurred while handling Exception. Erroring out Execution", ex);
    }
  }
}
