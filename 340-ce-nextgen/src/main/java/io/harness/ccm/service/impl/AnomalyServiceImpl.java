/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.ccm.commons.dao.anomaly.AnomalyDao;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedback;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveQueryDTO;
import io.harness.ccm.service.intf.AnomalyService;
import io.harness.timescaledb.tables.pojos.Anomalies;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.impl.DSL;

@Slf4j
public class AnomalyServiceImpl implements AnomalyService {
  @Inject AnomalyDao anomalyDao;
  @Inject AnomalyQueryBuilder anomalyQueryBuilder;

  private static final Integer DEFAULT_LIMIT = 100;
  private static final Integer DEFAULT_OFFSET = 0;

  @Override
  public List<AnomalyData> listAnomalies(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery) {
    if (anomalyQuery == null) {
      anomalyQuery = getDefaultAnomalyQuery();
    }
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
  public List<PerspectiveAnomalyData> listPerspectiveAnomalies(
      @NonNull String accountIdentifier, PerspectiveQueryDTO perspectiveQuery) {
    // Todo: Add perspective query to anomaly query mapping
    return null;
  }

  @Override
  public Boolean updateAnomalyFeedback(@NonNull String accountIdentifier, String anomalyId, AnomalyFeedback feedback) {
    // Todo: Add Update Query
    return true;
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

  private AnomalyQueryDTO getDefaultAnomalyQuery() {
    return AnomalyQueryDTO.builder()
        .filter(null)
        .groupBy(new ArrayList<>())
        .orderBy(new ArrayList<>())
        .limit(DEFAULT_LIMIT)
        .offset(DEFAULT_OFFSET)
        .build();
  }
}
