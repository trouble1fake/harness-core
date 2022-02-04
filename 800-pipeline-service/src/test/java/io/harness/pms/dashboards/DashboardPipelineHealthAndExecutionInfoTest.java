/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dashboards;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.Dashboard.DashboardPipelineExecutionInfo;
import io.harness.pms.Dashboard.DashboardPipelineHealthInfo;
import io.harness.pms.Dashboard.PipelineCountInfo;
import io.harness.pms.Dashboard.PipelineExecutionInfo;
import io.harness.pms.Dashboard.StatusAndTime;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.service.PipelineDashboardServiceImpl;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class DashboardPipelineHealthAndExecutionInfoTest extends CategoryTest {
  @Mock TimeScaleDBService timeScaleDBService;
  @InjectMocks @Spy private PipelineDashboardServiceImpl pipelineDashboardService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashboardPipelineHealthInfoTest() {
    // this test tests only total and success components of Executions  and duration part of mean and median under
    // DashboardPipelineHealthInfo

    long startInterval = 1617235200000L;
    long endInterval = 1619740800000L;
    long previousStartInterval = 1614556800000L;

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(), ExecutionStatus.SUCCESS.name(),
        ExecutionStatus.ABORTED.name(), ExecutionStatus.SUCCESS.name());
    List<Long> time = Arrays.asList(
        1617624465000L, 1619006865000L, 1619721000000L, 1617129000000L, 1617235200000L, 1616610600000L, 1616697939000L);
    StatusAndTime statusAndTime = StatusAndTime.builder().status(status).time(time).build();

    doReturn(statusAndTime)
        .when(pipelineDashboardService)
        .queryBuilderSelectStatusAndTime(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());
    doReturn(0L)
        .when(pipelineDashboardService)
        .queryBuilderMean(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());

    doReturn(0L)
        .when(pipelineDashboardService)
        .queryBuilderMedian(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());

    DashboardPipelineHealthInfo dashboardPipelineHealthInfo = pipelineDashboardService.getDashboardPipelineHealthInfo(
        "ac", "or", "pr", "pip", startInterval, endInterval, previousStartInterval, "CI");

    // total count = 4, Previous total count = 3, current success = 3, previous success = 2
    assertThat(dashboardPipelineHealthInfo.getExecutions().getTotal().getCount()).isEqualTo(4);
    assertThat(dashboardPipelineHealthInfo.getExecutions().getTotal().getRate()).isEqualTo((1 / (double) 3) * 100);
    assertThat(dashboardPipelineHealthInfo.getExecutions().getSuccess().getPercent()).isEqualTo((3 / (double) 4) * 100);
    assertThat(dashboardPipelineHealthInfo.getExecutions().getSuccess().getRate())
        .isEqualTo(((3 - 2) / (double) 2) * 100);

    // test for empty status and time
    List<String> emptyStatus = new ArrayList<>();
    List<Long> emptyTime = new ArrayList<>();
    doReturn(StatusAndTime.builder().time(emptyTime).status(emptyStatus).build())
        .when(pipelineDashboardService)
        .queryBuilderSelectStatusAndTime(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());

    DashboardPipelineHealthInfo dashboardPipelineHealthInfoEmptyList =
        pipelineDashboardService.getDashboardPipelineHealthInfo(
            "ac", "or", "pr", "pip", 1617235200000L, 1619740800000L, 1614556800000L, "CI");
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getTotal().getCount()).isEqualTo(0);
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getTotal().getRate()).isEqualTo(0.0);
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getSuccess().getPercent()).isEqualTo(0.0);
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getSuccess().getRate()).isEqualTo(0.0);

    // Mean And Median duration test
    String table = "pipeline_execution_summary_ci";

    // currentMean
    doReturn(100L)
        .when(pipelineDashboardService)
        .queryBuilderMean("ac", "or", "pr", "pip", startInterval, 1619827200000L, table);

    // currentMedian
    doReturn(150L)
        .when(pipelineDashboardService)
        .queryBuilderMedian("ac", "or", "pr", "pip", startInterval, 1619827200000L, table);

    // PreviousMean
    doReturn(40L)
        .when(pipelineDashboardService)
        .queryBuilderMean("ac", "or", "pr", "pip", previousStartInterval, startInterval, table);

    // previousMedian
    doReturn(180L)
        .when(pipelineDashboardService)
        .queryBuilderMedian("ac", "or", "pr", "pip", previousStartInterval, startInterval, table);

    DashboardPipelineHealthInfo dashboardPipelineHealthInfoMeanMedian =
        pipelineDashboardService.getDashboardPipelineHealthInfo(
            "ac", "or", "pr", "pip", 1617235200000L, 1619740800000L, 1614556800000L, "CI");
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMeanInfo().getDuration()).isEqualTo(100L);
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMeanInfo().getRate()).isEqualTo(60L);
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMedianInfo().getDuration()).isEqualTo(150L);
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMedianInfo().getRate()).isEqualTo(-30L);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashboardPipelineExecutionInfo() {
    long startInterval = 1617235200000L;
    long endInterval = 1617580800000L;

    List<String> status = Arrays.asList(ExecutionStatus.SUCCESS.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.ABORTED.name(), ExecutionStatus.RUNNING.name(), ExecutionStatus.FAILED.name(),
        ExecutionStatus.ABORTED.name(), ExecutionStatus.EXPIRED.name());
    List<Long> time = Arrays.asList(
        1617365265000L, 1617365265000L, 1617321600000L, 1617580800000L, 1617408000000L, 1617235200000L, 1617302739000L);
    StatusAndTime statusAndTime = StatusAndTime.builder().status(status).time(time).build();

    doReturn(statusAndTime)
        .when(pipelineDashboardService)
        .queryBuilderSelectStatusAndTime(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());

    DashboardPipelineExecutionInfo dashboardPipelineExecutionInfo =
        pipelineDashboardService.getDashboardPipelineExecutionInfo(
            "ac", "or", "pr", "pip", startInterval, endInterval, "CI");

    List<PipelineExecutionInfo> pipelineExecutionInfoList = new ArrayList<>();
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617235200000L)
                                      .count(PipelineCountInfo.builder().total(2).success(0).failure(2).build())
                                      .build());
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617321600000L)
                                      .count(PipelineCountInfo.builder().total(3).success(1).failure(2).build())
                                      .build());
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617408000000L)
                                      .count(PipelineCountInfo.builder().total(1).success(0).failure(1).build())
                                      .build());
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617494400000L)
                                      .count(PipelineCountInfo.builder().total(0).success(0).failure(0).build())
                                      .build());
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617580800000L)
                                      .count(PipelineCountInfo.builder().total(1).success(0).failure(0).build())
                                      .build());
    assertThat(dashboardPipelineExecutionInfo.getPipelineExecutionInfoList()).isEqualTo(pipelineExecutionInfoList);
  }
}
