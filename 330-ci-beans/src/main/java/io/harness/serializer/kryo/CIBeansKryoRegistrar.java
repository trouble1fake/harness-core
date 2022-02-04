/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.build.BuildUpdateType;
import io.harness.beans.build.CIPipelineDetails;
import io.harness.beans.build.PublishedArtifact;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.dependencies.ServiceDependency;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.VmBuildJobInfo;
import io.harness.beans.environment.pod.PodSetupInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.CommitDetails;
import io.harness.beans.execution.CustomExecutionSource;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookBaseAttributes;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.execution.WebhookGitUser;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.inputset.WebhookTriggerExecutionInputSet;
import io.harness.beans.outcomes.DependencyOutcome;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.BuildEnvSetupStepInfo;
import io.harness.beans.steps.stepinfo.CleanupStepInfo;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.steps.stepinfo.ECRStepInfo;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.RestoreCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheGCSStepInfo;
import io.harness.beans.steps.stepinfo.SaveCacheS3StepInfo;
import io.harness.beans.steps.stepinfo.TestStepInfo;
import io.harness.beans.steps.stepinfo.UploadToArtifactoryStepInfo;
import io.harness.beans.steps.stepinfo.UploadToGCSStepInfo;
import io.harness.beans.steps.stepinfo.UploadToS3StepInfo;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.StepTaskDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.CustomSecretVariable;
import io.harness.beans.yaml.extended.CustomTextVariable;
import io.harness.beans.yaml.extended.CustomVariable;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.ci.beans.entities.BuildNumberDetails;
import io.harness.ci.stdvars.BuildStandardVariables;
import io.harness.ci.stdvars.GitVariables;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

/**
 * Class will register all kryo classes
 */

@OwnedBy(HarnessTeam.CI)
public class CIBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(K8PodDetails.class, 100001);
    kryo.register(ContextElement.class, 100002);
    kryo.register(BuildEnvSetupStepInfo.class, 100003);
    kryo.register(CleanupStepInfo.class, 100005);
    kryo.register(InitializeStepInfo.class, 100008);
    kryo.register(RunStepInfo.class, 100011);
    kryo.register(TestStepInfo.class, 100013);
    kryo.register(StepTaskDetails.class, 100014);
    kryo.register(BuildStandardVariables.class, 100015);
    kryo.register(CIExecutionArgs.class, 100016);
    kryo.register(BuildNumberDetails.class, 100017);
    kryo.register(CustomVariable.class, 100023);
    kryo.register(K8BuildJobEnvInfo.class, 100024);
    kryo.register(K8BuildJobEnvInfo.PodsSetupInfo.class, 100025);
    kryo.register(PodSetupInfo.class, 100026);
    kryo.register(PodSetupInfo.PodSetupParams.class, 100027);
    kryo.register(ContainerDefinitionInfo.class, 100028);
    kryo.register(ContainerImageDetails.class, 100029);
    kryo.register(K8sDirectInfraYaml.class, 100040);
    kryo.register(K8sDirectInfraYamlSpec.class, 100041);
    kryo.register(WebhookExecutionSource.class, 100044);
    kryo.register(WebhookGitUser.class, 100045);
    kryo.register(WebhookEvent.class, 100046);
    kryo.register(PRWebhookEvent.class, 100047);
    kryo.register(CommitDetails.class, 100048);
    kryo.register(BranchWebhookEvent.class, 100049);
    kryo.register(CIPipelineDetails.class, 100050);
    kryo.register(PublishedArtifact.class, 100051);
    kryo.register(GitVariables.class, 100054);
    kryo.register(WebhookTriggerExecutionInputSet.class, 100055);
    kryo.register(ExecutionSource.Type.class, 100056);
    kryo.register(CIStepOutcome.class, 100057);
    kryo.register(PluginStepInfo.class, 100058);
    kryo.register(CustomSecretVariable.class, 100061);
    kryo.register(CustomTextVariable.class, 100062);
    kryo.register(CustomVariable.Type.class, 100063);
    kryo.register(DependencyElement.class, 100064);
    kryo.register(CIServiceInfo.class, 100065);
    kryo.register(UseFromStageInfraYaml.class, 100066);
    kryo.register(ManualExecutionSource.class, 100068);
    kryo.register(Infrastructure.Type.class, 100069);
    kryo.register(WebhookBaseAttributes.class, 100070);
    kryo.register(Repository.class, 100071);
    kryo.register(DependencyOutcome.class, 100072);
    kryo.register(ServiceDependency.class, 100073);
    kryo.register(ServiceDependency.Status.class, 100074);
    kryo.register(RunTestsStepInfo.class, 100075);
    kryo.register(GCRStepInfo.class, 100076);
    kryo.register(ECRStepInfo.class, 100077);
    kryo.register(DockerStepInfo.class, 100078);
    kryo.register(SaveCacheGCSStepInfo.class, 100079);
    kryo.register(RestoreCacheGCSStepInfo.class, 100080);
    kryo.register(SaveCacheS3StepInfo.class, 100081);
    kryo.register(RestoreCacheS3StepInfo.class, 100082);
    kryo.register(UploadToS3StepInfo.class, 100083);
    kryo.register(UploadToGCSStepInfo.class, 100084);
    kryo.register(K8BuildJobEnvInfo.ConnectorConversionInfo.class, 100085);
    kryo.register(UploadToArtifactoryStepInfo.class, 100086);
    kryo.register(PodCleanupDetails.class, 100087);
    kryo.register(CustomExecutionSource.class, 100088);
    kryo.register(LiteEnginePodDetailsOutcome.class, 100089);
    kryo.register(ContainerPortDetails.class, 100090);
    kryo.register(StepArtifacts.class, 100091);
    kryo.register(PublishedFileArtifact.class, 100092);
    kryo.register(PublishedImageArtifact.class, 100093);
    kryo.register(StageDetails.class, 100094);
    kryo.register(VmBuildJobInfo.class, 100095);
    kryo.register(VmInfraYaml.class, 100096);
    kryo.register(VmStageInfraDetails.class, 100098);
    kryo.register(StageInfraDetails.class, 100099);
    kryo.register(K8StageInfraDetails.class, 100100);

    kryo.register(VmDetailsOutcome.class, 110101); // quantum change in order because 100101 is already assigned.
    kryo.register(VmInfraSpec.Type.class, 110102);
    kryo.register(VmPoolYaml.class, 110103);
    kryo.register(VmPoolYamlSpec.class, 110104);

    kryo.register(BuildUpdateType.class, 390003);
    kryo.register(BuildStatusUpdateParameter.class, 390004);
  }
}
