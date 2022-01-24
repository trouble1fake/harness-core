/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.CustomHealthDefinition;
import io.harness.cvng.core.beans.CustomHealthLogDefinition;
import io.harness.cvng.core.beans.CustomHealthSpecLogDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceLogSpec;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class CustomHealthSourceSpecLogTransformer
    implements CVConfigToHealthSourceTransformer<CustomHealthLogCVConfig, CustomHealthSourceLogSpec> {
  @Override
  public CustomHealthSourceLogSpec transformToHealthSourceConfig(List<CustomHealthLogCVConfig> cvConfigGroup) {
    Preconditions.checkNotNull(cvConfigGroup);

    CustomHealthSourceLogSpec logSpec = CustomHealthSourceLogSpec.builder()
                                            .connectorRef(cvConfigGroup.get(0).getConnectorIdentifier())
                                            .logDefinitions(new ArrayList<>())
                                            .build();

    cvConfigGroup.forEach(cvConfig -> {
      CustomHealthLogDefinition customHealthLogDefinition = cvConfig.getQueryDefinition();
      CustomHealthDefinition customHealthDefinition = customHealthLogDefinition.getCustomHealthDefinition();
      CustomHealthSpecLogDefinition specLogDefinition =
          CustomHealthSpecLogDefinition.builder()
              .customHealthDefinition(CustomHealthDefinition.builder()
                                          .endTimeInfo(customHealthDefinition.getEndTimeInfo())
                                          .startTimeInfo(customHealthDefinition.getStartTimeInfo())
                                          .method(customHealthDefinition.getMethod())
                                          .queryType(customHealthDefinition.getQueryType())
                                          .requestBody(customHealthDefinition.getRequestBody())
                                          .urlPath(customHealthDefinition.getUrlPath())
                                          .build())
              .timestampFormat(customHealthLogDefinition.getTimestampFormat())
              .queryValueJsonPath(customHealthLogDefinition.getQueryValueJsonPath())
              .timestampJsonPath(customHealthLogDefinition.getTimestampJsonPath())
              .serviceInstanceJsonPath(customHealthLogDefinition.getServiceInstanceJsonPath())
              .groupName(cvConfig.getGroupName())
              .queryName(cvConfig.getQueryName())
              .build();
      logSpec.getLogDefinitions().add(specLogDefinition);
    });

    return logSpec;
  }
}
