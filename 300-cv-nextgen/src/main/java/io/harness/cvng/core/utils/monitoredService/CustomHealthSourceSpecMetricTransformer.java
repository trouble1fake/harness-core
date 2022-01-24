/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.CustomHealthDefinition;
import io.harness.cvng.core.beans.CustomHealthSpecMetricDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceMetricSpec;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class CustomHealthSourceSpecMetricTransformer
    implements CVConfigToHealthSourceTransformer<CustomHealthMetricCVConfig, CustomHealthSourceMetricSpec> {
  @Override
  public CustomHealthSourceMetricSpec transformToHealthSourceConfig(List<CustomHealthMetricCVConfig> cvConfigGroup) {
    Preconditions.checkNotNull(cvConfigGroup);
    CustomHealthSourceMetricSpec customHealthSourceSpec =
        CustomHealthSourceMetricSpec.builder()
            .connectorRef(cvConfigGroup.get(0).getConnectorIdentifier())
            .metricDefinitions(new ArrayList<>())
            .build();

    cvConfigGroup.forEach(customHealthCVConfig -> {
      customHealthCVConfig.getMetricDefinitions().forEach(definition -> {
        CustomHealthDefinition baseDefinition = definition.getHealthDefinition();
        CustomHealthSpecMetricDefinition customHealthMetricDefinition =
            CustomHealthSpecMetricDefinition.builder()
                .groupName(customHealthCVConfig.getGroupName())
                .healthDefinition(CustomHealthDefinition.builder()
                                      .urlPath(baseDefinition.getUrlPath())
                                      .queryType(baseDefinition.getQueryType())
                                      .method(baseDefinition.getMethod())
                                      .requestBody(baseDefinition.getRequestBody())
                                      .startTimeInfo(baseDefinition.getStartTimeInfo())
                                      .endTimeInfo(baseDefinition.getEndTimeInfo())
                                      .build())
                .metricName(definition.getMetricName())
                .metricResponseMapping(definition.getMetricResponseMapping())
                .metricName(definition.getMetricName())
                .analysis(definition.getAnalysis())
                .identifier(definition.getIdentifier())
                .riskProfile(definition.getRiskProfile())
                .build();
        customHealthSourceSpec.getMetricDefinitions().add(customHealthMetricDefinition);
      });
    });
    return customHealthSourceSpec;
  }
}
