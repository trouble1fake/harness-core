/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.pms.Dashboard.StatusAndTime;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCd;
import io.harness.timescaledb.tables.pojos.PipelineExecutionSummaryCi;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class QueryService {
  @Inject private DSLContext dsl;
  @Inject TimeScaleDBService timeScaleDBService;

  private String CD_TableName = "pipeline_execution_summary_cd";

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
}
