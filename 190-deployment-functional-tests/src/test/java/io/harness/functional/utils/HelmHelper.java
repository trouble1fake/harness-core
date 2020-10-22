package io.harness.functional.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static java.lang.String.format;
import static software.wings.beans.BasicOrchestrationWorkflow.BasicOrchestrationWorkflowBuilder.aBasicOrchestrationWorkflow;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.beans.WorkflowType;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.ServiceGenerator;
import io.harness.generator.WorkflowGenerator;
import io.harness.k8s.model.HelmVersion;
import software.wings.api.DeploymentType;
import software.wings.beans.BasicOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.WorkflowService;
import software.wings.utils.ArtifactType;

import java.util.List;
import java.util.Map;

@Singleton
public class HelmHelper {
  private static final int RELEASE_NAME_LENGTH_WITHOUT_SHORT_ID = 46;
  private static final String RELEASE_NAME_FORMAT = "%s-${infra.helm.shortId}";
  private static final String HELM_RELEASE_NAME_PREFIX = "helmReleaseNamePrefix";
  private static final String CREATE_NAMESPACE_STEP_NAME = "Create Namespace";
  private static final String CREATE_NAMESPACE_SCRIPT =
      "export KUBECONFIG=${HARNESS_KUBE_CONFIG_PATH}\nkubectl get namespace ${infra.kubernetes.namespace} || kubectl create namespace ${infra.kubernetes.namespace}";

  @Inject private WorkflowGenerator workflowGenerator;
  @Inject private WorkflowService workflowService;
  @Inject private ServiceGenerator serviceGenerator;
  @Inject private ApplicationManifestService applicationManifestService;

  public Workflow createHelmWorkflow(
      Owners owners, Seed seed, String name, Service service, InfrastructureDefinition infraDefinition) {
    Workflow workflow = workflowGenerator.ensureWorkflow(seed, owners,
        aWorkflow()
            .name(name)
            .appId(service.getAppId())
            .envId(infraDefinition.getEnvId())
            .infraDefinitionId(infraDefinition.getUuid())
            .serviceId(service.getUuid())
            .workflowType(WorkflowType.ORCHESTRATION)
            .orchestrationWorkflow(aBasicOrchestrationWorkflow().build())
            .build());

    return setupWorkflow(workflow, name, service.getHelmVersion());
  }

  public Workflow setupWorkflow(Workflow workflow, String releaseName, HelmVersion helmVersion) {
    BasicOrchestrationWorkflow orchestrationWorkflow = (BasicOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    GraphNode helmDeployStep = workflowPhase.getPhaseSteps().get(0).getSteps().get(0);
    Map<String, Object> properties = helmDeployStep.getProperties();
    properties.put(HELM_RELEASE_NAME_PREFIX, getReleaseName(releaseName));
    helmDeployStep.setProperties(properties);

    if (HelmVersion.V3 == helmVersion) {
      // Starting with v3, helm doesn't manage anymore namespace. This script will create namespace if it's missing
      // (create-namespace alternative is available starting with 3.2)
      GraphNode createNamespaceStep =
          GraphNode.builder()
              .name(CREATE_NAMESPACE_STEP_NAME)
              .type("SHELL_SCRIPT")
              .properties(ImmutableMap.of("scriptType", "BASH", "scriptString", CREATE_NAMESPACE_SCRIPT,
                  "executeOnDelegate", true, "timeoutMillis", 12000))
              .build();
      if (workflowPhase.getPhaseSteps().get(0).getSteps().size() > 1) {
        workflowPhase.getPhaseSteps().get(0).getSteps().set(0, createNamespaceStep);
      } else {
        workflowPhase.getPhaseSteps().get(0).getSteps().add(0, createNamespaceStep);
      }
    }

    return workflowService.updateWorkflow(workflow, false);
  }

  public Service createHelmService(
      Owners owners, Seed seed, String serviceName, HelmVersion version, HelmChartConfig helmChartConfig) {
    Service service = Service.builder()
                          .name(serviceName)
                          .deploymentType(DeploymentType.HELM)
                          .appId(owners.obtainApplication().getAppId())
                          .artifactType(ArtifactType.DOCKER)
                          .helmVersion(version)
                          .build();

    service = serviceGenerator.ensureService(seed, owners, service);
    applyHelmChartConfigToService(service, helmChartConfig);
    return service;
  }

  private void applyHelmChartConfigToService(Service service, HelmChartConfig helmChartConfig) {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId(service.getUuid())
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .helmChartConfig(helmChartConfig)
                                                  .kind(AppManifestKind.K8S_MANIFEST)
                                                  .build();
    applicationManifest.setAppId(service.getAppId());

    List<ApplicationManifest> applicationManifests =
        applicationManifestService.listAppManifests(service.getAppId(), service.getUuid());

    if (isEmpty(applicationManifests)) {
      applicationManifestService.create(applicationManifest);
    } else {
      boolean found = false;
      for (ApplicationManifest savedApplicationManifest : applicationManifests) {
        if (savedApplicationManifest.getKind() == AppManifestKind.K8S_MANIFEST
            && savedApplicationManifest.getStoreType() == StoreType.HelmChartRepo) {
          applicationManifest.setUuid(savedApplicationManifest.getUuid());
          applicationManifestService.update(applicationManifest);
          found = true;
          break;
        }
      }
      if (!found) {
        applicationManifestService.create(applicationManifest);
      }
    }
  }

  private String getReleaseName(String baseName) {
    if (baseName.length() > RELEASE_NAME_LENGTH_WITHOUT_SHORT_ID) {
      baseName = baseName.substring(0, RELEASE_NAME_LENGTH_WITHOUT_SHORT_ID);
    }

    baseName = baseName.toLowerCase().replace(" ", "-").replaceFirst("-$", "");

    return format(RELEASE_NAME_FORMAT, baseName);
  }
}
