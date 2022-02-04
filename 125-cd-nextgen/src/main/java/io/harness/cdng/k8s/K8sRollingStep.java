/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.K8sRollingBaseStepInfo.K8sRollingBaseStepInfoKeys;
import io.harness.cdng.k8s.K8sRollingOutcome.K8sRollingOutcomeBuilder;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.K8sRollingReleaseOutput;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class K8sRollingStep extends TaskChainExecutableWithRollbackAndRbac implements K8sStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_ROLLING.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String K8S_ROLLING_DEPLOY_COMMAND_NAME = "Rolling Deploy";

  @Inject private K8sStepHelper k8sStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    return k8sStepHelper.startChainLink(this, ambiance, stepElementParameters);
  }

  @Override
  public TaskChainResponse executeK8sTask(ManifestOutcome k8sManifestOutcome, Ambiance ambiance,
      StepElementParameters stepElementParameters, List<String> manifestOverrideContents,
      K8sExecutionPassThroughData executionPassThroughData, boolean shouldOpenFetchFilesLogStream,
      UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructure = executionPassThroughData.getInfrastructure();
    String releaseName = k8sStepHelper.getReleaseName(ambiance, infrastructure);
    K8sRollingStepParameters k8sRollingStepParameters = (K8sRollingStepParameters) stepElementParameters.getSpec();
    boolean skipDryRun = CDStepHelper.getParameterFieldBooleanValue(
        k8sRollingStepParameters.getSkipDryRun(), K8sRollingBaseStepInfoKeys.skipDryRun, stepElementParameters);
    List<String> manifestFilesContents =
        k8sStepHelper.renderValues(k8sManifestOutcome, ambiance, manifestOverrideContents);
    boolean isOpenshiftTemplate = ManifestType.OpenshiftTemplate.equals(k8sManifestOutcome.getType());
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    boolean isCanaryWorkflow = false;
    String canaryStepFqn = k8sRollingStepParameters.getCanaryStepFqn();
    if (canaryStepFqn != null) {
      OptionalSweepingOutput optionalCanaryOutcome = executionSweepingOutputService.resolveOptional(ambiance,
          RefObjectUtils.getSweepingOutputRefObject(
              canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));
      isCanaryWorkflow = optionalCanaryOutcome.isFound();
    }

    K8sRollingDeployRequest k8sRollingDeployRequest =
        K8sRollingDeployRequest.builder()
            .skipDryRun(skipDryRun)
            .inCanaryWorkflow(isCanaryWorkflow)
            .releaseName(releaseName)
            .commandName(K8S_ROLLING_DEPLOY_COMMAND_NAME)
            .taskType(K8sTaskType.DEPLOYMENT_ROLLING)
            .localOverrideFeatureFlag(false)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .valuesYamlList(!isOpenshiftTemplate ? manifestFilesContents : Collections.emptyList())
            .openshiftParamList(isOpenshiftTemplate ? manifestFilesContents : Collections.emptyList())
            .kustomizePatchesList(k8sStepHelper.renderPatches(k8sManifestOutcome, ambiance, manifestOverrideContents))
            .k8sInfraDelegateConfig(k8sStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .manifestDelegateConfig(k8sStepHelper.getManifestDelegateConfig(k8sManifestOutcome, ambiance))
            .accountId(accountId)
            .skipResourceVersioning(k8sStepHelper.getSkipResourceVersioning(k8sManifestOutcome))
            .shouldOpenFetchFilesLogStream(shouldOpenFetchFilesLogStream)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .useLatestKustomizeVersion(k8sStepHelper.isUseLatestKustomizeVersion(accountId))
            .useNewKubectlVersion(k8sStepHelper.isUseNewKubectlVersion(accountId))
            .build();

    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);
    TaskChainResponse response =
        k8sStepHelper.queueK8sTask(stepElementParameters, k8sRollingDeployRequest, ambiance, executionPassThroughData);
    executionSweepingOutputService.consume(ambiance, K8sRollingReleaseOutput.OUTPUT_NAME,
        K8sRollingReleaseOutput.builder().name(releaseName).build(), StepOutcomeGroup.STEP.name());

    return response;
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    log.info("Calling executeNextLink");
    return k8sStepHelper.executeNextLink(this, ambiance, stepElementParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof GitFetchResponsePassThroughData) {
      return k8sStepHelper.handleGitTaskFailure((GitFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof HelmValuesFetchResponsePassThroughData) {
      return k8sStepHelper.handleHelmValuesFetchFailure((HelmValuesFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof StepExceptionPassThroughData) {
      return k8sStepHelper.handleStepExceptionFailure((StepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    K8sExecutionPassThroughData k8sExecutionPassThroughData = (K8sExecutionPassThroughData) passThroughData;
    K8sRollingOutcomeBuilder k8sRollingOutcomeBuilder = K8sRollingOutcome.builder().releaseName(
        k8sStepHelper.getReleaseName(ambiance, k8sExecutionPassThroughData.getInfrastructure()));

    K8sDeployResponse k8sTaskExecutionResponse;
    try {
      k8sTaskExecutionResponse = (K8sDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing K8s Task response: {}", e.getMessage(), e);
      return k8sStepHelper.handleTaskException(ambiance, k8sExecutionPassThroughData, e);
    }

    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(k8sTaskExecutionResponse.getCommandUnitsProgress().getUnitProgresses());

    if (k8sTaskExecutionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.K8S_ROLL_OUT,
          k8sRollingOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
      return K8sStepHelper.getFailureResponseBuilder(k8sTaskExecutionResponse, stepResponseBuilder).build();
    }

    K8sRollingDeployResponse k8sTaskResponse =
        (K8sRollingDeployResponse) k8sTaskExecutionResponse.getK8sNGTaskResponse();
    K8sRollingOutcome k8sRollingOutcome =
        k8sRollingOutcomeBuilder.releaseNumber(k8sTaskResponse.getReleaseNumber()).build();
    executionSweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.K8S_ROLL_OUT, k8sRollingOutcome, StepOutcomeGroup.STEP.name());

    StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(
        ambiance, K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(k8sTaskResponse.getK8sPodList()));

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepOutcome.builder().name(OutcomeExpressionConstants.OUTPUT).outcome(k8sRollingOutcome).build())
        .stepOutcome(stepOutcome)
        .build();
  }
}
