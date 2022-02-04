/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVConstants.MONGO_QUERY_TIMEOUT_SEC;
import static io.harness.cvng.CVConstants.SERVICE_BASE_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.ANALYSIS_RISK_RESULTS_LIMIT;
import static io.harness.cvng.analysis.CVAnalysisConstants.DEPLOYMENT_LOG_ANALYSIS_SAVE_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.LOG_ANALYSIS_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.LOG_ANALYSIS_SAVE_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.PREVIOUS_ANALYSIS_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.PREVIOUS_LOG_ANALYSIS_PATH;
import static io.harness.cvng.analysis.CVAnalysisConstants.TEST_DATA_PATH;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.analysis.entities.CanaryLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis.DeploymentLogAnalysisKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisCluster.LogAnalysisClusterKeys;
import io.harness.cvng.analysis.entities.LogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.LogAnalysisRecord.LogAnalysisRecordKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.AnalysisResult.AnalysisResultKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisResultKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.analysis.entities.ServiceGuardLogAnalysisTask;
import io.harness.cvng.analysis.entities.TestLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.LogClusterService;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.LogCVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.AnalysisProgressLog;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ProgressLog;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Sort;

@Slf4j
public class LogAnalysisServiceImpl implements LogAnalysisService {
  @Inject private HPersistence hPersistence;
  @Inject private LearningEngineTaskService learningEngineTaskService;
  @Inject private LogClusterService logClusterService;
  @Inject private HeatMapService heatMapService;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private HostRecordService hostRecordService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;

  @Override
  public String scheduleServiceGuardLogAnalysisTask(AnalysisInput input) {
    ServiceGuardLogAnalysisTask task = createServiceGuardLogAnalysisTask(input);
    log.info("Scheduling ServiceGuardLogAnalysisTask {}", task);
    return learningEngineTaskService.createLearningEngineTask(task);
  }

  private ServiceGuardLogAnalysisTask createServiceGuardLogAnalysisTask(AnalysisInput input) {
    String taskId = generateUuid();
    String cvConfigId = verificationTaskService.getCVConfigId(input.getVerificationTaskId());
    LogCVConfig cvConfig = (LogCVConfig) cvConfigService.get(cvConfigId);
    ServiceGuardLogAnalysisTask task = ServiceGuardLogAnalysisTask.builder()
                                           .frequencyPatternUrl(createFrequencyPatternUrl(input))
                                           .testDataUrl(createTestDataUrl(input))
                                           .build();

    if (input.getEndTime().isAfter(cvConfig.getBaseline().getStartTime())
        && input.getEndTime().compareTo(cvConfig.getBaseline().getEndTime()) <= 0) {
      task.setBaselineWindow(true);
    }
    task.setAnalysisType(LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS);
    task.setAnalysisStartTime(input.getStartTime());
    task.setVerificationTaskId(input.getVerificationTaskId());
    task.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    task.setAnalysisEndTime(input.getEndTime());
    task.setAnalysisEndEpochMinute(DateTimeUtils.instantToEpochMinute(input.getEndTime()));
    task.setAnalysisSaveUrl(createAnalysisSaveUrl(input, taskId));
    task.setUuid(taskId);
    return task;
  }

  @Override
  public String scheduleDeploymentLogAnalysisTask(AnalysisInput analysisInput) {
    LogAnalysisLearningEngineTask task = createLogCanaryAnalysisLearningEngineTask(analysisInput);
    log.info("Scheduling LogCanaryAnalysisLearningEngineTask {}", task);
    return learningEngineTaskService.createLearningEngineTask(task);
  }

