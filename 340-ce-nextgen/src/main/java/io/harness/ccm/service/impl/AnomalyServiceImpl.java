/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.ccm.commons.dao.anomaly.AnomalyDao;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveQueryDTO;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.service.intf.AnomalyService;
import io.harness.timescaledb.tables.pojos.Anomalies;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.jooq.Condition;
import org.jooq.impl.DSL;

public class AnomalyServiceImpl implements AnomalyService {
  @Inject AnomalyDao anomalyDao;
  @Inject AnomalyQueryBuilder anomalyQueryBuilder;

  @Override
  public List<AnomalyData> listAnomalies(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery) {
    Condition condition = anomalyQuery.getFilter() != null
        ? anomalyQueryBuilder.applyAllFilters(anomalyQuery.getFilter())
        : DSL.noCondition();
    List<Anomalies> anomalies = anomalyDao.fetchAnomalies(accountIdentifier, condition,
        anomalyQueryBuilder.getOrderByFields(anomalyQuery.getOrderBy()), anomalyQuery.getOffset(),
        anomalyQuery.getLimit());

    List<AnomalyData> anomalyData = new ArrayList<>();

    anomalies.forEach(anomaly -> anomalyData.add(buildAnomalyData(anomaly)));
    return anomalyData;
  }

  @Override
  public List<PerspectiveAnomalyData> listPerspectiveAnomalies(@NonNull String accountIdentifier, PerspectiveQueryDTO perspectiveQuery) {
    return null;
  }

  private AnomalyData buildAnomalyData(Anomalies anomaly) {
    long anomalyTime = anomaly.getAnomalytime().toEpochSecond() * 1000;
    return AnomalyData.builder()
        .id(anomaly.getId())
        .time(anomalyTime)
        .anomalyRelativeTime(getAnomalyRelativeTime(anomalyTime))
        .actualAmount(anomaly.getActualcost())
        .expectedAmount(anomaly.getExpectedcost())
        .trend(getAnomalyTrend(anomaly.getActualcost(), anomaly.getExpectedcost()))
        .resourceName(getResourceName(anomaly))
        .resourceInfo(getResourceInfo(anomaly))
        .build();
  }

  private String getAnomalyRelativeTime(long anomalyTime) {
    return "";
  }

  private Double getAnomalyTrend(Double actualCost, Double expectedCost) {
    return Math.round(((actualCost - expectedCost) / expectedCost) * 100D) / 100D;
  }

  private String getResourceName(Anomalies anomaly) {
    return "";
  }

  private String getResourceInfo(Anomalies anomaly) {
    return "";
  }
}
