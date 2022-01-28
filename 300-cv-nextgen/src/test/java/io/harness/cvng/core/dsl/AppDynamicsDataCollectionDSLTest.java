/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.AppDynamicsDataCollectionInfo;
import io.harness.cvng.beans.AppDynamicsDataCollectionInfo.AppMetricInfoDTO;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.cvng.appd.AppDynamicsConnectorValidationInfo;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AppDynamicsDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
  @Inject private MetricPackService metricPackService;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private ExecutorService executorService;

  @Before
  public void setup() {
    accountId = generateUuid();
    orgIdentifier = generateUuid();
    projectIdentifier = generateUuid();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecute_appDyanmicsPerformancePackForServiceGuard() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("performance-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);

    AppDynamicsDataCollectionInfo appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder()
            .applicationName("cv-app")
            .tierName("docker-tier")
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Performance"))
                            .findFirst()
                            .get()
                            .toDTO())
            .build();
    Map<String, Object> params =
        appDynamicsDataCollectionInfo.getDslEnvVariables(AppDynamicsConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://harness-test.saas.appdynamics.com/controller/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(readJson("performance-service-guard-expectation.json"),
            new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecute_appDyanmicsCustomPackForServiceGuard() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("custom-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);

    AppDynamicsDataCollectionInfo appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder()
            .applicationName("cv-app")
            .tierName("docker-tier")
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Custom"))
                            .findFirst()
                            .get()
                            .toDTO())
            .customMetrics(Arrays.asList(AppMetricInfoDTO.builder()
                                             .metricIdentifier("calls_number")
                                             .baseFolder("Overall Application Performance")
                                             .metricName("Calls Number")
                                             .metricPath("Average Response Time (ms)")
                                             .build(),
                AppMetricInfoDTO.builder()
                    .metricIdentifier("stall_number")
                    .baseFolder("Overall Application Performance")
                    .metricName("Stall Count")
                    .metricPath("Stall Count")
                    .build()))
            .build();
    Map<String, Object> params =
        appDynamicsDataCollectionInfo.getDslEnvVariables(AppDynamicsConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://harness-test.saas.appdynamics.com/controller/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("custom-service-guard-expectation.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecute_appDyanmicsCustomPackForDeployment() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("custom-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);

    AppDynamicsDataCollectionInfo appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder()
            .applicationName("cv-app")
            .tierName("docker-tier")
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Custom"))
                            .findFirst()
                            .get()
                            .toDTO())
            .customMetrics(Arrays.asList(AppMetricInfoDTO.builder()
                                             .metricIdentifier("calls_number")
                                             .baseFolder("Overall Application Performance")
                                             .metricName("Calls Number")
                                             .serviceInstanceMetricPath("Individual Nodes|*|Average Response Time (ms)")
                                             .metricPath("Average Response Time (ms)")
                                             .build(),
                AppMetricInfoDTO.builder()
                    .metricIdentifier("stall_number")
                    .baseFolder("Overall Application Performance")
                    .metricName("Stall Count")
                    .serviceInstanceMetricPath("Individual Nodes|*|Stall Count")
                    .metricPath("Stall Count")
                    .build()))
            .build();
    appDynamicsDataCollectionInfo.setCollectHostData(true);
    Map<String, Object> params =
        appDynamicsDataCollectionInfo.getDslEnvVariables(AppDynamicsConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://harness-test.saas.appdynamics.com/controller/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("custom-deployment-expectation.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecute_appDyanmicsPerformancePackWithHosts() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("performance-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);

    AppDynamicsDataCollectionInfo appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder()
            .applicationName("cv-app")
            .tierName("docker-tier")
            .metricPack(metricPacks.stream()
                            .filter(metricPack -> metricPack.getIdentifier().equals("Performance"))
                            .findFirst()
                            .get()
                            .toDTO())
            .build();
    appDynamicsDataCollectionInfo.setCollectHostData(true);
    Map<String, Object> params =
        appDynamicsDataCollectionInfo.getDslEnvVariables(AppDynamicsConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://harness-test.saas.appdynamics.com/controller/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters);
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(readJson("performance-collection-hosts-expectation.json"),
            new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testExecute_appDyanmicsQualityPackForServiceGuard() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("quality-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);

    AppDynamicsDataCollectionInfo appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder()
            .applicationName("cv-app")
            .tierName("docker-tier")
            .metricPack(
                metricPacks.stream()
                    .filter(
                        metricPack -> metricPack.getIdentifier().equals(CVMonitoringCategory.ERRORS.getDisplayName()))
                    .findFirst()
                    .get()
                    .toDTO())
            .build();
    Map<String, Object> params =
        appDynamicsDataCollectionInfo.getDslEnvVariables(AppDynamicsConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(600))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://harness-test.saas.appdynamics.com/controller/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("quality-service-guard-expectation.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testExecute_appDyanmicsQualityPackWithHosts() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("quality-pack.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks =
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS);

    AppDynamicsDataCollectionInfo appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfo.builder()
            .applicationName("cv-app")
            .tierName("docker-tier")
            .metricPack(
                metricPacks.stream()
                    .filter(
                        metricPack -> metricPack.getIdentifier().equals(CVMonitoringCategory.ERRORS.getDisplayName()))
                    .findFirst()
                    .get()
                    .toDTO())
            .build();
    appDynamicsDataCollectionInfo.setCollectHostData(true);
    Map<String, Object> params =
        appDynamicsDataCollectionInfo.getDslEnvVariables(AppDynamicsConnectorDTO.builder().build());

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **"); // Replace this with the actual value when capturing the request.
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(600))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://harness-test.saas.appdynamics.com/controller/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(readJson("quality-collection-hosts-expectation.json"),
            new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecute_appDynamicsConnectionValidationValidSettings() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    AppDynamicsConnectorValidationInfo appDynamicsConnectorValidationInfo =
        AppDynamicsConnectorValidationInfo.builder().build();
    appDynamicsConnectorValidationInfo.setConnectorConfigDTO(
        AppDynamicsConnectorDTO.builder()
            .controllerUrl("https://harness-test.saas.appdynamics.com/controller")
            .accountname("harness-test")
            .username("uitest@harness.io")
            .passwordRef(SecretRefData.builder().decryptedValue("*Sj3&Sjsl32ssCv".toCharArray()).build())
            .build());
    String code = appDynamicsConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.now();
    System.out.println(instant);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(appDynamicsConnectorValidationInfo.getStartTime(instant))
            .endTime(appDynamicsConnectorValidationInfo.getEndTime(instant))
            .commonHeaders(appDynamicsConnectorValidationInfo.collectionHeaders())
            .baseUrl(appDynamicsConnectorValidationInfo.getBaseUrl())
            .otherEnvVariables(appDynamicsConnectorValidationInfo.getDslEnvVariables())
            .build();
    String isValid = (String) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(isValid).isEqualTo("true");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecute_appDynamicsConnectionValidationInValidSettings() {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    AppDynamicsConnectorValidationInfo appDynamicsConnectorValidationInfo =
        AppDynamicsConnectorValidationInfo.builder().build();
    appDynamicsConnectorValidationInfo.setConnectorConfigDTO(
        AppDynamicsConnectorDTO.builder()
            .controllerUrl("https://harness-test.saas.appdynamics.com/controllerr")
            .accountname("harness-test")
            .username("uitest@harness.io")
            .passwordRef(SecretRefData.builder().decryptedValue("*Sj3&Sjsl32ssCv".toCharArray()).build())
            .build());
    String code = appDynamicsConnectorValidationInfo.getConnectionValidationDSL();
    Instant instant = Instant.now();
    System.out.println(instant);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(appDynamicsConnectorValidationInfo.getStartTime(instant))
            .endTime(appDynamicsConnectorValidationInfo.getEndTime(instant))
            .commonHeaders(appDynamicsConnectorValidationInfo.collectionHeaders())
            .baseUrl(appDynamicsConnectorValidationInfo.getBaseUrl())
            .otherEnvVariables(appDynamicsConnectorValidationInfo.getDslEnvVariables())
            .build();
    assertThatThrownBy(() -> dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {}))
        .hasMessageContaining("Response code: 404");
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(AppDynamicsCVConfig.class, "/appdynamics/dsl/" + name)), StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("hoverfly/appdynamics/" + name)), StandardCharsets.UTF_8);
  }
}
