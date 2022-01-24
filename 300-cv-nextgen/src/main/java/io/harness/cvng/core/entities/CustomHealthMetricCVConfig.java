/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthDefinition;
import io.harness.cvng.core.beans.CustomHealthMetricDefinition;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "CustomHealthMetricCVConfigKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomHealthMetricCVConfig extends MetricCVConfig {
  String groupName;
  List<CustomHealthMetricDefinition> metricDefinitions;

  @Override
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CUSTOM_HEALTH_METRIC;
  }

  @Override
  protected void validateParams() {
    checkNotNull(groupName, generateErrorMessageFromParam(CustomHealthMetricCVConfigKeys.groupName));
    checkNotNull(metricDefinitions, generateErrorMessageFromParam(CustomHealthMetricCVConfigKeys.metricDefinitions));
    Set<String> uniqueMetricDefinitionsNames = new HashSet<>();

    for (int metricDefinitionIndex = 0; metricDefinitionIndex < metricDefinitions.size(); metricDefinitionIndex++) {
      CustomHealthMetricDefinition metricDefinition = metricDefinitions.get(metricDefinitionIndex);
      CustomHealthDefinition customHealthDefinition = metricDefinition.getHealthDefinition();

      checkNotNull(metricDefinition.getMetricName(),
          generateErrorMessageFromParam("metricName") + " for index " + metricDefinitionIndex);
      checkNotNull(customHealthDefinition.getMethod(),
          generateErrorMessageFromParam(CustomHealthDefinition.CustomHealthDefinitionKeys.method) + " for index "
              + metricDefinitionIndex);
      checkNotNull(customHealthDefinition.getUrlPath(),
          generateErrorMessageFromParam(CustomHealthDefinition.CustomHealthDefinitionKeys.urlPath) + " for index "
              + metricDefinitionIndex);

      AnalysisDTO analysisDTO = metricDefinition.getAnalysis();
      SLIDTO sliDTO = metricDefinition.getSli();

      switch (customHealthDefinition.getQueryType()) {
        case HOST_BASED:
          if ((analysisDTO != null && analysisDTO.getLiveMonitoring() != null
                  && analysisDTO.getLiveMonitoring().getEnabled() != null
                  && analysisDTO.getLiveMonitoring().getEnabled() == true)
              || (sliDTO != null && sliDTO.getEnabled() != null && sliDTO.getEnabled())) {
            throw new InvalidRequestException("Host based queries can only be used for continuous verification.");
          }
          break;
        case SERVICE_BASED:
          if (analysisDTO != null && analysisDTO.getDeploymentVerification() != null
              && analysisDTO.getDeploymentVerification().getEnabled() != null
              && analysisDTO.getDeploymentVerification().getEnabled()) {
            throw new InvalidRequestException(
                "Service based queries can only be used for live monitoring and service level indicators.");
          }
          break;
        default:
          throw new InvalidRequestException(
              String.format("Invalid query type %s provided, must be SERVICE_BASED or HOST_BASED",
                  customHealthDefinition.getQueryType()));
      }

      String uniqueKey = getMetricAndGroupNameKey(groupName, metricDefinition.getMetricName());
      if (uniqueMetricDefinitionsNames.contains(uniqueKey)) {
        throw new InvalidRequestException(
            String.format("Duplicate group name (%s) and metric name (%s) combination present.", groupName,
                metricDefinition.getMetricName()));
      }
      uniqueMetricDefinitionsNames.add(uniqueKey);
    }
  }

  public MetricPack generateMetricPack(String metricName, RiskProfile riskProfile) {
    Set<TimeSeriesThreshold> timeSeriesThresholds = getThresholdsToCreateOnSaveForCustomProviders(
        metricName, riskProfile.getMetricType(), riskProfile.getThresholdTypes());
    MetricPack metricPack = MetricPack.builder()
                                .category(riskProfile.getCategory())
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.CUSTOM_HEALTH_METRIC)
                                .projectIdentifier(getProjectIdentifier())
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .build();

    metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                .thresholds(new ArrayList<>(timeSeriesThresholds))
                                .type(riskProfile.getMetricType())
                                .name(metricName)
                                .included(true)
                                .build());

    return metricPack;
  }

  public static class CustomHealthCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<CustomHealthMetricCVConfig, CustomHealthMetricCVConfig> {
    @Override
    public void setUpdateOperations(UpdateOperations<CustomHealthMetricCVConfig> updateOperations,
        CustomHealthMetricCVConfig customHealthCVConfig) {
      setCommonOperations(updateOperations, customHealthCVConfig);
      updateOperations.set(CustomHealthMetricCVConfigKeys.groupName, customHealthCVConfig.getGroupName())
          .set(CustomHealthMetricCVConfigKeys.metricDefinitions, customHealthCVConfig.getMetricDefinitions());
    }
  }

  private String getMetricAndGroupNameKey(String groupName, String metricName) {
    return String.format("%s%s", groupName, metricName);
  }
}
