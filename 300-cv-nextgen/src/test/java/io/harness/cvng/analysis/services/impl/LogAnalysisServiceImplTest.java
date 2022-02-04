/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.analysis.entities.CanaryLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.ClusteredLog;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.Frequency;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.LogAnalysisClusterKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.AnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisResultKeys;
import io.harness.cvng.analysis.entities.ServiceGuardLogAnalysisTask;
import io.harness.cvng.analysis.entities.TestLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.beans.job.TestVerificationJobDTO;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.LogCVConfig;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.cvng.verificationjob.services.api.VerificationJobService;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class LogAnalysisServiceImplTest extends CvNextGenTestBase {
  private String cvConfigId;
  private String verificationTaskId;
  @Inject private HPersistence hPersistence;
  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private LogAnalysisService logAnalysisService;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private VerificationJobService verificationJobService;
  @Inject private HeatMapService heatMapService;
  @Mock private NextGenService nextGenService;
  private Instant instant;
  private String accountId;
  BuilderFactory builderFactory;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    CVConfig cvConfig = createCVConfig();
    cvConfigService.save(cvConfig);
    cvConfigId = cvConfig.getUuid();
    accountId = generateUuid();
    instant = Instant.parse("2020-07-27T10:44:11.000Z");
    verificationTaskId = verificationTaskService.getServiceGuardVerificationTaskId(cvConfig.getAccountId(), cvConfigId);
    FieldUtils.writeField(cvConfigService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(heatMapService, "cvConfigService", cvConfigService, true);
    FieldUtils.writeField(logAnalysisService, "heatMapService", heatMapService, true);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void scheduleLogAnalysisTask() {
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                              .endTime(Instant.now())
                              .build();
    String taskId = logAnalysisService.scheduleServiceGuardLogAnalysisTask(input);

    assertThat(taskId).isNotNull();

    LearningEngineTask task = hPersistence.get(LearningEngineTask.class, taskId);
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS.name());
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testScheduleLogAnalysisTask_baselineWindowIsSetProperly() {
    LogCVConfig cvConfig = (LogCVConfig) cvConfigService.get(cvConfigId);
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(cvConfig.getBaseline().getEndTime().minus(5, ChronoUnit.MINUTES))
                              .endTime(cvConfig.getBaseline().getEndTime())
                              .build();

    String taskId = logAnalysisService.scheduleServiceGuardLogAnalysisTask(input);
    assertThat(taskId).isNotNull();
    ServiceGuardLogAnalysisTask learningEngineTask =
        (ServiceGuardLogAnalysisTask) learningEngineTaskService.get(taskId);
    assertThat(learningEngineTask.isBaselineWindow()).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void getTestData() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<ClusteredLog> l2Logs = createClusteredLogRecords(start, end);
    hPersistence.save(l2Logs);

    List<LogClusterDTO> logClusterDTOList = logAnalysisService.getTestData(verificationTaskId, start, end);

    assertThat(logClusterDTOList).isNotNull();
    assertThat(logClusterDTOList.size()).isEqualTo(l2Logs.size());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void getFrequencyPattern_firstAnalysis() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> patterns = logAnalysisService.getPreviousAnalysis(verificationTaskId, start, end);

    assertThat(patterns).isNotNull();
    assertThat(patterns.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetFrequencyPattern_hasPreviousAnalysis() throws IOException {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<LogAnalysisCluster> analysisClusters = buildAnalysisClusters(12345l);

    analysisClusters.forEach(cluster -> {
      cluster.setVerificationTaskId(verificationTaskId);
      cluster.setAnalysisStartTime(start);
      cluster.setAnalysisEndTime(end);
    });
    hPersistence.save(analysisClusters);

    List<LogAnalysisCluster> patterns = logAnalysisService.getPreviousAnalysis(verificationTaskId, start, end);

    assertThat(patterns).isNotNull();
    assertThat(patterns.size()).isEqualTo(1);
    assertThat(patterns.get(0).getText()).isEqualTo("exception message");
    assertThat(patterns.get(0).getCompressedText()).isEqualTo(compressString(JsonUtils.asJson("exception message")));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveAnalysis_serviceGuard() {
    doReturn(builderFactory.environmentResponseDTOBuilder().type(EnvironmentType.Production).build())
        .when(nextGenService)
        .getEnvironment(any(), any(), any(), any());
    ServiceGuardLogAnalysisTask task = ServiceGuardLogAnalysisTask.builder().build();
    task.setTestDataUrl("testData");
    fillCommon(task, LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS);
    learningEngineTaskService.createLearningEngineTask(task);
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    LogAnalysisDTO analysisDTO = createAnalysisDTO(end);

    LogAnalysisCluster frequencyPattern = hPersistence.createQuery(LogAnalysisCluster.class)
                                              .filter(LogAnalysisClusterKeys.verificationTaskId, verificationTaskId)
                                              .get();
    assertThat(frequencyPattern).isNull();

    LogAnalysisResult result = hPersistence.createQuery(LogAnalysisResult.class)
                                   .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
                                   .get();
    assertThat(result).isNull();
    logAnalysisService.saveAnalysis(task.getUuid(), analysisDTO);
    LearningEngineTask updated = learningEngineTaskService.get(task.getUuid());
    assertThat(updated.getTaskStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    List<LogAnalysisCluster> analysisClusters =
        hPersistence.createQuery(LogAnalysisCluster.class)
            .filter(LogAnalysisClusterKeys.verificationTaskId, verificationTaskId)
            .asList();
    assertThat(analysisClusters).isNotNull();
    assertThat(analysisClusters.size()).isEqualTo(2);
    result = hPersistence.createQuery(LogAnalysisResult.class)
                 .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
                 .get();
    assertThat(result).isNotNull();
    assertThat(result.getLogAnalysisResults().size()).isEqualTo(2);
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    heatMaps.forEach(
        heatMap -> assertThat(heatMap.getHeatMapRisks().iterator().next().getAnomalousLogsCount()).isEqualTo(1));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSaveAnalysis_deployment() {
    CanaryLogAnalysisLearningEngineTask task =
        CanaryLogAnalysisLearningEngineTask.builder().controlHosts(Sets.newHashSet("host1", "host2")).build();
    task.setControlDataUrl("controlData");
    task.setTestDataUrl("testData");
    fillCommon(task, LearningEngineTaskType.CANARY_LOG_ANALYSIS);
    learningEngineTaskService.createLearningEngineTask(task);
    DeploymentLogAnalysisDTO deploymentLogAnalysisDTO = createDeploymentAnalysisDTO();
    logAnalysisService.saveAnalysis(task.getUuid(), deploymentLogAnalysisDTO);
    List<DeploymentLogAnalysis> deploymentLogAnalyses =
        deploymentLogAnalysisService.getAnalysisResults(verificationTaskId);
    assertThat(deploymentLogAnalyses).hasSize(1);
    assertThat(deploymentLogAnalyses.get(0).getClusters()).isEqualTo(deploymentLogAnalysisDTO.getClusters());
    List<HeatMap> heatMaps = hPersistence.createQuery(HeatMap.class).asList();
    assertThat(heatMaps.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetTaskStatus() throws Exception {
    LearningEngineTaskService learningEngineTaskService = mock(LearningEngineTaskService.class);
    FieldUtils.writeField(logAnalysisService, "learningEngineTaskService", learningEngineTaskService, true);
    List<String> taskIds = new ArrayList<>();
    taskIds.add("task1");
    taskIds.add("task2");
    logAnalysisService.getTaskStatus(taskIds);

    Mockito.verify(learningEngineTaskService).getTaskStatus(anySet());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisClusters() {
    List<LogAnalysisCluster> clusters = buildAnalysisClusters(1234l, 23456l);
    clusters.forEach(cluster -> cluster.setVerificationTaskId(verificationTaskId));
    hPersistence.save(clusters);

    List<LogAnalysisCluster> clustersReturned =
        logAnalysisService.getAnalysisClusters(verificationTaskId, new HashSet<>(Arrays.asList(1234l, 23456l)));

    assertThat(clustersReturned).containsExactlyInAnyOrderElementsOf(clusters);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisResults() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    LogAnalysisResult result = LogAnalysisResult.builder()
                                   .verificationTaskId(verificationTaskId)
                                   .logAnalysisResults(getAnalysisResults(1234l, 23456l))
                                   .analysisStartTime(start)
                                   .analysisEndTime(end)
                                   .build();
    hPersistence.save(result);
    LogAnalysisResult result2 = LogAnalysisResult.builder()
                                    .verificationTaskId(verificationTaskId)
                                    .logAnalysisResults(getAnalysisResults(1234l, 23456l))
                                    .analysisStartTime(end)
                                    .analysisEndTime(end.plus(5, ChronoUnit.MINUTES))
                                    .build();
    hPersistence.save(result2);

    List<LogAnalysisResult> analysisResults = logAnalysisService.getAnalysisResults(
        cvConfigId, Arrays.asList(LogAnalysisResult.LogAnalysisTag.UNKNOWN), start, end);

    assertThat(analysisResults.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisResults_validateOnlyUnknownIsReturned() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    LogAnalysisResult result = LogAnalysisResult.builder()
                                   .verificationTaskId(cvConfigId)
                                   .logAnalysisResults(getAnalysisResults(1235l))
                                   .analysisStartTime(start)
                                   .analysisEndTime(end)
                                   .build();
    hPersistence.save(result);
    LogAnalysisResult result2 = LogAnalysisResult.builder()
                                    .verificationTaskId(cvConfigId)
                                    .logAnalysisResults(getAnalysisResults(1234l, 23456l))
                                    .analysisStartTime(start)
                                    .analysisEndTime(end)
                                    .build();
    hPersistence.save(result2);

    List<LogAnalysisResult> analysisResults = logAnalysisService.getAnalysisResults(
        cvConfigId, Arrays.asList(LogAnalysisResult.LogAnalysisTag.UNKNOWN), start, end);

    assertThat(analysisResults.size()).isEqualTo(1);
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testScheduleDeploymentLogAnalysisTask_testVerificationWithNullBaseline() {
    VerificationJobInstance verificationJobInstance = newVerificationJobInstance();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(instant.plus(5, ChronoUnit.MINUTES))
                              .endTime(instant.plus(6, ChronoUnit.MINUTES))
                              .build();
    String taskId = logAnalysisService.scheduleDeploymentLogAnalysisTask(input);
    assertThat(taskId).isNotNull();

    LearningEngineTask task = hPersistence.get(LearningEngineTask.class, taskId);
    TestLogAnalysisLearningEngineTask testLogAnalysisLearningEngineTask = (TestLogAnalysisLearningEngineTask) task;
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.TEST_LOG_ANALYSIS.name());
    assertThat(testLogAnalysisLearningEngineTask.getControlDataUrl()).isNull();
    assertThat(testLogAnalysisLearningEngineTask.getTestDataUrl())
        .isEqualTo(CVConstants.SERVICE_BASE_URL + "/log-analysis/test-data?verificationTaskId=" + verificationTaskId
            + "&analysisStartTime=1595846951000&analysisEndTime=1595847011000");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testScheduleDeploymentLogAnalysisTask_testVerificationWithBaseline() {
    VerificationJobInstance verificationJobInstance = newVerificationJobInstance();
    VerificationJobInstance baseline = newVerificationJobInstance();
    String baselineJobInstanceId = verificationJobInstanceService.create(baseline);
    ((TestVerificationJob) verificationJobInstance.getResolvedJob())
        .setBaselineVerificationJobInstanceId(baselineJobInstanceId);
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String baselineVerificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, baselineJobInstanceId, APP_DYNAMICS);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    AnalysisInput input = AnalysisInput.builder()
                              .verificationTaskId(verificationTaskId)
                              .startTime(instant.plus(5, ChronoUnit.MINUTES))
                              .endTime(instant.plus(6, ChronoUnit.MINUTES))
                              .build();
    String taskId = logAnalysisService.scheduleDeploymentLogAnalysisTask(input);
    assertThat(taskId).isNotNull();

    LearningEngineTask task = hPersistence.get(LearningEngineTask.class, taskId);
    TestLogAnalysisLearningEngineTask testLogAnalysisLearningEngineTask = (TestLogAnalysisLearningEngineTask) task;
    assertThat(task).isNotNull();
    assertThat(task.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(Duration.between(task.getAnalysisStartTime(), input.getStartTime())).isZero();
    assertThat(Duration.between(task.getAnalysisEndTime(), input.getEndTime())).isZero();
    assertThat(task.getAnalysisType().name()).isEqualTo(LearningEngineTaskType.TEST_LOG_ANALYSIS.name());
    assertThat(testLogAnalysisLearningEngineTask.getControlDataUrl())
        .isEqualTo(CVConstants.SERVICE_BASE_URL + "/log-analysis/test-data?verificationTaskId="
            + baselineVerificationTaskId + "&analysisStartTime=1595846760000&analysisEndTime=1595847660000");
    assertThat(testLogAnalysisLearningEngineTask.getTestDataUrl())
        .isEqualTo(CVConstants.SERVICE_BASE_URL + "/log-analysis/test-data?verificationTaskId=" + verificationTaskId
            + "&analysisStartTime=1595846951000&analysisEndTime=1595847011000");
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTopLogAnalysisResults_emptyResults() {
    assertThat(logAnalysisService.getTopLogAnalysisResults(
                   Collections.singletonList(generateUuid()), instant.minus(Duration.ofMinutes(15)), instant))
        .isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTopLogAnalysisResults_validResults() {
    doReturn(builderFactory.environmentResponseDTOBuilder().type(EnvironmentType.Production).build())
        .when(nextGenService)
        .getEnvironment(any(), any(), any(), any());
    ServiceGuardLogAnalysisTask task = ServiceGuardLogAnalysisTask.builder().build();
    task.setTestDataUrl("testData");
    fillCommon(task, LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS);
    Instant start = instant.minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    task.setAnalysisStartTime(start);
    task.setAnalysisEndTime(end);
    learningEngineTaskService.createLearningEngineTask(task);
    for (int i = 0; i < 5; i++) {
      logAnalysisService.saveAnalysis(task.getUuid(), createAnalysisDTO(end, .1 * i));
    }
    List<LogAnalysisResult> logAnalysisResults =
        logAnalysisService.getTopLogAnalysisResults(Collections.singletonList(verificationTaskId),
            end.minus(Duration.ofMinutes(5)), end.plus(Duration.ofMinutes(10)));
    assertThat(logAnalysisResults).hasSize(3);
    for (int i = 0; i < 3; i++) {
      assertThat(logAnalysisResults.get(i).getOverallRisk()).isEqualTo((i + 2) * .1, offset(.00001));
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetPreviousDeploymentAnalysis() {
    CanaryLogAnalysisLearningEngineTask task =
        CanaryLogAnalysisLearningEngineTask.builder().controlHosts(Sets.newHashSet("host1", "host2")).build();
    task.setControlDataUrl("controlData");
    task.setTestDataUrl("testData");
    fillCommon(task, LearningEngineTaskType.CANARY_LOG_ANALYSIS);
    learningEngineTaskService.createLearningEngineTask(task);
    DeploymentLogAnalysisDTO deploymentLogAnalysisDTO = createDeploymentAnalysisDTO();
    logAnalysisService.saveAnalysis(task.getUuid(), deploymentLogAnalysisDTO);
    assertThat(logAnalysisService.getPreviousDeploymentAnalysis(
                   verificationTaskId, instant.minus(Duration.ofMinutes(10)), instant))
        .isNull();
    assertThat(logAnalysisService.getPreviousDeploymentAnalysis(
                   verificationTaskId, instant.minus(Duration.ofMinutes(9)), instant))
        .isNotNull();
  }

  private List<ClusteredLog> createClusteredLogRecords(Instant startTime, Instant endTime) {
    List<ClusteredLog> logRecords = new ArrayList<>();

    Instant timestamp = startTime;
    while (timestamp.isBefore(endTime)) {
      ClusteredLog record = ClusteredLog.builder()
                                .verificationTaskId(verificationTaskId)
                                .timestamp(timestamp)
                                .log("sample log record")
                                .clusterLabel("1")
                                .clusterCount(4)
                                .clusterLevel(LogClusterLevel.L2)
                                .build();
      logRecords.add(record);
      timestamp = timestamp.plus(1, ChronoUnit.MINUTES);
    }

    return logRecords;
  }
  //
  private LogAnalysisDTO createAnalysisDTO(Instant endTime) {
    return createAnalysisDTO(endTime, 0.2);
  }

  private LogAnalysisDTO createAnalysisDTO(Instant endTime, double score) {
    List<LogAnalysisCluster> clusters = buildAnalysisClusters(1234l, 23456l);
    LogAnalysisResult result =
        LogAnalysisResult.builder().logAnalysisResults(getAnalysisResults(12345l, 23456l)).build();
    return LogAnalysisDTO.builder()
        .score(score)
        .verificationTaskId(verificationTaskId)
        .logClusters(clusters)
        .logAnalysisResults(result.getLogAnalysisResults())
        .analysisMinute(endTime.getEpochSecond() / 60)
        .build();
  }
  private DeploymentLogAnalysisDTO createDeploymentAnalysisDTO() {
    return DeploymentLogAnalysisDTO.builder()
        .clusters(Collections.singletonList(DeploymentLogAnalysisDTO.Cluster.builder().label(1).text("text").build()))
        .resultSummary(
            DeploymentLogAnalysisDTO.ResultSummary.builder().controlClusterSummaries(null).risk(2).score(.4).build())
        .build();
  }
  private List<AnalysisResult> getAnalysisResults(long... labels) {
    List<AnalysisResult> results = new ArrayList<>();
    for (int i = 0; i < labels.length; i++) {
      AnalysisResult analysisResult =
          AnalysisResult.builder()
              .count(3)
              .label(labels[i])
              .tag(i % 2 == 0 ? LogAnalysisResult.LogAnalysisTag.KNOWN : LogAnalysisResult.LogAnalysisTag.UNKNOWN)
              .build();
      results.add(analysisResult);
    }
    return results;
  }

  private List<LogAnalysisCluster> buildAnalysisClusters(long... labels) {
    List<LogAnalysisCluster> clusters = new ArrayList<>();
    for (long label : labels) {
      LogAnalysisCluster cluster =
          LogAnalysisCluster.builder()
              .label(label)
              .isEvicted(false)
              .text("exception message")
              .frequencyTrend(Arrays.asList(Frequency.builder().count(1).timestamp(12353453L).build(),
                  Frequency.builder().count(2).timestamp(12353453L).build(),
                  Frequency.builder().count(3).timestamp(12353453L).build(),
                  Frequency.builder().count(4).timestamp(12353453L).build()))
              .build();
      clusters.add(cluster);
    }
    return clusters;
  }

  private CVConfig createCVConfig() {
    SplunkCVConfig cvConfig = new SplunkCVConfig();
    fillCommon(cvConfig);
    cvConfig.setQuery("exception");
    cvConfig.setServiceInstanceIdentifier(generateUuid());
    return cvConfig;
  }

  private void fillCommon(CVConfig cvConfig) {
    cvConfig.setVerificationType(VerificationType.LOG);
    cvConfig.setAccountId(generateUuid());
    cvConfig.setConnectorIdentifier(generateUuid());
    cvConfig.setServiceIdentifier(generateUuid());
    cvConfig.setEnvIdentifier("prod" + generateUuid());
    cvConfig.setProjectIdentifier(generateUuid());
    cvConfig.setOrgIdentifier(generateUuid());
    cvConfig.setIdentifier(generateUuid());
    cvConfig.setMonitoringSourceName(generateUuid());
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    cvConfig.setProductName(generateUuid());
  }

  private void fillCommon(LearningEngineTask learningEngineTask, LearningEngineTaskType analysisType) {
    learningEngineTask.setTaskStatus(ExecutionStatus.QUEUED);
    learningEngineTask.setVerificationTaskId(verificationTaskId);
    learningEngineTask.setAnalysisType(analysisType);
    learningEngineTask.setFailureUrl("failure-url");
    learningEngineTask.setAnalysisStartTime(instant.minus(Duration.ofMinutes(10)));
    learningEngineTask.setAnalysisEndTime(instant);
  }

  private VerificationJob newTestVerificationJob() {
    TestVerificationJobDTO testVerificationJob = new TestVerificationJobDTO();
    testVerificationJob.setIdentifier(generateUuid());
    testVerificationJob.setJobName(generateUuid());
    testVerificationJob.setDataSources(Lists.newArrayList(DataSourceType.SPLUNK));
    testVerificationJob.setSensitivity(Sensitivity.MEDIUM.name());
    testVerificationJob.setServiceIdentifier(generateUuid());
    testVerificationJob.setOrgIdentifier(generateUuid());
    testVerificationJob.setProjectIdentifier(generateUuid());
    testVerificationJob.setEnvIdentifier("prod" + generateUuid());
    testVerificationJob.setSensitivity(Sensitivity.MEDIUM.name());
    testVerificationJob.setBaselineVerificationJobInstanceId("LAST");
    testVerificationJob.setDuration("15m");
    return verificationJobService.fromDto(testVerificationJob);
  }

  private VerificationJobInstance newVerificationJobInstance() {
    return VerificationJobInstance.builder()
        .accountId(accountId)
        .deploymentStartTime(instant)
        .startTime(instant.plus(Duration.ofMinutes(2)))
        .dataCollectionDelay(Duration.ofMinutes(5))
        .resolvedJob(newTestVerificationJob())
        .build();
  }
}
