/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.ActivitySource;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.entities.HarnessCDCurrentGenActivity;
import io.harness.cvng.activity.entities.KubernetesActivity;
import io.harness.cvng.activity.entities.KubernetesActivitySource;
import io.harness.cvng.activity.entities.KubernetesClusterActivity;
import io.harness.cvng.activity.entities.PagerDutyActivity;
import io.harness.cvng.analysis.entities.CanaryLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.ClusteredLog;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.LogAnalysisRecord;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogClusterLearningEngineTask;
import io.harness.cvng.analysis.entities.ServiceGuardLogAnalysisTask;
import io.harness.cvng.analysis.entities.TestLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns;
import io.harness.cvng.analysis.entities.TimeSeriesCanaryLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesLoadTestLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVNGLog;
import io.harness.cvng.core.entities.CustomHealthCVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DatadogLogCVConfig;
import io.harness.cvng.core.entities.DatadogMetricCVConfig;
import io.harness.cvng.core.entities.DeletedCVConfig;
import io.harness.cvng.core.entities.DeploymentDataCollectionTask;
import io.harness.cvng.core.entities.ErrorTrackingCVConfig;
import io.harness.cvng.core.entities.HostRecord;
import io.harness.cvng.core.entities.LogCVConfig;
import io.harness.cvng.core.entities.LogRecord;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.NewRelicCVConfig;
import io.harness.cvng.core.entities.PagerDutyWebhook;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.SLIDataCollectionTask;
import io.harness.cvng.core.entities.ServiceDependency;
import io.harness.cvng.core.entities.ServiceGuardDataCollectionTask;
import io.harness.cvng.core.entities.SideKick;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.cvng.core.entities.StackdriverLogCVConfig;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.Webhook;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.cvng.core.entities.changeSource.PagerDutyChangeSource;
import io.harness.cvng.core.entities.demo.CVNGDemoDataIndex;
import io.harness.cvng.core.entities.demo.CVNGDemoPerpetualTask;
import io.harness.cvng.dashboard.entities.HealthVerificationHeatMap;
import io.harness.cvng.dashboard.entities.HeatMap;
import io.harness.cvng.migration.beans.CVNGSchema;
import io.harness.cvng.servicelevelobjective.entities.RatioServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLOErrorBudgetReset;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.UserJourney;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.verificationjob.entities.BlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob;
import io.harness.cvng.verificationjob.entities.CanaryVerificationJob;
import io.harness.cvng.verificationjob.entities.HealthVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

@OwnedBy(HarnessTeam.CV)
public class CVNextGenMorphiaRegister implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(Activity.class);
    set.add(ActivitySource.class);
    set.add(AnalysisOrchestrator.class);
    set.add(AnalysisStateMachine.class);
    set.add(HeatMap.class);
    set.add(DataCollectionTask.class);
    set.add(ServiceGuardDataCollectionTask.class);
    set.add(SLIDataCollectionTask.class);
    set.add(DeploymentDataCollectionTask.class);
    set.add(CVConfig.class);
    set.add(CVNGSchema.class);
    set.add(DataCollectionTask.class);
    set.add(DeletedCVConfig.class);
    set.add(SplunkCVConfig.class);
    set.add(AppDynamicsCVConfig.class);
    set.add(StackdriverCVConfig.class);
    set.add(LogCVConfig.class);
    set.add(MetricCVConfig.class);
    set.add(HealthVerificationHeatMap.class);
    set.add(HeatMap.class);
    set.add(LearningEngineTask.class);
    set.add(LogRecord.class);
    set.add(MetricPack.class);
    set.add(TimeSeriesRecord.class);
    set.add(TimeSeriesThreshold.class);
    set.add(TimeSeriesAnomalousPatterns.class);
    set.add(TimeSeriesCumulativeSums.class);
    set.add(TimeSeriesRiskSummary.class);
    set.add(TimeSeriesShortTermHistory.class);
    set.add(VerificationJobInstance.class);
    set.add(VerificationJob.class);
    set.add(VerificationTask.class);
    set.add(ClusteredLog.class);
    set.add(BlueGreenVerificationJob.class);
    set.add(DeploymentDataCollectionTask.class);
    set.add(AppDynamicsCVConfig.class);
    set.add(DeploymentLogAnalysis.class);
    set.add(TestVerificationJob.class);
    set.add(DeploymentTimeSeriesAnalysis.class);
    set.add(StackdriverCVConfig.class);
    set.add(TestLogAnalysisLearningEngineTask.class);
    set.add(TimeSeriesLoadTestLearningEngineTask.class);
    set.add(MetricCVConfig.class);
    set.add(HostRecord.class);
    set.add(HealthVerificationJob.class);
    set.add(SplunkCVConfig.class);
    set.add(KubernetesActivity.class);
    set.add(TimeSeriesCanaryLearningEngineTask.class);
    set.add(CanaryLogAnalysisLearningEngineTask.class);
    set.add(LogCVConfig.class);
    set.add(ServiceGuardDataCollectionTask.class);
    set.add(LogAnalysisCluster.class);
    set.add(LogClusterLearningEngineTask.class);
    set.add(LogAnalysisLearningEngineTask.class);
    set.add(LogAnalysisResult.class);
    set.add(KubernetesActivitySource.class);
    set.add(LogAnalysisRecord.class);
    set.add(CanaryVerificationJob.class);
    set.add(CanaryBlueGreenVerificationJob.class);
    set.add(DeploymentActivity.class);
    set.add(ServiceGuardLogAnalysisTask.class);
    set.add(TimeSeriesLearningEngineTask.class);
    set.add(CVNGLog.class);
    set.add(MonitoringSourcePerpetualTask.class);
    set.add(NewRelicCVConfig.class);
    set.add(CVNGStepTask.class);
    set.add(Comparable.class);
    set.add(PrometheusCVConfig.class);
    set.add(CustomHealthCVConfig.class);
    set.add(StackdriverLogCVConfig.class);
    set.add(DatadogMetricCVConfig.class);
    set.add(DatadogLogCVConfig.class);
    set.add(ErrorTrackingCVConfig.class);
    set.add(MonitoredService.class);
    set.add(HarnessCDChangeSource.class);
    set.add(ChangeSource.class);
    set.add(ServiceDependency.class);
    set.add(PagerDutyChangeSource.class);
    set.add(KubernetesChangeSource.class);
    set.add(KubernetesClusterActivity.class);
    set.add(Webhook.class);
    set.add(PagerDutyWebhook.class);
    set.add(PagerDutyActivity.class);
    set.add(HarnessCDCurrentGenChangeSource.class);
    set.add(HarnessCDCurrentGenActivity.class);
    set.add(ServiceLevelObjective.class);
    set.add(UserJourney.class);
    set.add(CVNGDemoDataIndex.class);
    set.add(CVNGDemoPerpetualTask.class);
    set.add(ServiceLevelIndicator.class);
    set.add(RatioServiceLevelIndicator.class);
    set.add(SLOHealthIndicator.class);
    set.add(ThresholdServiceLevelIndicator.class);
    set.add(SLIRecord.class);
    set.add(SideKick.class);
    set.add(SLOErrorBudgetReset.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes to register
  }
}