  private LogAnalysisLearningEngineTask createLogCanaryAnalysisLearningEngineTask(AnalysisInput input) {
    String taskId = generateUuid();
    String verificationJobInstanceId =
        verificationTaskService.getVerificationJobInstanceId(input.getVerificationTaskId());
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJobInstanceService.getPreDeploymentTimeRange(verificationJobInstanceId);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    LogAnalysisLearningEngineTask task = null;
    if (preDeploymentTimeRange.isPresent()) {
      task = CanaryLogAnalysisLearningEngineTask.builder()
                 .controlHosts(getControlHosts(input.getVerificationTaskId(), preDeploymentTimeRange.get()))
                 .build();
      task.setControlDataUrl(createDeploymentDataUrl(input.getVerificationTaskId(),
          preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime()));
      task.setAnalysisType(LearningEngineTaskType.CANARY_LOG_ANALYSIS);
    } else {
      TestVerificationJob testVerificationJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
      String baselineVerificationJobInstanceId = testVerificationJob.getBaselineVerificationJobInstanceId();
      task = TestLogAnalysisLearningEngineTask.builder().build();
      task.setAnalysisType(LearningEngineTaskType.TEST_LOG_ANALYSIS);
      if (baselineVerificationJobInstanceId != null) {
        VerificationJobInstance baselineVerificationJobInstance =
            verificationJobInstanceService.getVerificationJobInstance(baselineVerificationJobInstanceId);
        Optional<String> baselineVerificationTaskId = verificationTaskService.findBaselineVerificationTaskId(
            input.getVerificationTaskId(), verificationJobInstance);
        task.setControlDataUrl(baselineVerificationTaskId.isPresent()
                ? createDeploymentDataUrl(baselineVerificationTaskId.get(),
                    baselineVerificationJobInstance.getStartTime(), baselineVerificationJobInstance.getEndTime())
                : null);
      }
    }
    task.setPreviousAnalysisUrl(
        getPreviousAnalysisUrl(input.getVerificationTaskId(), input.getStartTime(), input.getEndTime()));
    task.setTestDataUrl(
        createDeploymentDataUrl(input.getVerificationTaskId(), input.getStartTime(), input.getEndTime()));
    task.setAnalysisStartTime(input.getStartTime());
    task.setVerificationTaskId(input.getVerificationTaskId());
    task.setFailureUrl(learningEngineTaskService.createFailureUrl(taskId));
    task.setAnalysisEndTime(input.getEndTime());
    task.setAnalysisEndEpochMinute(DateTimeUtils.instantToEpochMinute(input.getEndTime()));
    task.setAnalysisSaveUrl(createDeploymentAnalysisSaveUrl(taskId));
    task.setUuid(taskId);
    return task;
  }

