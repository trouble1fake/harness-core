package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.IdentityNodeExecutionMetadata;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecutionMetadata;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class SpawnChildrenRequestProcessorTest extends OrchestrationTestBase {
  @Mock private PlanService planService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private OrchestrationEngine engine;
  @InjectMocks @Inject private SpawnChildrenRequestProcessor spawnChildrenRequestProcessor;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventForPlanNode() {
    String childNodeId = generateUuid();
    SdkResponseEventProto sdkResponseEvent =
        SdkResponseEventProto.newBuilder()
            .setSpawnChildrenRequest(SpawnChildrenRequest.newBuilder().setChildren(
                ChildrenExecutableResponse.newBuilder()
                    .addChildren(ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(childNodeId).build())
                    .build()))
            .build();
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(generateUuid()).ambiance(ambiance).build();

    PlanNode planNode = PlanNode.builder().build();
    Mockito
        .when(nodeExecutionService.getWithFieldsIncluded(
            AmbianceUtils.obtainCurrentRuntimeId(ambiance), NodeProjectionUtils.withAmbiance))
        .thenReturn(nodeExecution);
    Mockito.when(planService.fetchNode(ambiance.getPlanId(), childNodeId)).thenReturn(PlanNode.builder().build());

    spawnChildrenRequestProcessor.handleEvent(sdkResponseEvent);

    Mockito.verify(engine).triggerNode(eq(ambiance), eq(planNode), any(NodeExecutionMetadata.class));
    Mockito.verify(nodeExecutionService)
        .getWithFieldsIncluded(AmbianceUtils.obtainCurrentRuntimeId(ambiance), NodeProjectionUtils.withAmbiance);
    Mockito.verify(planService).fetchNode(ambiance.getPlanId(), childNodeId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventForIdentityNode() {
    String childNodeId = generateUuid();
    SdkResponseEventProto sdkResponseEvent =
        SdkResponseEventProto.newBuilder()
            .setSpawnChildrenRequest(SpawnChildrenRequest.newBuilder().setChildren(
                ChildrenExecutableResponse.newBuilder()
                    .addChildren(ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(childNodeId).build())
                    .build()))
            .build();
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    NodeExecution nodeExecution = NodeExecution.builder().uuid(generateUuid()).ambiance(ambiance).build();

    IdentityPlanNode planNode = IdentityPlanNode.builder().build();
    Mockito
        .when(nodeExecutionService.getWithFieldsIncluded(
            AmbianceUtils.obtainCurrentRuntimeId(ambiance), NodeProjectionUtils.withAmbiance))
        .thenReturn(nodeExecution);
    Mockito.when(planService.fetchNode(ambiance.getPlanId(), childNodeId)).thenReturn(PlanNode.builder().build());

    spawnChildrenRequestProcessor.handleEvent(sdkResponseEvent);

    Mockito.verify(engine).triggerNode(eq(ambiance), eq(planNode), any(IdentityNodeExecutionMetadata.class));
    Mockito.verify(nodeExecutionService)
        .getWithFieldsIncluded(AmbianceUtils.obtainCurrentRuntimeId(ambiance), NodeProjectionUtils.withAmbiance);
    Mockito.verify(planService).fetchNode(ambiance.getPlanId(), childNodeId);
  }
}