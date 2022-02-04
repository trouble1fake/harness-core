/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.deploymentevent;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.models.DeploymentEvent;
import io.harness.models.constants.InstanceSyncFlow;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instancesync.InstanceSyncService;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.util.logging.InstanceSyncLogContext;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class DeploymentEventListener implements OrchestrationEventHandler {
  private final OutcomeService outcomeService;
  private final InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;
  private final InfrastructureMappingService infrastructureMappingService;
  private final InstanceSyncService instanceSyncService;
  private final InstanceInfoService instanceInfoService;
  private final DeploymentSummaryService deploymentSummaryService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String accountIdentifier = getAccountIdentifier(ambiance);
    try (AutoLogContext ignore1 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 = InstanceSyncLogContext.builder()
                                      .instanceSyncFlow(InstanceSyncFlow.NEW_DEPLOYMENT.name())
                                      .build(OVERRIDE_ERROR)) {
      if (!StatusUtils.isFinalStatus(event.getStatus())) {
        return;
      }

      StepType stepType = AmbianceUtils.getCurrentStepType(ambiance);
      List<ServerInstanceInfo> serverInstanceInfoList = instanceInfoService.listServerInstances(ambiance, stepType);
      if (serverInstanceInfoList.isEmpty()) {
        return;
      }
      ServiceStepOutcome serviceStepOutcome = getServiceOutcomeFromAmbiance(ambiance);
      InfrastructureOutcome infrastructureOutcome = getInfrastructureOutcomeFromAmbiance(ambiance);

      InfrastructureMappingDTO infrastructureMappingDTO =
          createInfrastructureMappingIfNotExists(ambiance, serviceStepOutcome, infrastructureOutcome);
      DeploymentSummaryDTO deploymentSummaryDTO = createDeploymentSummary(
          ambiance, serviceStepOutcome, infrastructureOutcome, infrastructureMappingDTO, serverInstanceInfoList);

      instanceSyncService.processInstanceSyncForNewDeployment(
          new DeploymentEvent(deploymentSummaryDTO, null, infrastructureOutcome));
    } catch (Exception exception) {
      log.error("Exception occured while handling event for instance sync", exception);
    }
  }

  // --------------------- PRIVATE METHODS ------------------------

  private InfrastructureMappingDTO createInfrastructureMappingIfNotExists(
      Ambiance ambiance, ServiceStepOutcome serviceOutcome, InfrastructureOutcome infrastructureOutcome) {
    InfrastructureMappingDTO infrastructureMappingDTO =
        InfrastructureMappingDTO.builder()
            .accountIdentifier(getAccountIdentifier(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .serviceIdentifier(serviceOutcome.getIdentifier())
            .envIdentifier(infrastructureOutcome.getEnvironment().getIdentifier())
            .infrastructureKey(infrastructureOutcome.getInfrastructureKey())
            .infrastructureKind(infrastructureOutcome.getKind())
            .connectorRef(infrastructureOutcome.getConnectorRef())
            .build();

    Optional<InfrastructureMappingDTO> infrastructureMappingDTOOptional =
        infrastructureMappingService.createNewOrReturnExistingInfrastructureMapping(infrastructureMappingDTO);
    if (infrastructureMappingDTOOptional.isPresent()) {
      return infrastructureMappingDTOOptional.get();
    } else {
      throw new InvalidRequestException("Failed to create infrastructure mapping for infrastructure key : "
          + infrastructureOutcome.getInfrastructureKey());
    }
  }

  private DeploymentSummaryDTO createDeploymentSummary(Ambiance ambiance, ServiceStepOutcome serviceOutcome,
      InfrastructureOutcome infrastructureOutcome, InfrastructureMappingDTO infrastructureMappingDTO,
      List<ServerInstanceInfo> serverInstanceInfoList) {
    AbstractInstanceSyncHandler abstractInstanceSyncHandler =
        instanceSyncHandlerFactoryService.getInstanceSyncHandler(serviceOutcome.getType());
    DeploymentInfoDTO deploymentInfoDTO =
        abstractInstanceSyncHandler.getDeploymentInfo(infrastructureOutcome, serverInstanceInfoList);

    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder()
            .accountIdentifier(getAccountIdentifier(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .pipelineExecutionId(ambiance.getPlanExecutionId())
            .pipelineExecutionName(ambiance.getMetadata().getPipelineIdentifier())
            .deployedByName(ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier())
            .deployedById(ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getUuid())
            .infrastructureMappingId(infrastructureMappingDTO.getId())
            .instanceSyncKey(deploymentInfoDTO.prepareInstanceSyncHandlerKey())
            .deploymentInfoDTO(deploymentInfoDTO)
            .deployedAt(AmbianceUtils.getCurrentLevelStartTs(ambiance))
            .build();
    setArtifactDetails(ambiance, deploymentSummaryDTO);
    deploymentSummaryDTO = deploymentSummaryService.save(deploymentSummaryDTO);

    deploymentSummaryDTO.setServerInstanceInfoList(serverInstanceInfoList);
    deploymentSummaryDTO.setInfrastructureMapping(infrastructureMappingDTO);

    return deploymentSummaryDTO;
  }

  private void setArtifactDetails(Ambiance ambiance, DeploymentSummaryDTO deploymentSummaryDTO) {
    if (isRollbackDeploymentEvent(ambiance)) {
      /**
       * Fetch the 2nd deployment summary in DB for the given deployment info
       * The 1st one will be the deployment summary for which changes have to be reverted, its prev one will
       * be the last stable one
       */
      Optional<DeploymentSummaryDTO> deploymentSummaryDTOOptional =
          deploymentSummaryService.getNthDeploymentSummaryFromNow(2, deploymentSummaryDTO.getInstanceSyncKey());
      if (deploymentSummaryDTOOptional.isPresent()) {
        deploymentSummaryDTO.setArtifactDetails(deploymentSummaryDTOOptional.get().getArtifactDetails());
        deploymentSummaryDTO.setRollbackDeployment(true);
        return;
      } else {
        throw new InvalidRequestException(
            "Rollback deployment event received but no past successful deployment summary present to rollback to, for instanceSyncKey: "
            + deploymentSummaryDTO.getInstanceSyncKey());
      }
    }

    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (!optionalOutcome.isFound()) {
      deploymentSummaryDTO.setArtifactDetails(ArtifactDetails.builder().artifactId("").tag("").build());
      return;
    }
    ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) optionalOutcome.getOutcome();
    deploymentSummaryDTO.setArtifactDetails(ArtifactDetails.builder()
                                                .tag(artifactsOutcome.getPrimary().getTag())
                                                .artifactId(artifactsOutcome.getPrimary().getIdentifier())
                                                .build());
  }

  private ServiceStepOutcome getServiceOutcomeFromAmbiance(Ambiance ambiance) {
    return (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
  }

  private InfrastructureOutcome getInfrastructureOutcomeFromAmbiance(Ambiance ambiance) {
    return (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
  }

  private String getAccountIdentifier(Ambiance ambiance) {
    return AmbianceUtils.getAccountId(ambiance);
  }

  private boolean isRollbackDeploymentEvent(Ambiance ambiance) {
    for (Level level : ambiance.getLevelsList()) {
      if (level.getIdentifier().equals(ROLLBACK_STEPS)) {
        return true;
      }
    }
    return false;
  }
}
