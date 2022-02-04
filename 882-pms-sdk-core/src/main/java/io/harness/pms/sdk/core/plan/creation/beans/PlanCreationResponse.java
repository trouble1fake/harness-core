/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.async.AsyncCreatorResponse;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.plan.PlanNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
public class PlanCreationResponse implements AsyncCreatorResponse {
  @Singular @Deprecated Map<String, PlanNode> nodes;
  PlanNode planNode;
  Dependencies dependencies;
  YamlUpdates yamlUpdates;
  @Singular("contextMap") Map<String, PlanCreationContextValue> contextMap;
  GraphLayoutResponse graphLayoutResponse;

  String startingNodeId;
  @Singular List<String> errorMessages;

  public void merge(PlanCreationResponse other) {
    // adding PlanNode to map of nodes
    addNode(other.getPlanNode());

    addNodes(other.getNodes());
    addDependencies(other.getDependencies());
    mergeStartingNodeId(other.getStartingNodeId());
    mergeContext(other.getContextMap());
    mergeLayoutNodeInfo(other.getGraphLayoutResponse());
    addYamlUpdates(other.getYamlUpdates());
  }

  public void mergeContext(Map<String, PlanCreationContextValue> contextMap) {
    if (EmptyPredicate.isEmpty(contextMap)) {
      return;
    }
    for (Map.Entry<String, PlanCreationContextValue> entry : contextMap.entrySet()) {
      putContextValue(entry.getKey(), entry.getValue());
    }
  }

  public void mergeLayoutNodeInfo(GraphLayoutResponse layoutNodeInfo) {
    if (layoutNodeInfo == null) {
      return;
    }
    if (graphLayoutResponse == null) {
      graphLayoutResponse = layoutNodeInfo;
      return;
    }
    graphLayoutResponse.mergeStartingNodeId(layoutNodeInfo.getStartingNodeId());
    graphLayoutResponse.addLayoutNodes(layoutNodeInfo.getLayoutNodes());
  }

  public void addNodes(Map<String, PlanNode> newNodes) {
    if (EmptyPredicate.isEmpty(newNodes)) {
      return;
    }
    newNodes.values().forEach(this::addNode);
  }

  public void addNode(PlanNode newNode) {
    if (nodes == null) {
      nodes = new HashMap<>();
    } else if (!(nodes instanceof HashMap)) {
      nodes = new HashMap<>(nodes);
    }

    if (newNode == null) {
      return;
    }
    nodes.put(newNode.getUuid(), newNode);
    if (dependencies != null) {
      dependencies = dependencies.toBuilder().removeDependencies(newNode.getUuid()).build();

      // removing dependencyMetadata for this node id
      dependencies = dependencies.toBuilder().removeDependencyMetadata(newNode.getUuid()).build();
    }
  }

  public void addDependencies(Dependencies dependencies) {
    if (dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return;
    }
    dependencies.getDependenciesMap().forEach((key, value) -> addDependency(dependencies.getYaml(), key, value));

    // merging the dependencyMetadata
    dependencies.getDependencyMetadataMap().forEach(
        (key, value) -> addDependencyMetadata(dependencies.getYaml(), key, value));
  }

  public void addDependencyMetadata(String yaml, String nodeId, Dependency value) {
    if ((dependencies != null && dependencies.getDependencyMetadataMap().containsKey(nodeId))
        || (nodes != null && nodes.containsKey(nodeId))) {
      return;
    }

    if (value == null) {
      return;
    }
    if (dependencies == null) {
      dependencies = Dependencies.newBuilder().setYaml(yaml).putDependencyMetadata(nodeId, value).build();
      return;
    }
    dependencies = dependencies.toBuilder().putDependencyMetadata(nodeId, value).build();
  }

  public void putContextValue(String key, PlanCreationContextValue value) {
    if (contextMap != null && contextMap.containsKey(key)) {
      return;
    }

    if (contextMap == null) {
      contextMap = new HashMap<>();
    } else if (!(contextMap instanceof HashMap)) {
      contextMap = new HashMap<>(contextMap);
    }
    contextMap.put(key, value);
  }

  public void addDependency(String yaml, String nodeId, String yamlPath) {
    if ((dependencies != null && dependencies.getDependenciesMap().containsKey(nodeId))
        || (nodes != null && nodes.containsKey(nodeId))) {
      return;
    }
    if (dependencies == null) {
      dependencies = Dependencies.newBuilder().setYaml(yaml).putDependencies(nodeId, yamlPath).build();
      return;
    }
    dependencies = dependencies.toBuilder().putDependencies(nodeId, yamlPath).build();
  }

  public void mergeStartingNodeId(String otherStartingNodeId) {
    if (EmptyPredicate.isEmpty(otherStartingNodeId)) {
      return;
    }
    if (EmptyPredicate.isEmpty(startingNodeId)) {
      startingNodeId = otherStartingNodeId;
      return;
    }
    if (!startingNodeId.equals(otherStartingNodeId)) {
      throw new InvalidRequestException(
          String.format("Received different set of starting nodes: %s and %s", startingNodeId, otherStartingNodeId));
    }
  }

  public void addYamlUpdates(YamlUpdates otherYamlUpdates) {
    if (otherYamlUpdates == null) {
      return;
    }
    if (yamlUpdates == null) {
      yamlUpdates = otherYamlUpdates;
      return;
    }
    yamlUpdates = yamlUpdates.toBuilder().putAllFqnToYaml(otherYamlUpdates.getFqnToYamlMap()).build();
  }
}
