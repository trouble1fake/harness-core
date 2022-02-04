/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.StackdriverDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.cvng.core.entities.StackdriverCVConfig.MetricInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.StackdriverDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
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

public class StackdriverMetricDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;
  @Inject private StackdriverDataCollectionInfoMapper dataCollectionInfoMapper;
  private ExecutorService executorService;

  public static String DEFAULT_JSON_METRIC_DEFINITION = "{\n"
      + "  \"dataSets\": [\n"
      + "    {\n"
      + "      \"timeSeriesFilter\": {\n"
      + "        \"filter\": \"metric.type=\\\"kubernetes.io/container/cpu/limit_utilization\\\" resource.type=\\\"k8s_container\\\"\",\n"
      + "        \"minAlignmentPeriod\": \"60s\",\n"
      + "        \"aggregations\": [\n"
      + "          {\n"
      + "            \"perSeriesAligner\": \"ALIGN_MEAN\",\n"
      + "            \"crossSeriesReducer\": \"REDUCE_MEAN\",\n"
      + "            \"alignmentPeriod\": \"60s\",\n"
      + "            \"groupByFields\": []\n"
      + "          },\n"
      + "          {\n"
      + "            \"crossSeriesReducer\": \"REDUCE_NONE\",\n"
      + "            \"alignmentPeriod\": \"60s\",\n"
      + "            \"groupByFields\": []\n"
      + "          }\n"
      + "        ]\n"
      + "      },\n"
      + "      \"targetAxis\": \"Y1\",\n"
      + "      \"plotType\": \"LINE\"\n"
      + "    }\n"
      + "  ],\n"
      + "  \"options\": {\n"
      + "    \"mode\": \"COLOR\"\n"
      + "  },\n"
      + "  \"constantLines\": [],\n"
      + "  \"timeshiftDuration\": \"0s\",\n"
      + "  \"y1Axis\": {\n"
      + "    \"label\": \"y1Axis\",\n"
      + "    \"scale\": \"LINEAR\"\n"
      + "  }\n"
      + "}";

  @Before
  public void setup() {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecute_stackdriverDSL() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.APP_DYNAMICS);

    CVConfig stackdriverCVConfig =
        builderFactory.stackdriverMetricCVConfigBuilder()
            .metricInfoList(Arrays.asList(MetricInfo.builder()
                                              .metricName("name")
                                              .identifier("identifier")
                                              .metricType(TimeSeriesMetricType.RESP_TIME)
                                              .jsonMetricDefinition(DEFAULT_JSON_METRIC_DEFINITION)
                                              .serviceInstanceField("resource.label.pod_name")
                                              .build()))
            .dashboardName("CPU limit utilization [MEAN]")
            .dashboardPath("projects/7065288960/dashboards/883f5f4e-02e3-44f6-a64e-7f6915d093cf")
            .metricPack(metricPackService.getMetricPack(builderFactory.getContext().getAccountId(),
                builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
                DataSourceType.STACKDRIVER, "Performance"))
            .build();

    StackdriverDataCollectionInfo stackdriverDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo((StackdriverCVConfig) stackdriverCVConfig, TaskType.DEPLOYMENT);

    Map<String, Object> params = stackdriverDataCollectionInfo.getDslEnvVariables(GcpConnectorDTO.builder().build());
    params.put("accessToken",
        "run command - gcloud beta auth application-default print-access-token"); // use GOOGLE_APPLICATION_CREDENTIALS
                                                                                  // =
                                                                                  // https://vault-internal.harness.io:8200/ui/vault/secrets/secret/show/srm/chi-play-gcp
    params.put("project", "chi-play");
    Map<String, String> headers = new HashMap<>();
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60 * 60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://monitoring.googleapis.com/v3/projects/")
                                              .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("expected-stackdriver-dsl-output.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecute_stackdriverDSLWithHostDetails() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.ofEpochMilli(1642966157038L);
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.APP_DYNAMICS);

    CVConfig stackdriverCVConfig =
        builderFactory.stackdriverMetricCVConfigBuilder()
            .metricInfoList(Arrays.asList(MetricInfo.builder()
                                              .metricName("kubernetes.io/container/cpu/limit_utilization")
                                              .identifier("identifier")
                                              .metricType(TimeSeriesMetricType.RESP_TIME)
                                              .jsonMetricDefinition(DEFAULT_JSON_METRIC_DEFINITION)
                                              .serviceInstanceField("resource.label.pod_name")
                                              .build()))
            .dashboardName("CPU limit utilization [MEAN]")
            .dashboardPath("projects/7065288960/dashboards/883f5f4e-02e3-44f6-a64e-7f6915d093cf")
            .metricPack(metricPackService.getMetricPack(builderFactory.getContext().getAccountId(),
                builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
                DataSourceType.STACKDRIVER, "Performance"))
            .build();

    StackdriverDataCollectionInfo stackdriverDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo((StackdriverCVConfig) stackdriverCVConfig, TaskType.DEPLOYMENT);
    stackdriverDataCollectionInfo.setCollectHostData(true);

    Map<String, Object> params = stackdriverDataCollectionInfo.getDslEnvVariables(GcpConnectorDTO.builder().build());
    params.put("accessToken",
        "ya29.c.b0AXv0zTP-t4B2e_u3C3wFpZKl2Evseif0tf03DfMO-WTLavJylIXBzBP4FZH18ShPrtio3LK_hTTILZqAbc7xYgyjxrEH2N4ko63ZurHTvl7ViKiMKII7mhZsN8wgdUJb-GJbkfwHGpJerGSz3KraI2uYHBDLRdwZZmsCMjDYLncOF-lJzfL2NWsxqwQfhLLML1PfLzD7i-hlPW1bsFSLTwZTMK_7AA");
    // https://vault-internal.harness.io:8200/ui/vault/secrets/secret/show/srm/chi-play-gcp
    params.put("project", "chi-play");
    Map<String, String> headers = new HashMap<>();
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minusSeconds(60))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("https://monitoring.googleapis.com/v3/projects/")
                                              .build();

    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, callDetails -> {});
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(readJson("expected-stackdriver-dsl-output-with-hosts.json"),
            new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(AppDynamicsCVConfig.class, "/stackdriver/dsl/" + name)), StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("hoverfly/stackdriver/" + name)), StandardCharsets.UTF_8);
  }
}
