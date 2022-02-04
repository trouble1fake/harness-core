/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.beans.TimeSeriesTestDataDTO;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.beans.TimeSeriesDataCollectionRecord;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.core.beans.demo.DemoTemplate;
import io.harness.cvng.core.entities.TimeSeriesRecord;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface TimeSeriesRecordService {
  boolean save(List<TimeSeriesDataCollectionRecord> dataRecords);

  boolean updateRiskScores(String verificationTaskId, TimeSeriesRiskSummary riskSummary);

  List<TimeSeriesMetricDefinition> getTimeSeriesMetricDefinitions(String cvConfigId);
  TimeSeriesTestDataDTO getTxnMetricDataForRange(
      String verificationTaskId, Instant startTime, Instant endTime, String metricName, String txnName);

  /**
   * startTime is inclusive and endTime is exclusive.
   * @param verificationTaskId
   * @param startTime inclusive
   * @param endTime exclusive
   * @return
   */
  List<TimeSeriesRecordDTO> getTimeSeriesRecordDTOs(String verificationTaskId, Instant startTime, Instant endTime);
  TimeSeriesTestDataDTO getMetricGroupDataForRange(
      String verificationTaskId, Instant startTime, Instant endTime, String metricName, List<String> groupNames);

  List<TimeSeriesRecord> getTimeSeriesRecordsForConfigs(
      List<String> verificationTaskIds, Instant startTime, Instant endTime, boolean anomalousOnly);

  void createDemoAnalysisData(String accountId, String verificationTaskId, String dataCollectionWorkerId,
      DemoTemplate demoTemplate, Instant startTime, Instant endTime) throws IOException;
}
