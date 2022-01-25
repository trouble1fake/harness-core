/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package ci.pipeline.execution;

import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Singleton
public class CIPipelineEndEventHandler implements OrchestrationEventHandler {
  @Inject CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject TelemetryReporter telemetryReporter;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject private ConnectorUtils connectorUtils;

  private static final String CI_EXECUTED = "ci_built";
  private static final String NODE_EXECUTED = "step_executed";

  @Override
  public void handleEvent(OrchestrationEvent event) {
    PipelineModuleInfo moduleInfo = event.getModuleInfo();
    if (moduleInfo instanceof CIPipelineModuleInfo) {
      CIPipelineModuleInfo ciModuleInfo = (CIPipelineModuleInfo) moduleInfo;
      updateExecutionCount(ciModuleInfo, event);
      sendCITelemetryEvents(ciModuleInfo, event);
    }
  }

  private void updateExecutionCount(CIPipelineModuleInfo moduleInfo, OrchestrationEvent event) {
    if (moduleInfo.getIsPrivateRepo()) {
      ciAccountExecutionMetadataRepository.updateAccountExecutionMetadata(
          AmbianceUtils.getAccountId(event.getAmbiance()), event.getEndTs());
    }
  }

  private void sendCITelemetryEvents(CIPipelineModuleInfo moduleInfo, OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String identity = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();

    sendCIExecutedEvent(ambiance, event, moduleInfo, identity, accountId);
    sendNodeExecutedEvent(identity, accountId, planExecutionId);
  }

  private void sendCIExecutedEvent(
      Ambiance ambiance, OrchestrationEvent event, CIPipelineModuleInfo moduleInfo, String identity, String accountId) {
    HashMap<String, Object> ciBuiltMap = new HashMap<>();

    StepElementParameters stepElementParameters = (StepElementParameters) event.getResolvedStepParameters();
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();
    BaseNGAccess baseNGAccess = retrieveBaseNGAccess(ambiance);
    if (initializeStepInfo != null && initializeStepInfo.getCiCodebase() != null) {
      ciBuiltMap.put("used_codebase", true);
      if (initializeStepInfo.getCiCodebase().getConnectorRef() != null) {
        ConnectorDetails connectorDetails =
            connectorUtils.getConnectorDetails(baseNGAccess, initializeStepInfo.getCiCodebase().getConnectorRef());
        ciBuiltMap.put("url", connectorUtils.retrieveURL(connectorDetails));
      }
    } else {
      ciBuiltMap.put("used_codebase", false);
    }

    ciBuiltMap.put("branch", moduleInfo.getBranch());
    ciBuiltMap.put("build_type", moduleInfo.getBuildType());
    ciBuiltMap.put("private_repo", moduleInfo.getIsPrivateRepo());
    ciBuiltMap.put("repo_name", moduleInfo.getRepoName());
    telemetryReporter.sendTrackEvent(CI_EXECUTED, identity, accountId, ciBuiltMap,
        Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
        io.harness.telemetry.TelemetryOption.builder().sendForCommunity(false).build());
  }

  private void sendNodeExecutedEvent(String identity, String accountId, String planExecutionId) {
    List<NodeExecution> nodeExecutionList = nodeExecutionService.fetchNodeExecutions(planExecutionId);
    for (NodeExecution nodeExecution : nodeExecutionList) {
      HashMap<String, Object> nodeExecutedMap = new HashMap<>();
      nodeExecutedMap.put("step_execution_id", nodeExecution.getOriginalNodeExecutionId());
      nodeExecutedMap.put("step_execution_duration", (nodeExecution.getEndTs() - nodeExecution.getStartTs()) / 1000);
      nodeExecutedMap.put("step_execution_type", nodeExecution.getNodeType());
      telemetryReporter.sendTrackEvent(NODE_EXECUTED, identity, accountId, nodeExecutedMap,
          Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL,
          io.harness.telemetry.TelemetryOption.builder().sendForCommunity(false).build());
    }
  }

  private BaseNGAccess retrieveBaseNGAccess(Ambiance ambiance) {
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);

    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
