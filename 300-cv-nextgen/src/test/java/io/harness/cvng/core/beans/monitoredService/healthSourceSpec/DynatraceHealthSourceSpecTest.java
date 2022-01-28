package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.beans.monitoredService.MetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.DynatraceHealthSourceSpec;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DynatraceCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class DynatraceHealthSourceSpecTest extends CvNextGenTestBase {
  @Inject MetricPackService metricPackService;
  private DynatraceHealthSourceSpec classUnderTest;
  private ProjectParams mockedProjectParams;
  @Captor private ArgumentCaptor<OnboardingRequestDTO> requestCaptor;

  private static final BuilderFactory builderFactory = BuilderFactory.builder().build();
  private static final String CONNECTOR_IDENTIFIER = generateUuid();
  private static final String HEALTH_SOURCE_SERVICE_IDENTIFIER = builderFactory.getContext().getServiceIdentifier();
  private static final String DYNATRACE_ENTITY_SERVICE_ID = "service_id_mock";
  private static final String FEATURE = "dynatrace_apm";
  private static final String IDENTIFIER = "identifier";
  private static final String HEALTH_SOURCE_NAME = "some-name";
  private static final String ENV_IDENTIFIER = builderFactory.getContext().getEnvIdentifier();
  private static final String MONITORED_SERVICE_IDENTIFIER = "mock_monitored_service_identifier";

  private static DynatraceCVConfig apply(CVConfig cvConfig) {
    return (DynatraceCVConfig) cvConfig;
  }

  @Before
  public void setup() {
    classUnderTest = DynatraceHealthSourceSpec.builder().connectorRef(CONNECTOR_IDENTIFIER).build();
    mockedProjectParams = ProjectParams.builder()
                              .accountIdentifier(builderFactory.getContext().getAccountId())
                              .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                              .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                              .build();
    MockitoAnnotations.initMocks(this);
    metricPackService.createDefaultMetricPackAndThresholds(mockedProjectParams.getAccountIdentifier(),
        mockedProjectParams.getOrgIdentifier(), mockedProjectParams.getProjectIdentifier());
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResultWhenDeleted() {
    // existing cvConfigs
    List<CVConfig> cvConfigs = new ArrayList<>();
    cvConfigs.add(createCVConfig(MetricPack.builder()
                                     .accountId(mockedProjectParams.getAccountIdentifier())
                                     .category(CVMonitoringCategory.PERFORMANCE)
                                     .build()));
    CVConfigUpdateResult result = classUnderTest.getCVConfigUpdateResult(mockedProjectParams.getAccountIdentifier(),
        mockedProjectParams.getOrgIdentifier(), mockedProjectParams.getProjectIdentifier(), ENV_IDENTIFIER,
        HEALTH_SOURCE_SERVICE_IDENTIFIER, MONITORED_SERVICE_IDENTIFIER, IDENTIFIER, HEALTH_SOURCE_NAME, cvConfigs,
        metricPackService);
    assertThat(result.getDeleted()).hasSize(1);
    DynatraceCVConfig dynatraceCVConfig = (DynatraceCVConfig) result.getDeleted().get(0);
    assertThat(dynatraceCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResultWhenAdded() {
    // provide mock data to spec
    classUnderTest =
        DynatraceHealthSourceSpec.builder()
            .connectorRef(CONNECTOR_IDENTIFIER)
            .feature(FEATURE)
            .serviceId(DYNATRACE_ENTITY_SERVICE_ID)
            .metricPacks(Collections.singleton(
                MetricPackDTO.builder().identifier(CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER).build()))
            .build();
    List<CVConfig> cvConfigs = new ArrayList<>();
    CVConfigUpdateResult result = classUnderTest.getCVConfigUpdateResult(mockedProjectParams.getAccountIdentifier(),
        mockedProjectParams.getOrgIdentifier(), mockedProjectParams.getProjectIdentifier(), ENV_IDENTIFIER,
        HEALTH_SOURCE_SERVICE_IDENTIFIER, MONITORED_SERVICE_IDENTIFIER, IDENTIFIER, HEALTH_SOURCE_NAME, cvConfigs,
        metricPackService);
    // one metric pack should be mapped into one CV config
    assertThat(result.getAdded()).hasSize(1);
    result.getAdded().stream().map(DynatraceHealthSourceSpecTest::apply).forEach(this::assertCommon);
    assertThat(result.getAdded().stream().map(DynatraceHealthSourceSpecTest::apply).count()).isEqualTo(1);
    DynatraceCVConfig dynamicsCVConfig = (DynatraceCVConfig) result.getAdded().get(0);
    assertThat(dynamicsCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResultWhenUpdated() {
    MetricPack mockMetricPack = metricPackService.getMetricPack(mockedProjectParams.getAccountIdentifier(),
        mockedProjectParams.getOrgIdentifier(), mockedProjectParams.getProjectIdentifier(), DataSourceType.DYNATRACE,
        CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER);
    // provide mock data to spec
    classUnderTest = DynatraceHealthSourceSpec.builder()
                         .connectorRef(CONNECTOR_IDENTIFIER)
                         .feature(FEATURE)
                         .serviceId(DYNATRACE_ENTITY_SERVICE_ID)
                         .metricPacks(Collections.singleton(MetricPackDTO.toMetricPackDTO(mockMetricPack)))
                         .build();
    List<CVConfig> cvConfigs = new ArrayList<>();
    CVConfig cvConfigToUpdate = createCVConfig(mockMetricPack);
    cvConfigs.add(cvConfigToUpdate);
    CVConfigUpdateResult result = classUnderTest.getCVConfigUpdateResult(mockedProjectParams.getAccountIdentifier(),
        mockedProjectParams.getOrgIdentifier(), mockedProjectParams.getProjectIdentifier(), ENV_IDENTIFIER,
        HEALTH_SOURCE_SERVICE_IDENTIFIER, MONITORED_SERVICE_IDENTIFIER, IDENTIFIER, HEALTH_SOURCE_NAME, cvConfigs,
        metricPackService);
    assertThat(result.getUpdated()).hasSize(1);
    DynatraceCVConfig dynatraceCVConfig = (DynatraceCVConfig) result.getUpdated().get(0);
    assertCommon(dynatraceCVConfig);
    assertThat(dynatraceCVConfig.getMetricPack().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
  }

  private void assertCommon(DynatraceCVConfig cvConfig) {
    assertThat(cvConfig.getAccountId()).isEqualTo(mockedProjectParams.getAccountIdentifier());
    assertThat(cvConfig.getOrgIdentifier()).isEqualTo(mockedProjectParams.getOrgIdentifier());
    assertThat(cvConfig.getProjectIdentifier()).isEqualTo(mockedProjectParams.getProjectIdentifier());
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(MONITORED_SERVICE_IDENTIFIER);
    assertThat(cvConfig.getConnectorIdentifier()).isEqualTo(CONNECTOR_IDENTIFIER);
    assertThat(cvConfig.getEnvIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(cvConfig.getFullyQualifiedIdentifier()).isEqualTo(IDENTIFIER);
    assertThat(cvConfig.getProductName()).isEqualTo(FEATURE);
    assertThat(cvConfig.getMonitoringSourceName()).isEqualTo(HEALTH_SOURCE_NAME);
    assertThat(cvConfig.getDynatraceServiceId()).isEqualTo(DYNATRACE_ENTITY_SERVICE_ID);
    assertThat(cvConfig.getMetricPack().getAccountId()).isEqualTo(mockedProjectParams.getAccountIdentifier());
    assertThat(cvConfig.getMetricPack().getOrgIdentifier()).isEqualTo(mockedProjectParams.getOrgIdentifier());
    assertThat(cvConfig.getMetricPack().getDataSourceType()).isEqualTo(DataSourceType.DYNATRACE);
  }

  private CVConfig createCVConfig(MetricPack metricPack) {
    return builderFactory.dynatraceCVConfigBuilder()
        .metricPack(metricPack)
        .connectorIdentifier(CONNECTOR_IDENTIFIER)
        .monitoringSourceName(HEALTH_SOURCE_NAME)
        .productName(FEATURE)
        .identifier(IDENTIFIER)
        .category(metricPack.getCategory())
        .build();
  }
}
