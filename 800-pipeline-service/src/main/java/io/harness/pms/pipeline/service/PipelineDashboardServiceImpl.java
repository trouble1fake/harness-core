/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.NGDateUtils.DAY_IN_MS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.Dashboard.DashboardPipelineExecutionInfo;
import io.harness.pms.Dashboard.DashboardPipelineHealthInfo;
import io.harness.pms.Dashboard.MeanMedianInfo;
import io.harness.pms.Dashboard.PipelineCountInfo;
import io.harness.pms.Dashboard.PipelineExecutionInfo;
import io.harness.pms.Dashboard.PipelineHealthInfo;
import io.harness.pms.Dashboard.StatusAndTime;
import io.harness.pms.Dashboard.SuccessHealthInfo;
import io.harness.pms.Dashboard.TotalHealthInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCd;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCi;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class PipelineDashboardServiceImpl implements PipelineDashboardService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private DSLContext dsl;

  private String tableName_default = "pipeline_execution_summary_ci";
  private String CI_TableName = "pipeline_execution_summary_ci";
  private String CD_TableName = "pipeline_execution_summary_cd";
  private List<String> failedList = Arrays.asList(ExecutionStatus.FAILED.name(), ExecutionStatus.ABORTED.name(),
      ExecutionStatus.EXPIRED.name(), ExecutionStatus.IGNOREFAILED.name(), ExecutionStatus.ERRORED.name());

  private static final int MAX_RETRY_COUNT = 5;

  public StatusAndTime queryBuilderSelectStatusAndTime(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, String tableName) {
    List<PipelineExecutionSummaryCd> pipelineExecutionSummaryCds;
    List<PipelineExecutionSummaryCi> pipelineExecutionSummaryCis;
    List<String> status = new ArrayList<>();
    List<Long> time = new ArrayList<>();
    if (tableName == CD_TableName) {
      pipelineExecutionSummaryCds =
          this.dsl.select(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STATUS, Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS)
              .from(Tables.PIPELINE_EXECUTION_SUMMARY_CD)
              .where(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountId)
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq(orgId))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER.eq(pipelineId))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(projectId))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval))
                         .and(accountId != null)
                         .and(orgId != null)
                         .and(projectId != null)
                         .and(pipelineId != null)
                         .and(startInterval > 0)
                         .and(endInterval > 0))
              .fetch()
              .into(PipelineExecutionSummaryCd.class);
      for (PipelineExecutionSummaryCd x : pipelineExecutionSummaryCds) {
        status.add(x.getStatus());
        time.add(x.getStartts());
      }
    } else {
      pipelineExecutionSummaryCis =
          this.dsl.select(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STATUS, Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS)
              .from(Tables.PIPELINE_EXECUTION_SUMMARY_CI)
              .where(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ACCOUNTID.eq(accountId)
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ORGIDENTIFIER.eq(orgId))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PIPELINEIDENTIFIER.eq(pipelineId))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PROJECTIDENTIFIER.eq(projectId))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.ge(startInterval))
                         .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.lt(endInterval))
                         .and(accountId != null)
                         .and(orgId != null)
                         .and(projectId != null)
                         .and(pipelineId != null)
                         .and(startInterval > 0)
                         .and(endInterval > 0))
              .fetch()
              .into(PipelineExecutionSummaryCi.class);
      for (PipelineExecutionSummaryCi x : pipelineExecutionSummaryCis) {
        status.add(x.getStatus());
        time.add(x.getStartts());
      }
    }
    return StatusAndTime.builder().status(status).time(time).build();
  }

  public double getRate(long current, long previous) {
    double rate = 0.0;
    if (previous != 0) {
      rate = (current - previous) / (double) previous;
    }
    rate = rate * 100.0;
    return rate;
  }

  public double successPercent(long success, long total) {
    double rate = 0.0;
    if (total != 0) {
      rate = success / (double) total;
    }
    rate = rate * 100.0;
    return rate;
  }

  public long queryBuilderMean(String accountId, String orgId, String projectId, String pipelineId, long startInterval,
      long endInterval, String tableName) {
    @NotNull List<Long> mean;
    if (tableName == CD_TableName) {
      mean = this.dsl
                 .select(DSL.avg(
                     (Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS.sub(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS))
                         .div(1000)))
                 .from(Tables.PIPELINE_EXECUTION_SUMMARY_CD)
                 .where(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountId)
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq(orgId))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER.eq(pipelineId))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(projectId))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval))
                            .and(accountId != null)
                            .and(orgId != null)
                            .and(projectId != null)
                            .and(pipelineId != null)
                            .and(startInterval > 0)
                            .and(endInterval > 0)
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS != null))
                 .fetch()
                 .into(long.class);
    } else {
      mean = this.dsl
                 .select(DSL.avg(
                     (Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS.sub(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS))
                         .div(1000)))
                 .from(Tables.PIPELINE_EXECUTION_SUMMARY_CI)
                 .where(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ACCOUNTID.eq(accountId)
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ORGIDENTIFIER.eq(orgId))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PIPELINEIDENTIFIER.eq(pipelineId))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PROJECTIDENTIFIER.eq(projectId))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.ge(startInterval))
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.lt(endInterval))
                            .and(accountId != null)
                            .and(orgId != null)
                            .and(projectId != null)
                            .and(pipelineId != null)
                            .and(startInterval > 0)
                            .and(endInterval > 0)
                            .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS != null))
                 .fetch()
                 .into(long.class);
    }
    return mean.get(0);
  }

  public long queryBuilderMedian(String accountId, String orgId, String projectId, String pipelineId,
      long startInterval, long endInterval, String tableName) {
    @NotNull List<Long> median;
    if (tableName == CD_TableName) {
      median = this.dsl
                   .select(DSL.percentileDisc(0.5).withinGroupOrderBy(
                       (Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS.sub(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS))
                           .div(1000)))
                   .from(Tables.PIPELINE_EXECUTION_SUMMARY_CD)
                   .where(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountId)
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq(orgId))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER.eq(pipelineId))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(projectId))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval))
                              .and(accountId != null)
                              .and(orgId != null)
                              .and(projectId != null)
                              .and(pipelineId != null)
                              .and(startInterval > 0)
                              .and(endInterval > 0)
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS != null))
                   .fetch()
                   .into(long.class);
    } else {
      median = this.dsl
                   .select(DSL.percentileDisc(0.5).withinGroupOrderBy(
                       (Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS.sub(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS))
                           .div(1000)))
                   .from(Tables.PIPELINE_EXECUTION_SUMMARY_CI)
                   .where(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ACCOUNTID.eq(accountId)
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ORGIDENTIFIER.eq(orgId))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PIPELINEIDENTIFIER.eq(pipelineId))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PROJECTIDENTIFIER.eq(projectId))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.ge(startInterval))
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.lt(endInterval))
                              .and(accountId != null)
                              .and(orgId != null)
                              .and(projectId != null)
                              .and(pipelineId != null)
                              .and(startInterval > 0)
                              .and(endInterval > 0)
                              .and(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS != null))
                   .fetch()
                   .into(long.class);
    }
    return median.get(0);
  }

  public String selectTableFromModuleInfo(String moduleInfo) {
    String tableName = tableName_default;
    if (moduleInfo.equalsIgnoreCase("CI")) {
      tableName = CI_TableName;
    } else if (moduleInfo.equalsIgnoreCase("CD")) {
      tableName = CD_TableName;
    }
    return tableName;
  }

  @Override
  public DashboardPipelineHealthInfo getDashboardPipelineHealthInfo(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, long previousStartInterval, String moduleInfo) {
    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);
    previousStartInterval = getStartingDateEpochValue(previousStartInterval);

    endInterval = endInterval + DAY_IN_MS;

    String tableName = selectTableFromModuleInfo(moduleInfo);

    StatusAndTime statusAndTime = queryBuilderSelectStatusAndTime(
        accountId, orgId, projectId, pipelineId, previousStartInterval, endInterval, tableName);

    List<String> status = statusAndTime.getStatus();
    List<Long> time = statusAndTime.getTime();

    long currentTotal = 0, currentSuccess = 0;
    long previousTotal = 0, previousSuccess = 0;
    for (int i = 0; i < time.size(); i++) {
      long variableEpoch = time.get(i);
      if (variableEpoch >= startInterval && variableEpoch < endInterval) {
        currentTotal++;
        if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
          currentSuccess++;
        }
      }

      // previous interval record
      if (previousStartInterval <= variableEpoch && startInterval > variableEpoch) {
        previousTotal++;
        if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
          previousSuccess++;
        }
      }
    }

    // mean calculation
    long currentMean = queryBuilderMean(accountId, orgId, projectId, pipelineId, startInterval, endInterval, tableName);

    long previousMean =
        queryBuilderMean(accountId, orgId, projectId, pipelineId, previousStartInterval, startInterval, tableName);

    // Median calculation
    long currentMedian =
        queryBuilderMedian(accountId, orgId, projectId, pipelineId, startInterval, endInterval, tableName);

    long previousMedian =
        queryBuilderMedian(accountId, orgId, projectId, pipelineId, previousStartInterval, startInterval, tableName);

    return DashboardPipelineHealthInfo.builder()
        .executions(
            PipelineHealthInfo.builder()
                .total(TotalHealthInfo.builder().count(currentTotal).rate(getRate(currentTotal, previousTotal)).build())
                .success(SuccessHealthInfo.builder()
                             .rate(getRate(currentSuccess, previousSuccess))
                             .percent(successPercent(currentSuccess, currentTotal))
                             .build())
                .meanInfo(MeanMedianInfo.builder().duration(currentMean).rate(currentMean - previousMean).build())
                .medianInfo(
                    MeanMedianInfo.builder().duration(currentMedian).rate(currentMedian - previousMedian).build())
                .build())
        .build();
  }

  @Override
  public DashboardPipelineExecutionInfo getDashboardPipelineExecutionInfo(String accountId, String orgId,
      String projectId, String pipelineId, long startInterval, long endInterval, String moduleInfo) {
    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);

    endInterval = endInterval + DAY_IN_MS;

    String tableName = selectTableFromModuleInfo(moduleInfo);

    StatusAndTime statusAndTime =
        queryBuilderSelectStatusAndTime(accountId, orgId, projectId, pipelineId, startInterval, endInterval, tableName);
    List<String> status = statusAndTime.getStatus();
    List<Long> time = statusAndTime.getTime();

    List<PipelineExecutionInfo> pipelineExecutionInfoList = new ArrayList<>();

    while (startInterval < endInterval) {
      long total = 0, success = 0, failed = 0;
      for (int i = 0; i < time.size(); i++) {
        if (startInterval == getStartingDateEpochValue(time.get(i))) {
          total++;
          if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
            success++;
          } else if (failedList.contains(status.get(i))) {
            failed++;
          }
        }
      }
      pipelineExecutionInfoList.add(
          PipelineExecutionInfo.builder()
              .date(startInterval)
              .count(PipelineCountInfo.builder().total(total).success(success).failure(failed).build())
              .build());
      startInterval = startInterval + DAY_IN_MS;
    }

    return DashboardPipelineExecutionInfo.builder().pipelineExecutionInfoList(pipelineExecutionInfoList).build();
  }

  public long getStartingDateEpochValue(long epochValue) {
    return epochValue - epochValue % DAY_IN_MS;
  }
}
