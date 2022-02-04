/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.notification.PipelineEventType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.notification.NotificationHelper;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.PIPELINE)
public class StageStatusUpdateNotificationEventHandlerTest extends CategoryTest {
  NotificationHelper notificationHelper;
  StageStatusUpdateNotificationEventHandler stageStatusUpdateNotificationEventHandler;

  @Before
  public void setUp() {
    notificationHelper = mock(NotificationHelper.class);
    stageStatusUpdateNotificationEventHandler = spy(new StageStatusUpdateNotificationEventHandler());
    stageStatusUpdateNotificationEventHandler.notificationHelper = notificationHelper;
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdate() {
    PlanNode stagePlanNode = PlanNode.builder()
                                 .uuid(generateUuid())
                                 .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                 .identifier("dummyIdentifier")
                                 .build();
    PlanNode stepPlanNode = PlanNode.builder()
                                .uuid(generateUuid())
                                .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                .identifier("dummyIdentifier")
                                .build();
    String stageNodeExecutionId = generateUuid();
    Ambiance stageAmbiance =
        Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeExecutionId, stagePlanNode)).build();

    NodeExecution nodeExecution =
        NodeExecution.builder().ambiance(stageAmbiance).planNode(stagePlanNode).status(Status.SUCCEEDED).build();
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).build();
    when(notificationHelper.getEventTypeForStage(nodeExecution))
        .thenReturn(Optional.of(PipelineEventType.STAGE_SUCCESS));
    doNothing().when(notificationHelper).sendNotification(any(), any(), any(), any());

    ArgumentCaptor<PipelineEventType> pipelineEventTypeArgumentCaptor =
        ArgumentCaptor.forClass(PipelineEventType.class);

    stageStatusUpdateNotificationEventHandler.onNodeStatusUpdate(nodeUpdateInfo);

    verify(notificationHelper, times(1))
        .sendNotification(any(), pipelineEventTypeArgumentCaptor.capture(), any(), any());
    assertEquals(pipelineEventTypeArgumentCaptor.getValue(), PipelineEventType.STAGE_SUCCESS);

    Ambiance stepAmbiance = Ambiance.newBuilder()
                                .addLevels(PmsLevelUtils.buildLevelFromNode(stageNodeExecutionId, stagePlanNode))
                                .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode))
                                .build();
    nodeExecution = NodeExecution.builder().ambiance(stepAmbiance).planNode(stepPlanNode).status(Status.FAILED).build();
    nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).build();
    stageStatusUpdateNotificationEventHandler.onNodeStatusUpdate(nodeUpdateInfo);
    verify(notificationHelper, times(2))
        .sendNotification(any(), pipelineEventTypeArgumentCaptor.capture(), any(), any());
    assertEquals(pipelineEventTypeArgumentCaptor.getValue(), PipelineEventType.STEP_FAILED);
  }
}
