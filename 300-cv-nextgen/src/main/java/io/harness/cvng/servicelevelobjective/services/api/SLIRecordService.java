/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;

import java.time.Instant;
import java.util.List;

public interface SLIRecordService {
  void create(List<SLIRecordParam> sliRecordList, String sliId, String verificationTaskId, int sliVersion);
  SLOGraphData getGraphData(String sliId, Instant startTime, Instant endTime, int totalErrorBudgetMinutes,
      SLIMissingDataType sliMissingDataType, int sliVersion);
  void delete(List<String> sliIds);
}
