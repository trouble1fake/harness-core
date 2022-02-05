/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow.creation.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.k8s.model.K8sExpressions.primaryServiceNameExpression;
import static io.harness.k8s.model.K8sExpressions.stageServiceNameExpression;

import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.PhaseStepType.K8S_PHASE_STEP;
import static software.wings.sm.StateType.RANCHER_K8S_BLUE_GREEN_DEPLOY;
import static software.wings.sm.StateType.RANCHER_KUBERNETES_SWAP_SERVICE_SELECTORS;
import static software.wings.sm.StateType.RANCHER_RESOLVE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.common.WorkflowConstants;
import software.wings.service.impl.workflow.WorkflowServiceHelper;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class RancherK8BlueGreenWorkflowPhaseHelper extends K8BlueGreenWorkflowPhaseHelper {
  @Override
  protected PhaseStep getRollbackRouteUpdatePhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.ROUTE_UPDATE)
        .withPhaseStepNameForRollback(WorkflowServiceHelper.ROUTE_UPDATE)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(RANCHER_KUBERNETES_SWAP_SERVICE_SELECTORS.name())
                     .name(WorkflowServiceHelper.RANCHER_KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE)
                     .properties(ImmutableMap.<String, Object>builder()
                                     .put("service1", primaryServiceNameExpression)
                                     .put("service2", stageServiceNameExpression)
                                     .build())
                     .rollback(true)
                     .build())
        .withStatusForRollback(ExecutionStatus.SUCCESS)
        .withRollback(true)
        .build();
  }

  @Override
  protected PhaseStep getDeployPhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.DEPLOY)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(RANCHER_RESOLVE.name())
                     .name(WorkflowConstants.RANCHER_RESOLVE_CLUSTERS)
                     .properties(new HashMap<>())
                     .build())
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(RANCHER_K8S_BLUE_GREEN_DEPLOY.name())
                     .name(WorkflowConstants.RANCHER_K8S_STAGE_DEPLOY)
                     .properties(new HashMap<>())
                     .build())
        .build();
  }

  @Override
  protected PhaseStep getRoutUpdatePhaseStep() {
    return aPhaseStep(K8S_PHASE_STEP, WorkflowServiceHelper.ROUTE_UPDATE)
        .addStep(GraphNode.builder()
                     .id(generateUuid())
                     .type(RANCHER_KUBERNETES_SWAP_SERVICE_SELECTORS.name())
                     .name(WorkflowServiceHelper.RANCHER_KUBERNETES_SWAP_SERVICES_PRIMARY_STAGE)
                     .properties(ImmutableMap.<String, Object>builder()
                                     .put("service1", primaryServiceNameExpression)
                                     .put("service2", stageServiceNameExpression)
                                     .build())
                     .build())
        .build();
  }
}