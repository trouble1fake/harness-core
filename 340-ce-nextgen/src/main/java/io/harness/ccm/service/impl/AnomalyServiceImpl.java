/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.constants.AnomalyFieldConstants;
import io.harness.ccm.commons.dao.anomaly.AnomalyDao;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedbackDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveQueryDTO;
import io.harness.ccm.service.intf.AnomalyService;
import io.harness.timescaledb.tables.pojos.Anomalies;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
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
  private static final String ANOMALY_RELATIVE_TIME_TEMPLATE = "since %s %s";
  private static final String STATUS_RELATIVE_TIME_TEMPLATE = "%s %s ago";
  private static final String SEPARATOR = "/";

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

    log.info("Anomalies: {}", anomalies);

    List<AnomalyData> anomalyData = new ArrayList<>();
    anomalies.forEach(anomaly -> anomalyData.add(buildAnomalyData(anomaly)));
    return anomalyData;
  }

  @Override
  public List<PerspectiveAnomalyData> listPerspectiveAnomalies(
      @NonNull String accountIdentifier, PerspectiveQueryDTO perspectiveQuery) {
    // Todo: Add perspective query to anomaly query mapping
    return Collections.singletonList(buildDummyPerspectiveAnomalyData());
  }

  @Override
  public Boolean updateAnomalyFeedback(
      @NonNull String accountIdentifier, String anomalyId, AnomalyFeedbackDTO feedback) {
    // Todo: Add Update Query
    return true;
  }

  private AnomalyData buildAnomalyData(Anomalies anomaly) {
    long anomalyTime = anomaly.getAnomalytime().toEpochSecond() * 1000;
    return AnomalyData.builder()
        .id(anomaly.getId())
        .time(anomalyTime)
        .anomalyRelativeTime(getRelativeTime(anomalyTime, ANOMALY_RELATIVE_TIME_TEMPLATE))
        .actualAmount(anomaly.getActualcost())
        .expectedAmount(anomaly.getExpectedcost())
        .trend(getAnomalyTrend(anomaly.getActualcost(), anomaly.getExpectedcost()))
        .resourceName(getResourceName(anomaly))
        .resourceInfo(getResourceInfo(anomaly))
        // Todo : Remove default assignment when status column is added to anomaly table
        .status("Open")
        .statusRelativeTime(getRelativeTime(anomalyTime, STATUS_RELATIVE_TIME_TEMPLATE))
        .cloudProvider(getCloudProvider(anomaly))
        .build();
  }

  private PerspectiveAnomalyData buildDummyPerspectiveAnomalyData() {
    long anomalyTime = BudgetUtils.getStartOfCurrentDay() - 3 * BudgetUtils.ONE_DAY_MILLIS;
    return PerspectiveAnomalyData.builder()
        .timestamp(anomalyTime)
        .anomalyCount(1)
        .actualCost(137.22)
        .differenceFromExpectedCost(120.21)
        .associatedResources(Collections.singletonList("sample-ce-dev"))
        .resourceType("cluster")
        .build();
  }

  private String getRelativeTime(long anomalyTime, String template) {
    long currentTime = System.currentTimeMillis();
    long timeDiff = currentTime - anomalyTime;
    long days = timeDiff / TimeUtils.ONE_DAY_MILLIS;
    if (days != 0) {
      return days > 1 ? String.format(template, days, "days") : String.format(template, 1, "day");
    }
    long hours = timeDiff / TimeUtils.ONE_HOUR_MILLIS;
    if (hours != 0) {
      return hours > 1 ? String.format(template, days, "hours") : String.format(template, 1, "hour");
    }
    long minutes = timeDiff / TimeUtils.ONE_MINUTE_MILLIS;
    return minutes > 1 ? String.format(template, minutes, "minutes") : String.format(template, 1, "minute");
  }

  private Double getAnomalyTrend(Double actualCost, Double expectedCost) {
    return expectedCost != 0 ? Math.round(((actualCost - expectedCost) / expectedCost) * 100D) / 100D : 0;
  }

  private String getResourceName(Anomalies anomaly) {
    StringBuilder builder = new StringBuilder();
    if (anomaly.getClustername() != null) {
      builder.append(anomaly.getClustername());
      if (anomaly.getNamespace() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getNamespace());
      }
      if (anomaly.getWorkloadname() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getWorkloadname());
      }
      return builder.toString();
    }
    return "";
  }

  private String getResourceInfo(Anomalies anomaly) {
    StringBuilder builder = new StringBuilder();
    if (anomaly.getClustername() != null) {
      builder.append(AnomalyFieldConstants.CLUSTER);
      if (anomaly.getNamespace() != null) {
        builder.append(SEPARATOR);
        builder.append(AnomalyFieldConstants.NAMESPACE);
      }
      if (anomaly.getWorkloadname() != null) {
        builder.append(SEPARATOR);
        builder.append(AnomalyFieldConstants.WORKLOAD);
      }
      return builder.toString();
    }

    return "";
  }

  private String getCloudProvider(Anomalies anomaly) {
    if (anomaly.getClustername() != null) {
      return AnomalyFieldConstants.CLUSTER.toUpperCase();
    } else if (anomaly.getAwsaccount() != null) {
      return AnomalyFieldConstants.AWS;
    } else if (anomaly.getGcpproject() != null) {
      return AnomalyFieldConstants.GCP;
    }
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