  private String getPreviousAnalysisUrl(String verificationTaskId, Instant startTime, Instant endTime) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_ANALYSIS_RESOURCE + "/" + PREVIOUS_ANALYSIS_URL);
    uriBuilder.addParameter("verificationTaskId", verificationTaskId);
    uriBuilder.addParameter(LogAnalysisRecordKeys.analysisStartTime, String.valueOf(startTime.toEpochMilli()));
    uriBuilder.addParameter(LogAnalysisRecordKeys.analysisEndTime, String.valueOf(endTime.toEpochMilli()));
    return getUriString(uriBuilder);
  }

  private Set<String> getControlHosts(String verificationTaskId, TimeRange preDeploymentTimeRange) {
    return hostRecordService.get(
        verificationTaskId, preDeploymentTimeRange.getStartTime(), preDeploymentTimeRange.getEndTime());
  }
  private String createDeploymentDataUrl(String verificationTaskId, Instant startTime, Instant endTime) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_ANALYSIS_RESOURCE + "/" + TEST_DATA_PATH);
    uriBuilder.addParameter("verificationTaskId", verificationTaskId);
    uriBuilder.addParameter(LogAnalysisRecordKeys.analysisStartTime, String.valueOf(startTime.toEpochMilli()));
    uriBuilder.addParameter(LogAnalysisRecordKeys.analysisEndTime, String.valueOf(endTime.toEpochMilli()));
    return getUriString(uriBuilder);
  }

  @Override
  public List<LogClusterDTO> getTestData(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime) {
    return logClusterService.getClusteredLogData(
        verificationTaskId, analysisStartTime, analysisEndTime, LogClusterLevel.L2);
  }

  @Override
  public List<LogAnalysisCluster> getPreviousAnalysis(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime) {
    List<LogAnalysisCluster> logAnalysisClusters =
        hPersistence.createQuery(LogAnalysisCluster.class, excludeAuthority)
            .filter(LogAnalysisClusterKeys.verificationTaskId, verificationTaskId)
            .filter(LogAnalysisClusterKeys.isEvicted, false)
            .project(LogAnalysisClusterKeys.analysisMinute, true)
            .project(LogAnalysisClusterKeys.label, true)
            .project(LogAnalysisClusterKeys.text, true)
            .project(LogAnalysisClusterKeys.compressedText, true)
            .project(LogAnalysisClusterKeys.frequencyTrend, true)
            .project(LogAnalysisClusterKeys.firstSeenTime, true)
            .asList(new FindOptions().maxTime(MONGO_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS));
    return logAnalysisClusters.stream().peek(LogAnalysisCluster::deCompressText).collect(Collectors.toList());
  }

  @Override
  public DeploymentLogAnalysisDTO getPreviousDeploymentAnalysis(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime) {
    DeploymentLogAnalysis logAnalysis = hPersistence.createQuery(DeploymentLogAnalysis.class, excludeAuthority)
                                            .filter(DeploymentLogAnalysisKeys.verificationTaskId, verificationTaskId)
                                            .field(DeploymentLogAnalysisKeys.startTime)
                                            .lessThan(analysisStartTime)
                                            .order(Sort.descending(DeploymentLogAnalysisKeys.startTime))
                                            .get();

    if (logAnalysis == null) {
      return null;
    }

    return DeploymentLogAnalysisDTO.builder()
        .clusterCoordinates(logAnalysis.getClusterCoordinates())
        .hostSummaries(logAnalysis.getHostSummaries())
        .resultSummary(logAnalysis.getResultSummary())
        .clusters(logAnalysis.getClusters())
        .build();
  }

  @Override
  public void saveAnalysis(String taskId, LogAnalysisDTO analysisBody) {
    LearningEngineTask learningEngineTask = learningEngineTaskService.get(taskId);
    Preconditions.checkNotNull(learningEngineTask, "Needs to be a valid LE task.");
    log.info("Saving service guard log analysis for verificationTaskid {} and taskId {}",
        learningEngineTask.getVerificationTaskId(), taskId);
    analysisBody.setVerificationTaskId(learningEngineTask.getVerificationTaskId());
    analysisBody.setAnalysisStartTime(learningEngineTask.getAnalysisStartTime());
    analysisBody.setAnalysisEndTime(learningEngineTask.getAnalysisEndTime());
    List<LogAnalysisCluster> analysisClusters =
        analysisBody.toAnalysisClusters(learningEngineTask.getVerificationTaskId(),
            learningEngineTask.getAnalysisStartTime(), learningEngineTask.getAnalysisEndTime());
    LogAnalysisResult analysisResult = analysisBody.toAnalysisResult(learningEngineTask.getVerificationTaskId(),
        learningEngineTask.getAnalysisStartTime(), learningEngineTask.getAnalysisEndTime());
    log.info("Saving {} analyzed log clusters for LETask", learningEngineTask);
    // first delete the existing records which have not been evicted.
    hPersistence.delete(
        hPersistence.createQuery(LogAnalysisCluster.class)
            .filter(LogAnalysisClusterKeys.verificationTaskId, learningEngineTask.getVerificationTaskId())
            .filter(LogAnalysisClusterKeys.isEvicted, false));
    // next, save the new records.
    // TODO: move this to LogAnalysisClusterService
    hPersistence.save(analysisClusters);
    // TODO: move this to LogAnalysisResultService
    hPersistence.save(analysisResult);
    learningEngineTaskService.markCompleted(taskId);
    CVConfig cvConfig =
        cvConfigService.get(verificationTaskService.getCVConfigId(learningEngineTask.getVerificationTaskId()));
    Preconditions.checkNotNull(
        cvConfig, String.format("Invalid verification task id: [%s]", learningEngineTask.getVerificationTaskId()));
    long anomalousLogCount = analysisBody.getLogAnalysisResults()
                                 .stream()
                                 .filter(result -> LogAnalysisTag.getAnomalousTags().contains(result.getTag()))
                                 .count();
    heatMapService.updateRiskScore(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
        cvConfig.getProjectIdentifier(), cvConfig.getServiceIdentifier(), cvConfig.getEnvIdentifier(), cvConfig,
        cvConfig.getCategory(), learningEngineTask.getAnalysisStartTime(), analysisBody.getScore(), 0,
        anomalousLogCount);
  }

  @Override
  public void saveAnalysis(String learningEngineTaskId, DeploymentLogAnalysisDTO deploymentLogAnalysisDTO) {
    LearningEngineTask learningEngineTask = learningEngineTaskService.get(learningEngineTaskId);
    Preconditions.checkNotNull(learningEngineTask, "Needs to be a valid LE task.");
    learningEngineTaskService.markCompleted(learningEngineTaskId);

    DeploymentLogAnalysis deploymentLogAnalysis =
        DeploymentLogAnalysis.builder()
            .startTime(learningEngineTask.getAnalysisStartTime())
            .endTime(learningEngineTask.getAnalysisEndTime())
            .verificationTaskId(learningEngineTask.getVerificationTaskId())
            .hostSummaries(deploymentLogAnalysisDTO.getHostSummaries())
            .resultSummary(deploymentLogAnalysisDTO.getResultSummary())
            .clusters(deploymentLogAnalysisDTO.getClusters())
            .clusterCoordinates(deploymentLogAnalysisDTO.getClusterCoordinates())
            .build();
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
  }

  @Override
  public void logDeploymentVerificationProgress(AnalysisInput inputs, AnalysisStatus finalStatus) {
    ProgressLog progressLog =
        AnalysisProgressLog.builder()
            .startTime(inputs.getStartTime())
            .endTime(inputs.getEndTime())
            .analysisStatus(finalStatus)
            .isFinalState(
                true) // TODO: analysis is the final state now. We need to change it once feedback is implemented
            .log("Log analysis")
            .verificationTaskId(inputs.getVerificationTaskId())
            .build();
    verificationJobInstanceService.logProgress(progressLog);
  }

  @Override
  public List<LogAnalysisResult> getTopLogAnalysisResults(
      List<String> verificationTaskIds, Instant startTime, Instant endTime) {
    List<LogAnalysisResult> logAnalysisResults = hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority)
                                                     .field(LogAnalysisResultKeys.verificationTaskId)
                                                     .in(verificationTaskIds)
                                                     .field(LogAnalysisResultKeys.analysisEndTime)
                                                     .greaterThanOrEq(startTime)
                                                     .field(LogAnalysisResultKeys.analysisEndTime)
                                                     .lessThan(endTime)
                                                     .order(Sort.descending(LogAnalysisResultKeys.overallRisk))
                                                     .asList(new FindOptions().limit(ANALYSIS_RISK_RESULTS_LIMIT));
    Collections.sort(logAnalysisResults, Comparator.comparingDouble(LogAnalysisResult::getOverallRisk));
    return logAnalysisResults;
  }

  private String createDeploymentAnalysisSaveUrl(String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_ANALYSIS_RESOURCE + "/" + DEPLOYMENT_LOG_ANALYSIS_SAVE_PATH);
    uriBuilder.addParameter("taskId", taskId);
    return getUriString(uriBuilder);
  }

  private String createAnalysisSaveUrl(AnalysisInput input, String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_ANALYSIS_RESOURCE + "/" + LOG_ANALYSIS_SAVE_PATH);
    uriBuilder.addParameter("taskId", taskId);
    return getUriString(uriBuilder);
  }

  private String createFrequencyPatternUrl(AnalysisInput input) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_ANALYSIS_RESOURCE + "/" + PREVIOUS_LOG_ANALYSIS_PATH);
    uriBuilder.addParameter(LogAnalysisRecordKeys.verificationTaskId, input.getVerificationTaskId());
    uriBuilder.addParameter(
        LogAnalysisRecordKeys.analysisStartTime, input.getStartTime().minus(5, ChronoUnit.MINUTES).toString());
    uriBuilder.addParameter(
        LogAnalysisRecordKeys.analysisEndTime, input.getEndTime().minus(5, ChronoUnit.MINUTES).toString());
    return getUriString(uriBuilder);
  }

  private String createTestDataUrl(AnalysisInput input) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LOG_ANALYSIS_RESOURCE + "/" + TEST_DATA_PATH);
    uriBuilder.addParameter("verificationTaskId", input.getVerificationTaskId());
    uriBuilder.addParameter(
        LogAnalysisRecordKeys.analysisStartTime, String.valueOf(input.getStartTime().toEpochMilli()));
    uriBuilder.addParameter(LogAnalysisRecordKeys.analysisEndTime, String.valueOf(input.getEndTime().toEpochMilli()));
    return getUriString(uriBuilder);
  }

  private String getUriString(URIBuilder uriBuilder) {
    try {
      return uriBuilder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Map<String, ExecutionStatus> getTaskStatus(List<String> taskIds) {
    return learningEngineTaskService.getTaskStatus(new HashSet<>(taskIds));
  }

  @Override
  public List<LogAnalysisCluster> getAnalysisClusters(String verificationTaskId, Set<Long> labels) {
    return hPersistence.createQuery(LogAnalysisCluster.class, excludeAuthority)
        .filter(LogAnalysisClusterKeys.verificationTaskId, verificationTaskId)
        .field(LogAnalysisClusterKeys.label)
        .in(labels)
        .asList(new FindOptions().maxTime(MONGO_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS));
  }

  @Override
  public List<LogAnalysisResult> getAnalysisResults(
      String verificationTaskId, List<LogAnalysisTag> tags, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority)
        .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
        .field(LogAnalysisResultKeys.analysisStartTime)
        .greaterThanOrEq(startTime)
        .field(LogAnalysisResultKeys.analysisEndTime)
        .lessThanOrEq(endTime)
        .field(LogAnalysisResultKeys.logAnalysisResults + "." + AnalysisResultKeys.tag)
        .in(tags)
        .asList(new FindOptions().maxTime(MONGO_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS));
  }

  @Override
  public List<LogAnalysisResult> getAnalysisResults(String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority)
        .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
        .field(LogAnalysisResultKeys.analysisStartTime)
        .greaterThanOrEq(startTime)
        .field(LogAnalysisResultKeys.analysisEndTime)
        .lessThanOrEq(endTime)
        .asList(new FindOptions().maxTime(MONGO_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS));
  }

  @Override
  public LogAnalysisResult getLatestAnalysisForVerificationTaskId(
      String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority)
        .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
        .field(LogAnalysisResultKeys.analysisEndTime)
        .greaterThanOrEq(startTime)
        .field(LogAnalysisResultKeys.analysisEndTime)
        .lessThanOrEq(endTime)
        .order(Sort.descending(LogAnalysisResultKeys.analysisEndTime))
        .get();
  }
}
