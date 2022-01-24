/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthDefinition;
import io.harness.cvng.core.beans.CustomHealthLogDefinition;
import io.harness.cvng.core.beans.CustomHealthSpecLogDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.validators.UniqueIdentifierCheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomHealthSourceLogSpec extends HealthSourceSpec {
  @UniqueIdentifierCheck List<CustomHealthSpecLogDefinition> logDefinitions = new ArrayList<>();

  @Data
  @Builder
  public static class Key {
    String groupName;
    String query;
  }

  @Override
  public HealthSource.CVConfigUpdateResult getCVConfigUpdateResult(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String identifier, String name,
      List<CVConfig> existingCVConfigs, MetricPackService metricPackService) {
    List<CustomHealthLogCVConfig> existingDBCVConfigs = (List<CustomHealthLogCVConfig>) (List<?>) existingCVConfigs;
    Map<Key, CustomHealthLogCVConfig> existingConfigs = new HashMap<>();
    existingDBCVConfigs.forEach(config
        -> existingConfigs.put(
            Key.builder().groupName(config.getGroupName()).query(config.getQuery()).build(), config));

    Map<Key, CustomHealthLogCVConfig> currentCVConfigs =
        getCVConfigs(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef, identifier, name);

    Set<Key> deleted = Sets.difference(existingConfigs.keySet(), currentCVConfigs.keySet());
    Set<Key> added = Sets.difference(currentCVConfigs.keySet(), existingConfigs.keySet());
    Set<Key> updated = Sets.intersection(existingConfigs.keySet(), currentCVConfigs.keySet());

    List<CVConfig> updatedConfigs = updated.stream().map(currentCVConfigs::get).collect(Collectors.toList());
    List<CVConfig> updatedConfigWithUuid = updated.stream().map(existingConfigs::get).collect(Collectors.toList());
    for (int i = 0; i < updatedConfigs.size(); i++) {
      updatedConfigs.get(i).setUuid(updatedConfigWithUuid.get(i).getUuid());
    }
    return HealthSource.CVConfigUpdateResult.builder()
        .deleted(deleted.stream().map(existingConfigs::get).collect(Collectors.toList()))
        .updated(updatedConfigs)
        .added(added.stream().map(currentCVConfigs::get).collect(Collectors.toList()))
        .build();
  }

  public Map<Key, CustomHealthLogCVConfig> getCVConfigs(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentRef, String serviceRef, String identifier, String name) {
    Map<Key, CustomHealthLogCVConfig> cvConfigMap = new HashMap<>();
    logDefinitions.forEach(logDefinition -> {
      String groupName = logDefinition.getGroupName();
      String query = logDefinition.getCustomHealthDefinition().getRequestBody();

      Key cvConfigKey = Key.builder().query(query).groupName(groupName).build();
      CustomHealthLogCVConfig existingCvConfig = cvConfigMap.get(cvConfigKey);

      if (existingCvConfig != null) {
        return;
      }

      CustomHealthLogCVConfig mappedCVConfig =
          CustomHealthLogCVConfig.builder()
              .accountId(accountId)
              .orgIdentifier(orgIdentifier)
              .projectIdentifier(projectIdentifier)
              .envIdentifier(environmentRef)
              .serviceIdentifier(serviceRef)
              .monitoringSourceName(name)
              .identifier(identifier)
              .queryDefinition(CustomHealthLogDefinition.builder()
                                   .customHealthDefinition(logDefinition.getCustomHealthDefinition())
                                   .queryValueJsonPath(logDefinition.getQueryValueJsonPath())
                                   .timestampFormat(logDefinition.getTimestampFormat())
                                   .timestampJsonPath(logDefinition.getTimestampJsonPath())
                                   .serviceInstanceJsonPath(logDefinition.getServiceInstanceJsonPath())
                                   .build())
              .build();

      cvConfigMap.put(cvConfigKey, mappedCVConfig);
    });

    return cvConfigMap;
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.CUSTOM_HEALTH_LOG;
  }
}
