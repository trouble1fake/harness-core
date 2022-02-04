/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.start;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionUpdateFailedException;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.contracts.TimeoutObtainment;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeStartHelperTest extends OrchestrationTestBase {
  @Mock private InterruptService interruptService;
  @Mock private PlanService planService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsEventSender pmsEventSender;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @InjectMocks private NodeStartHelper nodeStartHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartDiscontinuingNodeExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .stepType(StepType.newBuilder().setType("DUMMY_TYPE").build())
                            .serviceName("CD")
                            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.DISCONTINUING)
                                      .mode(ExecutionMode.TASK)
                                      .planNode(planNode)
                                      .startTs(System.currentTimeMillis())
                                      .build();

    when(planService.fetchNode(planId, planNode.getUuid())).thenReturn(planNode);
    when(interruptService.checkInterruptsPreInvocation(planExecutionId, nodeExecutionId))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());

    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), eq(null), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(null);

    when(nodeExecutionService.get(eq(nodeExecutionId))).thenReturn(nodeExecution);

    assertThatThrownBy(()
                           -> nodeStartHelper.startNode(ambiance,
                               FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.TASK).build()))
        .isInstanceOf(NodeExecutionUpdateFailedException.class)
        .hasMessage("Cannot Start node Execution");

    verify(pmsEventSender, times(0)).sendEvent(any(), any(), any(), any(), eq(true));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStartQueuedNodeExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .stepType(StepType.newBuilder().setType("DUMMY_TYPE").build())
                            .timeoutObtainment(TimeoutObtainment.newBuilder()
                                                   .setDimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                                                   .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                                                       AbsoluteSdkTimeoutTrackerParameters.builder()
                                                           .timeout(ParameterField.createValueField("30m"))
                                                           .build())))
                                                   .build())
                            .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecutionBuilder builder = NodeExecution.builder()
                                       .uuid(nodeExecutionId)
                                       .ambiance(ambiance)
                                       .mode(ExecutionMode.TASK)
                                       .planNode(planNode)
                                       .startTs(System.currentTimeMillis());

    when(planService.fetchNode(planId, planNode.getUuid())).thenReturn(planNode);
    when(interruptService.checkInterruptsPreInvocation(planExecutionId, nodeExecutionId))
        .thenReturn(ExecutionCheck.builder().proceed(true).build());
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbianceAndNode))
        .thenReturn(builder.build());
    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.RUNNING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(
            builder.status(Status.RUNNING).timeoutInstanceIds(Collections.singletonList(generateUuid())).build());

    nodeStartHelper.startNode(
        ambiance, FacilitatorResponseProto.newBuilder().setExecutionMode(ExecutionMode.TASK).build());

    verify(pmsEventSender, times(1)).sendEvent(any(), any(), any(), any(), eq(true));
  }
}
