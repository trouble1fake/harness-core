/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.NonNull;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class MarkStatusInterruptHandler implements InterruptHandler {
  @Inject protected NodeExecutionService nodeExecutionService;
  @Inject protected InterruptService interruptService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PlanExecutionService planExecutionService;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return handleInterruptForNodeExecution(savedInterrupt, interrupt.getNodeExecutionId());
  }

  private Interrupt validateAndSave(@Valid @NonNull Interrupt interrupt) {
    if (isEmpty(interrupt.getNodeExecutionId())) {
      throw new InvalidRequestException("NodeExecutionId Cannot be empty for MARK_SUCCESS interrupt");
    }

    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(interrupt.getNodeExecutionId(), NodeProjectionUtils.withStatus);
    if (!StatusUtils.brokeStatuses().contains(nodeExecution.getStatus())
        && nodeExecution.getStatus() != INTERVENTION_WAITING) {
      throw new InvalidRequestException(
          "NodeExecution is not in a finalizable or broken status. Current Status: " + nodeExecution.getStatus());
    }

    interrupt.setState(Interrupt.State.PROCESSING);
    return interruptService.save(interrupt);
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    throw new UnsupportedOperationException(interrupt.getType() + " handling Not required on plan");
  }

  protected Interrupt handleInterruptStatus(Interrupt interrupt, String nodeExecutionId, Status status) {
    return handleInterruptStatus(interrupt, nodeExecutionId, status, EnumSet.noneOf(Status.class));
  }

  protected Interrupt handleInterruptStatus(
      Interrupt interrupt, String nodeExecutionId, Status status, EnumSet<Status> overrideStatusSet) {
    try {
      NodeExecution nodeExecution = nodeExecutionService.update(nodeExecutionId,
          ops
          -> ops.addToSet(NodeExecutionKeys.interruptHistories,
              InterruptEffect.builder()
                  .interruptType(interrupt.getType())
                  .tookEffectAt(System.currentTimeMillis())
                  .interruptId(interrupt.getUuid())
                  .interruptConfig(interrupt.getInterruptConfig())
                  .build()));

      handlePlanStatus(interrupt.getPlanExecutionId(), nodeExecution.getUuid());
      orchestrationEngine.concludeNodeExecution(
          nodeExecution.getAmbiance(), status, nodeExecution.getStatus(), overrideStatusSet);
    } catch (Exception ex) {
      interruptService.markProcessed(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }
    return interruptService.markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);
  }

  private void handlePlanStatus(String planExecutionId, String nodeExecutionId) {
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusIn(
        planExecutionId, StatusUtils.activeStatuses());
    List<NodeExecution> filteredExecutions =
        nodeExecutions.stream().filter(ne -> !ne.getUuid().equals(nodeExecutionId)).collect(Collectors.toList());
    Status planStatus = OrchestrationUtils.calculateStatus(filteredExecutions, planExecutionId);
    if (!StatusUtils.isFinalStatus(planStatus)) {
      planExecutionService.updateStatus(planExecutionId, planStatus);
    }
  }
}
