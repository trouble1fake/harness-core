/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.pms.contracts.execution.events.OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.cache.SpringMongoStore;
import io.harness.category.element.UnitTests;
import io.harness.data.OutcomeInstance;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;
import io.harness.testlib.RealMongo;
import io.harness.utils.DummyVisualizationOutcome;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.assertj.core.util.Maps;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class GraphStatusUpdateHelperTest extends OrchestrationVisualizationTestBase {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private SpringMongoStore mongoStore;

  @Inject @InjectMocks private NodeExecutionService nodeExecutionService;
  @Inject @Spy private GraphGenerationService graphGenerationService;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private GraphStatusUpdateHelper eventHandlerV2;

  @Mock private OrchestrationEventEmitter eventEmitter;

  @Before
  public void setup() {
    doNothing().when(eventEmitter).emitEvent(any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldDoNothingIfRuntimeIdIsNull() {
    String planExecutionId = generateUuid();
    eventHandlerV2.handleEvent(
        planExecutionId, null, NODE_EXECUTION_STATUS_UPDATE, OrchestrationGraph.builder().build());

    verify(graphGenerationService, never()).getCachedOrchestrationGraph(planExecutionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  @Ignore("Using event sourcing now, will remove/update")
  public void shouldAddRootNodeIdToTheGraphAndAddVertex() {
    // creating PlanExecution
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).startTs(System.currentTimeMillis()).status(Status.RUNNING).build();
    planExecutionService.save(planExecution);

    // creating NodeExecution
    NodeExecution dummyStart =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build())
            .mode(ExecutionMode.SYNC)
            .planNode(PlanNode.builder()
                          .uuid(generateUuid())
                          .name("name")
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .identifier("identifier1")
                          .build())
            .status(Status.QUEUED)
            .build();
    nodeExecutionService.save(dummyStart);

    // creating cached graph
    OrchestrationGraph cachedGraph = OrchestrationGraph.builder()
                                         .cacheKey(planExecution.getUuid())
                                         .cacheParams(null)
                                         .cacheContextOrder(System.currentTimeMillis())
                                         .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                            .graphVertexMap(new HashMap<>())
                                                            .adjacencyMap(new HashMap<>())
                                                            .build())
                                         .planExecutionId(planExecution.getUuid())
                                         .rootNodeIds(new ArrayList<>())
                                         .startTs(planExecution.getStartTs())
                                         .endTs(planExecution.getEndTs())
                                         .status(planExecution.getStatus())
                                         .build();
    mongoStore.upsert(cachedGraph, Duration.ofDays(10));

    OrchestrationGraph updatedGraph = eventHandlerV2.handleEvent(
        planExecution.getUuid(), dummyStart.getUuid(), NODE_EXECUTION_STATUS_UPDATE, cachedGraph);

    Awaitility.await().atMost(2, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
      OrchestrationGraph graphInternal = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());
      return !graphInternal.getRootNodeIds().isEmpty();
    });

    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getEndTs()).isNull();
    assertThat(updatedGraph.getRootNodeIds()).containsExactlyInAnyOrder(dummyStart.getUuid());
    assertThat(updatedGraph.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getAdjacencyList().getAdjacencyMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldUpdateExistingVertexInGraphAndAddOutcomes() {
    // creating PlanExecution
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).startTs(System.currentTimeMillis()).status(Status.RUNNING).build();
    planExecutionService.save(planExecution);

    // creating NodeExecution
    StepType stepType = StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .name("name")
                            .stepType(stepType)
                            .identifier("identifier1")
                            .serviceName("PIPELINE")
                            .build();
    NodeExecution dummyStart =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(planExecution.getUuid())
                          .addLevels(Level.newBuilder().setStepType(stepType).setSetupId(planNode.getUuid()).build())
                          .build())
            .mode(ExecutionMode.SYNC)
            .status(SUCCEEDED)
            .planNode(planNode)
            .build();
    nodeExecutionService.save(dummyStart);

    // creating cached graph
    OrchestrationGraph cachedGraph =
        OrchestrationGraph.builder()
            .cacheKey(planExecution.getUuid())
            .cacheParams(null)
            .cacheContextOrder(System.currentTimeMillis())
            .adjacencyList(
                OrchestrationAdjacencyListInternal.builder()
                    .graphVertexMap(
                        Maps.newHashMap(dummyStart.getUuid(), convertNodeExecutionWithStatusSucceeded(dummyStart)))
                    .adjacencyMap(Maps.newHashMap(dummyStart.getUuid(),
                        EdgeListInternal.builder().edges(new ArrayList<>()).nextIds(new ArrayList<>()).build()))
                    .build())
            .planExecutionId(planExecution.getUuid())
            .rootNodeIds(Lists.newArrayList(dummyStart.getUuid()))
            .startTs(planExecution.getStartTs())
            .endTs(planExecution.getEndTs())
            .status(planExecution.getStatus())
            .build();
    mongoStore.upsert(cachedGraph, Duration.ofDays(10));

    // creating outcome
    DummyVisualizationOutcome dummyVisualizationOutcome = new DummyVisualizationOutcome("outcome");
    Map<String, Object> doc = RecastOrchestrationUtils.toMap(dummyVisualizationOutcome);
    OutcomeInstance outcome = OutcomeInstance.builder()
                                  .planExecutionId(planExecution.getUuid())
                                  .producedBy(PmsLevelUtils.buildLevelFromNode(dummyStart.getUuid(), planNode))
                                  .createdAt(System.currentTimeMillis())
                                  .outcomeValue(PmsOutcome.parse(doc))
                                  .build();
    mongoTemplate.insert(outcome);

    OrchestrationGraph updatedGraph = eventHandlerV2.handleEvent(
        planExecution.getUuid(), dummyStart.getUuid(), NODE_EXECUTION_STATUS_UPDATE, cachedGraph);

    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getEndTs()).isNull();
    assertThat(updatedGraph.getRootNodeIds()).containsExactlyInAnyOrder(dummyStart.getUuid());

    Map<String, GraphVertex> graphVertexMap = updatedGraph.getAdjacencyList().getGraphVertexMap();
    assertThat(graphVertexMap.size()).isEqualTo(1);
    assertThat(graphVertexMap.get(dummyStart.getUuid()).getStatus()).isEqualTo(SUCCEEDED);
    assertThat(graphVertexMap.get(dummyStart.getUuid()).getOutcomeDocuments().values())
        .containsExactlyInAnyOrder(PmsOutcome.parse(RecastOrchestrationUtils.toMap(dummyVisualizationOutcome)));
    assertThat(updatedGraph.getAdjacencyList().getAdjacencyMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }

  private GraphVertex convertNodeExecutionWithStatusSucceeded(NodeExecution nodeExecution) {
    Level level = AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance());
    return GraphVertex.builder()
        .uuid(nodeExecution.getUuid())
        .planNodeId(level.getSetupId())
        .name(nodeExecution.name())
        .startTs(nodeExecution.getStartTs())
        .endTs(nodeExecution.getEndTs())
        .initialWaitDuration(nodeExecution.getInitialWaitDuration())
        .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
        .stepType(level.getStepType().getType())
        .status(SUCCEEDED)
        .failureInfo(nodeExecution.getFailureInfo())
        .stepParameters(nodeExecution.getPmsStepParameters())
        .mode(nodeExecution.getMode())
        .interruptHistories(nodeExecution.getInterruptHistories())
        .retryIds(nodeExecution.getRetryIds())
        .build();
  }
}
