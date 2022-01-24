/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthDefinition;
import io.harness.cvng.core.beans.CustomHealthMetricDefinition;
import io.harness.cvng.core.beans.CustomHealthSpecMetricDefinition;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceMetricSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthSourceSpecTest extends CvNextGenTestBase {
  List<CustomHealthSpecMetricDefinition> customHealthSourceSpecs;
  CustomHealthSourceMetricSpec customHealthSourceSpec;
  String groupName = "group_1";
  String metricName = "metric_1";
  String metricValueJSONPath = "json.path.to.metricValue";
  String identifier = "1234_identifier";
  String accountId = "1234_accountId";
  String orgIdentifier = "1234_orgIdentifier";
  String projectIdentifier = "1234_projectIdentifier";
  String environmentRef = "1234_envRef";
  String serviceRef = "1234_serviceRef";
  String name = "customhealthsource";
  String monitoredServiceIdentifier = generateUuid();
  MetricResponseMapping responseMapping;
  @Inject MetricPackService metricPackService;

  @Before
  public void setup() {
    responseMapping = MetricResponseMapping.builder().metricValueJsonPath(metricValueJSONPath).build();

    CustomHealthDefinition customHealthMetricDefinition = CustomHealthDefinition.builder()
                                                              .queryType(HealthSourceQueryType.HOST_BASED)
                                                              .method(CustomHealthMethod.GET)
                                                              .build();

    CustomHealthSpecMetricDefinition metricDefinition =
        CustomHealthSpecMetricDefinition.builder()
            .groupName(groupName)
            .healthDefinition(customHealthMetricDefinition)
            .metricName(metricName)
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .metricResponseMapping(responseMapping)
            .identifier(identifier)
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build())
            .build();

    customHealthSourceSpecs = new ArrayList<>();
    customHealthSourceSpecs.add(metricDefinition);
    customHealthSourceSpec = CustomHealthSourceMetricSpec.builder().metricDefinitions(customHealthSourceSpecs).build();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forCreate() {
    CustomHealthMetricDefinition metricDefinition3 =
        CustomHealthMetricDefinition.builder()
            .metricName("metric_3")
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.POST)
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .build())
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.ERRORS).build())
            .build();

    CustomHealthMetricDefinition metricDefinition =
        CustomHealthMetricDefinition.builder()
            .metricName(metricName)
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.GET)
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .build())
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build())
            .build();

    CustomHealthMetricCVConfig existingCVConfig = CustomHealthMetricCVConfig.builder()
                                                      .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
                                                        { add(metricDefinition3); }
                                                      })
                                                      .groupName("group")
                                                      .build();

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    MetricPack.MetricDefinition metricDefinition1 =
        MetricPack.MetricDefinition.builder().name("metric1").thresholds(new ArrayList<>()).included(true).build();
    List<CustomHealthMetricCVConfig> addedConfigs = new ArrayList<>();
    HashSet<MetricPack.MetricDefinition> hashSet = new HashSet<>();
    hashSet.add(metricDefinition1);

    addedConfigs.add(CustomHealthMetricCVConfig.builder()
                         .groupName(groupName)
                         .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
                           { add(metricDefinition); }
                         })
                         .metricPack(MetricPack.builder()
                                         .category(CVMonitoringCategory.PERFORMANCE)
                                         .dataSourceType(DataSourceType.CUSTOM_HEALTH_METRIC)
                                         .accountId(accountId)
                                         .projectIdentifier(projectIdentifier)
                                         .identifier("Performance")
                                         .metrics(hashSet)
                                         .build())
                         .build());

    assertThat(((CustomHealthMetricCVConfig) result.getAdded().get(0)).getMetricDefinitions())
        .isEqualTo(addedConfigs.get(0).getMetricDefinitions());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forDelete() {
    CustomHealthMetricDefinition metricDefinition2 =
        CustomHealthMetricDefinition.builder()
            .metricName("metric_2")
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.POST)
                                  .queryType(HealthSourceQueryType.SERVICE_BASED)
                                  .build())
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.ERRORS).build())
            .build();

    CustomHealthMetricCVConfig existingCVConfig = CustomHealthMetricCVConfig.builder()
                                                      .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
                                                        { add(metricDefinition2); }
                                                      })
                                                      .groupName(groupName)
                                                      .category(CVMonitoringCategory.ERRORS)
                                                      .build();

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    List<CustomHealthMetricCVConfig> deletedConfigs = new ArrayList<>();
    deletedConfigs.add(CustomHealthMetricCVConfig.builder()
                           .groupName(groupName)
                           .category(CVMonitoringCategory.ERRORS)
                           .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
                             { add(metricDefinition2); }
                           })
                           .build());

    assertThat(result.getDeleted()).isEqualTo(deletedConfigs);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forUpdate() {
    CustomHealthMetricDefinition metricDefinition =
        CustomHealthMetricDefinition.builder()
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.GET)
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .build())
            .metricName(metricName)
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .identifier("9876_identifier")
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build())
            .build();

    CustomHealthMetricDefinition updatedMetricDefinition =
        CustomHealthMetricDefinition.builder()
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.POST)
                                  .queryType(HealthSourceQueryType.SERVICE_BASED)
                                  .requestBody("post body")
                                  .build())
            .metricName(metricName)
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build())
            .build();

    CustomHealthMetricDefinition customHealthMetricDefinition = customHealthSourceSpec.getMetricDefinitions().get(0);
    customHealthMetricDefinition.getHealthDefinition().setQueryType(HealthSourceQueryType.SERVICE_BASED);
    customHealthMetricDefinition.getHealthDefinition().setRequestBody("post body");
    customHealthMetricDefinition.getHealthDefinition().setMethod(CustomHealthMethod.POST);

    CustomHealthMetricCVConfig existingCVConfig = CustomHealthMetricCVConfig.builder()
                                                      .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
                                                        { add(metricDefinition); }
                                                      })
                                                      .groupName(groupName)
                                                      .category(CVMonitoringCategory.PERFORMANCE)
                                                      .build();

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    List<CustomHealthMetricCVConfig> updatedConfigs = new ArrayList<>();
    updatedConfigs.add(CustomHealthMetricCVConfig.builder()
                           .groupName(groupName)
                           .category(CVMonitoringCategory.PERFORMANCE)
                           .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
                             { add(updatedMetricDefinition); }
                           })
                           .build());

    assertThat(((CustomHealthMetricCVConfig) result.getUpdated().get(0)).getMetricDefinitions())
        .isEqualTo(updatedConfigs.get(0).getMetricDefinitions());
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigs() {
    CustomHealthSpecMetricDefinition customHealthMetricDefinition =
        CustomHealthSpecMetricDefinition.builder()
            .healthDefinition(CustomHealthDefinition.builder()
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .method(CustomHealthMethod.GET)
                                  .build())
            .metricName("metric_2")
            .metricResponseMapping(responseMapping)
            .identifier("2132_identifier")
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build())
            .build();

    CustomHealthSpecMetricDefinition customHealthMetricDefinition2 =
        CustomHealthSpecMetricDefinition.builder()
            .healthDefinition(CustomHealthDefinition.builder()
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .method(CustomHealthMethod.POST)
                                  .build())

            .metricName("metric_3")
            .metricResponseMapping(responseMapping)
            .identifier("43534_identifier")
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build())
            .build();

    CustomHealthMetricDefinition metricDefinition =
        CustomHealthMetricDefinition.builder()
            .metricName(metricName)
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.GET)
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .build())
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.PERFORMANCE).build())
            .build();

    CustomHealthMetricDefinition metricDefinition2 =
        CustomHealthMetricDefinition.builder()
            .metricName("metric_2")
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.GET)
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .build())
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build())
            .build();

    CustomHealthMetricDefinition metricDefinition3 =
        CustomHealthMetricDefinition.builder()
            .metricName("metric_3")
            .riskProfile(RiskProfile.builder().build())
            .metricResponseMapping(responseMapping)
            .healthDefinition(CustomHealthDefinition.builder()
                                  .method(CustomHealthMethod.POST)
                                  .queryType(HealthSourceQueryType.HOST_BASED)
                                  .build())
            .analysis(HealthSourceMetricDefinition.AnalysisDTO.builder().build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build())
            .build();

    customHealthSourceSpec.getMetricDefinitions().add(customHealthMetricDefinition);
    customHealthSourceSpec.getMetricDefinitions().add(customHealthMetricDefinition2);

    CustomHealthMetricCVConfig singleMetricDefinition =
        CustomHealthMetricCVConfig.builder()
            .groupName(groupName)
            .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
              { add(metricDefinition); }
            })
            .serviceIdentifier(serviceRef)
            .envIdentifier(environmentRef)
            .orgIdentifier(orgIdentifier)
            .category(CVMonitoringCategory.INFRASTRUCTURE)
            .enabled(false)
            .isDemo(false)
            .projectIdentifier(projectIdentifier)
            .identifier(identifier)
            .monitoringSourceName("customhealthSource")
            .build();
    CustomHealthMetricCVConfig multipleMetricDefinitions =
        CustomHealthMetricCVConfig.builder()
            .groupName(groupName)
            .metricDefinitions(new ArrayList<CustomHealthMetricDefinition>() {
              {
                add(metricDefinition2);
                add(metricDefinition3);
              }
            })
            .serviceIdentifier(serviceRef)
            .envIdentifier(environmentRef)
            .orgIdentifier(orgIdentifier)
            .category(CVMonitoringCategory.INFRASTRUCTURE)
            .enabled(false)
            .isDemo(false)
            .projectIdentifier(projectIdentifier)
            .identifier(identifier)
            .monitoringSourceName("customhealthSource")
            .build();
    List<CustomHealthMetricCVConfig> configs =
        customHealthSourceSpec
            .getCVConfigs(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef, identifier, name)
            .values()
            .stream()
            .collect(Collectors.toList());
    if (configs.get(0).getMetricDefinitions().size() == 2) {
      assertThat(configs.get(0).getMetricDefinitions()).isEqualTo(multipleMetricDefinitions.getMetricDefinitions());
      assertThat(configs.get(1).getMetricDefinitions()).isEqualTo(singleMetricDefinition.getMetricDefinitions());
    } else {
      assertThat(configs.get(0).getMetricDefinitions()).isEqualTo(singleMetricDefinition.getMetricDefinitions());
      assertThat(configs.get(1).getMetricDefinitions()).isEqualTo(multipleMetricDefinitions.getMetricDefinitions());
    }
  }
}
