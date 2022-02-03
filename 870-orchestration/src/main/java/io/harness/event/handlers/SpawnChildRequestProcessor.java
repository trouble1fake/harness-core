/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.resume.EngineResumeCallback;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.IdentityNodeExecutionMetadata;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionMetadata;
import io.harness.execution.NodeSpawnType;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SpawnChildRequest;
import io.harness.pms.execution.utils.SdkResponseEventUtils;
import io.harness.pms.expression.PmsEngineExpressionService;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class SpawnChildRequestProcessor implements SdkResponseProcessor {
  @Inject private PlanService planService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private OrchestrationEngine engine;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    SpawnChildRequest request = event.getSpawnChildRequest();
    NodeExecution childNodeExecution = triggerNodeExecution(SdkResponseEventUtils.getNodeExecutionId(event), request);

    log.info("For Child Executable starting Child NodeExecution with id: {}", childNodeExecution.getUuid());

    // Attach a Callback to the parent for the child
    EngineResumeCallback callback = EngineResumeCallback.builder().ambiance(event.getAmbiance()).build();
    waitNotifyEngine.waitForAllOn(publisherName, callback, childNodeExecution.getUuid());

    // Update the parent with executable response
    nodeExecutionService.updateV2(SdkResponseEventUtils.getNodeExecutionId(event),
        ops -> ops.addToSet(NodeExecutionKeys.executableResponses, buildExecutableResponse(request)));
  }

  private NodeExecution triggerNodeExecution(String nodeExecutionId, SpawnChildRequest spawnChildRequest) {
    NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);

    String childNodeId = extractChildNodeId(spawnChildRequest);
    Node node = planService.fetchNode(nodeExecution.getAmbiance().getPlanId(), childNodeId);

    String childInstanceId = generateUuid();

    PmsNodeExecutionMetadata metadata;
    if (node.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
      metadata = IdentityNodeExecutionMetadata.builder()
                     .nodeSpawnType(NodeSpawnType.CHILD)
                     .childNodeId(childInstanceId)
                     .build();
    } else {
      metadata =
          NodeExecutionMetadata.builder().nodeSpawnType(NodeSpawnType.CHILD).childNodeId(childInstanceId).build();
    }
    return engine.triggerNode(nodeExecution.getAmbiance(), node, metadata);
  }

  private String extractChildNodeId(SpawnChildRequest spawnChildRequest) {
    switch (spawnChildRequest.getSpawnableExecutableResponseCase()) {
      case CHILD:
        return spawnChildRequest.getChild().getChildNodeId();
      case CHILDCHAIN:
        return spawnChildRequest.getChildChain().getNextChildId();
      default:
        throw new InvalidRequestException("CHILD or CHILD_CHAIN response should be set");
    }
  }

  private ExecutableResponse buildExecutableResponse(SpawnChildRequest spawnChildRequest) {
    switch (spawnChildRequest.getSpawnableExecutableResponseCase()) {
      case CHILD:
        return ExecutableResponse.newBuilder().setChild(spawnChildRequest.getChild()).build();
      case CHILDCHAIN:
        return ExecutableResponse.newBuilder().setChildChain(spawnChildRequest.getChildChain()).build();
      default:
        throw new InvalidRequestException("CHILD or CHILD_CHAIN response should be set");
    }
  }
}
