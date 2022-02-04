/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.execution.NodeExecution.NodeExecutionKeys;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.engine.outcomes.OutcomeException;
import io.harness.engine.outputs.SweepingOutputException;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.LateBindingMap;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * NodeExecutionMap resolves expressions for a single node execution.
 *
 * Suppose the current node has identifier `node1` and we see an expression `node1.child1`:
 * 1. We first try to find a child with identifier `child1`
 * 2. Then we try to find a property of node1's step parameters with name `child1`
 * 3. Then we try to find an outcome in node1's scope with name `child1`
 * 4. Then we try to find an sweeping output in node1's scope with name `child1`
 */
@OwnedBy(CDC)
@Value
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NodeExecutionMap extends LateBindingMap {
  transient NodeExecutionsCache nodeExecutionsCache;
  transient PmsOutcomeService pmsOutcomeService;
  transient PmsSweepingOutputService pmsSweepingOutputService;
  transient Ambiance ambiance;
  transient NodeExecution nodeExecution;
  transient Set<NodeExecutionEntityType> entityTypes;
  transient Map<String, Object> children;

  @Builder
  NodeExecutionMap(NodeExecutionsCache nodeExecutionsCache, PmsOutcomeService pmsOutcomeService,
      PmsSweepingOutputService pmsSweepingOutputService, Ambiance ambiance, NodeExecution nodeExecution,
      Set<NodeExecutionEntityType> entityTypes, Map<String, Object> children) {
    this.nodeExecutionsCache = nodeExecutionsCache;
    this.pmsOutcomeService = pmsOutcomeService;
    this.pmsSweepingOutputService = pmsSweepingOutputService;
    this.ambiance = ambiance;
    this.nodeExecution = nodeExecution;
    this.entityTypes = entityTypes == null ? NodeExecutionEntityType.allEntities() : entityTypes;
    if (children == null) {
      this.children = Collections.emptyMap();
    } else {
      this.children = new LateBindingMap();
      this.children.putAll(children);
    }
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }

    return fetchFirst(asList(this::fetchCurrentStatus, this::fetchChild, this::fetchNodeExecutionField,
                          this::fetchStepParameters, this::fetchOutcomeOrOutput),
        (String) key);
  }

  private Object fetchFirst(List<Function<String, Optional<Object>>> fns, String key) {
    if (EmptyPredicate.isEmpty(fns)) {
      return null;
    }

    for (Function<String, Optional<Object>> fn : fns) {
      Optional<Object> optional = fn.apply(key);
      if (optional.isPresent()) {
        return optional.get();
      }
    }
    return null;
  }

  private Optional<Object> fetchChild(String key) {
    return children.containsKey(key) ? Optional.of(children.get(key)) : Optional.empty();
  }

  // This function calculates final status of the node TILL now.
  private Optional<Object> fetchCurrentStatus(String key) {
    if (!key.equals(OrchestrationConstants.CURRENT_STATUS)) {
      return Optional.empty();
    }
    if (nodeExecution == null) {
      return Optional.empty();
    }
    List<NodeExecution> allChildren = nodeExecutionsCache.findAllChildren(nodeExecution.getUuid());
    List<NodeExecution> childrenNodesWithoutCurrentNodeList =
        allChildren.stream()
            .filter(node -> StatusUtils.finalStatuses().contains(node.getStatus()))
            .collect(Collectors.toList());
    Status status =
        OrchestrationUtils.calculateStatus(childrenNodesWithoutCurrentNodeList, ambiance.getPlanExecutionId());
    return Optional.of(status.name());
  }

  private Optional<Object> fetchNodeExecutionField(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.NODE_EXECUTION_FIELDS)) {
      return Optional.empty();
    }

    if (NodeExecutionKeys.status.equals(key)) {
      return nodeExecution.getStatus() == null ? Optional.empty() : Optional.of(nodeExecution.getStatus().name());
    } else if (NodeExecutionKeys.startTs.equals(key)) {
      return Optional.ofNullable(nodeExecution.getStartTs());
    } else if (NodeExecutionKeys.endTs.equals(key)) {
      return Optional.ofNullable(nodeExecution.getEndTs());
    } else {
      return Optional.empty();
    }
  }

  private Optional<Object> fetchStepParameters(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.STEP_PARAMETERS)) {
      return Optional.empty();
    }
    return ExpressionEvaluatorUtils.fetchField(extractFinalStepParameters(nodeExecution, nodeExecutionsCache), key);
  }

  private Optional<Object> fetchOutcomeOrOutput(String key) {
    if (nodeExecution == null
        || (!entityTypes.contains(NodeExecutionEntityType.OUTCOME)
            && !entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT))) {
      return Optional.empty();
    }

    Ambiance newAmbiance = nodeExecution.getAmbiance();
    if (newAmbiance == null) {
      return Optional.empty();
    }

    Optional<Object> value = fetchOutcome(newAmbiance, key);
    if (!value.isPresent()) {
      value = fetchSweepingOutput(newAmbiance, key);
    }
    return value;
  }

  private Optional<Object> fetchOutcome(Ambiance newAmbiance, String key) {
    if (!entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      return Optional.empty();
    }

    try {
      return jsonToObject(pmsOutcomeService.resolve(newAmbiance, RefObjectUtils.getOutcomeRefObject(key)));
    } catch (OutcomeException ignored) {
      return Optional.empty();
    }
  }

  private Optional<Object> fetchSweepingOutput(Ambiance newAmbiance, String key) {
    if (!entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      return Optional.empty();
    }

    try {
      return jsonToObject(
          pmsSweepingOutputService.resolve(newAmbiance, RefObjectUtils.getSweepingOutputRefObject(key)));
    } catch (SweepingOutputException ignored) {
      return Optional.empty();
    }
  }

  private static Map<String, Object> extractFinalStepParameters(
      NodeExecution nodeExecution, NodeExecutionsCache nodeExecutionsCache) {
    if (nodeExecution.getResolvedStepParameters() != null) {
      Map<String, Object> stepParameters = NodeExecutionUtils.extractAndProcessObject(
          RecastOrchestrationUtils.toJson(nodeExecution.getResolvedStepParameters()));
      if (stepParameters != null) {
        return stepParameters;
      }
    }
    Node node = nodeExecutionsCache.fetchNode(nodeExecution.nodeId());
    return NodeExecutionUtils.extractAndProcessObject(node.getStepParameters().toJson());
  }

  private static Optional<Object> jsonToObject(String json) {
    return Optional.ofNullable(NodeExecutionUtils.extractAndProcessObject(json));
  }
}
