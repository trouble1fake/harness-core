package io.harness.functional.helm;

import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.rule.OwnerRule.ABOSII;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.CDFunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.functional.utils.HelmHelper;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator;
import io.harness.generator.InfrastructureDefinitionGenerator.InfrastructureDefinitions;
import io.harness.generator.OwnerManager;
import io.harness.generator.OwnerManager.Owners;
import io.harness.generator.Randomizer.Seed;
import io.harness.generator.SettingGenerator;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ApplicationManifestService;

@Slf4j
public class HelmFunctionalTest extends AbstractFunctionalTest {
  private static final String WORKFLOW_NAME = "Helm%s S3 Deployment";
  private static final String HELM_S3_SERVICE_NAME = "Helm%s S3 Service";
  private static final String CHART_NAME = "harness-todolist";
  private static final String HELM_V2_BASE_PATH = "helmv2/charts";
  private static final String HELM_V3_BASE_PATH = "helmv3/charts";

  @Inject private OwnerManager ownerManager;
  @Inject private SettingGenerator settingGenerator;
  @Inject private InfrastructureDefinitionGenerator infrastructureDefinitionGenerator;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private ApplicationGenerator applicationGenerator;
  @Inject private HelmHelper helmHelper;

  private Owners owners;
  private InfrastructureDefinition infrastructureDefinition;
  private Workflow workflow;

  private final Seed seed = new Seed(0);

  @Before
  public void setUp() throws Exception {
    owners = ownerManager.create();
    owners.obtainApplication(
        () -> applicationGenerator.ensurePredefined(seed, owners, ApplicationGenerator.Applications.GENERIC_TEST));
    infrastructureDefinition =
        infrastructureDefinitionGenerator.ensurePredefined(seed, owners, InfrastructureDefinitions.GCP_HELM);
    logger.info("Ensured Infra def");
    resetCache(owners.obtainAccount().getUuid());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  public void testHelmV2S3WorkflowExecution() {
    Service helmS3Service = createHelmS3Service(V2);
    logger.info("Created Service");
    addValuesYamlToService(helmS3Service);
    logger.info("Added values.yaml to service");
    workflow = helmHelper.createHelmWorkflow(
        owners, seed, format(WORKFLOW_NAME, V2.name()), helmS3Service, infrastructureDefinition);
    logger.info("Workflow created");

    resetCache(owners.obtainAccount().getUuid());
    ExecutionArgs executionArgs = getExecutionArgs();

    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, helmS3Service.getAppId(), infrastructureDefinition.getEnvId(), executionArgs);

    logStateExecutionInstanceErrors(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  public void testHelmV3S3WorkflowExecution() {
    Service helmS3Service = createHelmS3Service(V3);
    logger.info("Created Service");
    addValuesYamlToService(helmS3Service);
    logger.info("Added values.yaml to service");
    workflow = helmHelper.createHelmWorkflow(
        owners, seed, format(WORKFLOW_NAME, V3.name()), helmS3Service, infrastructureDefinition);
    logger.info("Workflow created");

    resetCache(owners.obtainAccount().getUuid());
    ExecutionArgs executionArgs = getExecutionArgs();

    WorkflowExecution workflowExecution =
        runWorkflow(bearerToken, helmS3Service.getAppId(), infrastructureDefinition.getEnvId(), executionArgs);

    logStateExecutionInstanceErrors(workflowExecution);
    assertThat(workflowExecution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private Service createHelmS3Service(HelmVersion helmVersion) {
    SettingAttribute helmS3Connector =
        settingGenerator.ensurePredefined(seed, owners, SettingGenerator.Settings.HELM_S3_CONNECTOR);

    HelmChartConfig helmChartConfig = HelmChartConfig.builder()
                                          .connectorId(helmS3Connector.getUuid())
                                          .chartName(CHART_NAME)
                                          .basePath(V3 == helmVersion ? HELM_V3_BASE_PATH : HELM_V2_BASE_PATH)
                                          .build();

    return helmHelper.createHelmService(
        owners, seed, format(HELM_S3_SERVICE_NAME, helmVersion.name()), helmVersion, helmChartConfig);
  }

  private void addValuesYamlToService(Service helmS3Service) {
    ApplicationManifest existingAppManifest = applicationManifestService.getAppManifest(
        helmS3Service.getAppId(), null, helmS3Service.getUuid(), AppManifestKind.VALUES);
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .kind(AppManifestKind.VALUES)
                                                  .serviceId(helmS3Service.getUuid())
                                                  .storeType(StoreType.Local)
                                                  .build();
    applicationManifest.setAppId(helmS3Service.getAppId());
    if (existingAppManifest == null) {
      applicationManifest = applicationManifestService.create(applicationManifest);
    } else {
      applicationManifest.setUuid(existingAppManifest.getUuid());
      applicationManifest = applicationManifestService.update(applicationManifest);
    }
    ManifestFile manifestFile = ManifestFile.builder()
                                    .fileContent("serviceName: functional-test")
                                    .applicationManifestId(applicationManifest.getUuid())
                                    .fileName("values.yaml")
                                    .build();
    manifestFile.setAppId(helmS3Service.getAppId());
    applicationManifestService.deleteAllManifestFilesByAppManifestId(
        applicationManifest.getAppId(), applicationManifest.getUuid());
    applicationManifestService.upsertApplicationManifestFile(manifestFile, applicationManifest, true);
  }

  @NotNull
  private ExecutionArgs getExecutionArgs() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setOrchestrationId(workflow.getUuid());
    executionArgs.setWorkflowType(workflow.getWorkflowType());
    return executionArgs;
  }
}
