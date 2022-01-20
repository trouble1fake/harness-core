package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.impl.DynatraceServiceImpl;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DynatraceServiceImplTest extends CategoryTest {

  private static final String PERFORMANCE_IDENTIFIER = "Performance";

  @Mock private OnboardingService mockedOnboardingService;
  @InjectMocks private DynatraceService classUnderTest = new DynatraceServiceImpl();
  private ProjectParams mockedProjectParams;
  private String connectorIdentifier;
  @Captor private ArgumentCaptor<OnboardingRequestDTO> requestCaptor;

  private static final List<DynatraceServiceDTO> mockedServices =
      Arrays.asList(createDynatraceServiceResponse("Service 1", "service_1_entity_id"),
          createDynatraceServiceResponse("Service 2", "service_2_entity_id"),
          createDynatraceServiceResponse("Service 3", "service_3_entity_id"),
          createDynatraceServiceResponse("Service 4", "service_4_entity_id"));

  @Before
  public void setup() {
    mockedProjectParams = ProjectParams.builder()
                              .accountIdentifier(generateUuid())
                              .orgIdentifier(generateUuid())
                              .projectIdentifier(generateUuid())
                              .build();
    connectorIdentifier = generateUuid();
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetAllDynatraceServices() {
    when(mockedOnboardingService.getOnboardingResponse(eq(mockedProjectParams.getAccountIdentifier()), any()))
        .thenReturn(OnboardingResponseDTO.builder().result(mockedServices).build());
    List<DynatraceServiceDTO> dynatraceServices =
        classUnderTest.getAllServices(mockedProjectParams, connectorIdentifier, "", generateUuid());

    OnboardingRequestDTO onboardingRequestDTO =
        assertOnboardingRequest(mockedOnboardingService, mockedProjectParams, connectorIdentifier, requestCaptor);

    DataCollectionRequest<DynatraceConnectorDTO> request = onboardingRequestDTO.getDataCollectionRequest();
    assertThat(request.getType().name()).isEqualTo(DataCollectionRequestType.DYNATRACE_SERVICE_LIST.name());
    assertThat(dynatraceServices).isNotNull();
    assertThat(dynatraceServices.size()).isEqualTo(mockedServices.size());
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetServiceDetails() throws Exception {
    when(mockedOnboardingService.getOnboardingResponse(eq(mockedProjectParams.getAccountIdentifier()), any()))
        .thenReturn(OnboardingResponseDTO.builder()
                        .result(createDynatraceServiceResponse("Service 1", "service_1_id"))
                        .build());
    DynatraceServiceDTO dynatraceServiceDetails = classUnderTest.getServiceDetails(
        mockedProjectParams, connectorIdentifier, "dynatrace_service_id", generateUuid());

    OnboardingRequestDTO onboardingRequestDTO =
        assertOnboardingRequest(mockedOnboardingService, mockedProjectParams, connectorIdentifier, requestCaptor);

    DataCollectionRequest<DynatraceConnectorDTO> request = onboardingRequestDTO.getDataCollectionRequest();
    assertThat(request.getType().name()).isEqualTo(DataCollectionRequestType.DYNATRACE_SERVICE_DETAILS.name());
    assertThat(dynatraceServiceDetails).isNotNull();
    assertThat(dynatraceServiceDetails.getEntityId()).isEqualTo("service_1_id");
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testValidateMetricPacksData() throws Exception {
    String textLoad = Resources.toString(
        DynatraceServiceImplTest.class.getResource("/timeseries/dynatrace_metric_data_validation_mock.json"),
        Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    when(mockedOnboardingService.getOnboardingResponse(anyString(), any(OnboardingRequestDTO.class)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    Set<MetricPackValidationResponse> metricPackData =
        classUnderTest.validateData(mockedProjectParams, connectorIdentifier, "service_mock_id",
            Collections.singletonList(MetricPackDTO.builder().identifier(PERFORMANCE_IDENTIFIER).build()), generateUuid());

    // verify performance pack
    MetricPackValidationResponse performanceValidationResponse =
        metricPackData.stream()
            .filter(validationResponse -> validationResponse.getMetricPackName().equals(PERFORMANCE_IDENTIFIER))
            .findFirst()
            .orElse(null);
    assertThat(performanceValidationResponse).isNotNull();
    assertThat(performanceValidationResponse.getOverallStatus()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS);
    List<MetricPackValidationResponse.MetricValidationResponse> metricValueValidationResponses =
            performanceValidationResponse.getMetricValidationResponses();
    assertThat(metricValueValidationResponses.size()).isEqualTo(2);
    assertThat(metricValueValidationResponses.get(0))
        .isEqualTo(MetricPackValidationResponse.MetricValidationResponse.builder()
                       .metricName("Method Response time")
                       .status(ThirdPartyApiResponseStatus.SUCCESS)
                       .value(181.0)
                       .build());
    assertThat(metricValueValidationResponses.get(1))
            .isEqualTo(MetricPackValidationResponse.MetricValidationResponse.builder()
                    .metricName("Name of server side errors")
                    .status(ThirdPartyApiResponseStatus.SUCCESS)
                    .value(52.0)
                    .build());
  }

  private static OnboardingRequestDTO assertOnboardingRequest(OnboardingService mockService,
      ProjectParams mockedProjectParams, String connectorIdentifier,
      ArgumentCaptor<OnboardingRequestDTO> requestCaptor) {
    verify(mockService).getOnboardingResponse(eq(mockedProjectParams.getAccountIdentifier()), requestCaptor.capture());
    OnboardingRequestDTO onboardingRequestDTO = requestCaptor.getValue();
    assertThat(onboardingRequestDTO.getOrgIdentifier()).isEqualTo(mockedProjectParams.getOrgIdentifier());
    assertThat(onboardingRequestDTO.getConnectorIdentifier()).isEqualTo(connectorIdentifier);
    assertThat(onboardingRequestDTO.getAccountId()).isEqualTo(mockedProjectParams.getAccountIdentifier());
    assertThat(onboardingRequestDTO.getProjectIdentifier()).isEqualTo(mockedProjectParams.getProjectIdentifier());
    assertThat(onboardingRequestDTO.getDataCollectionRequest()).isNotNull();
    return onboardingRequestDTO;
  }

  private static DynatraceServiceDTO createDynatraceServiceResponse(String displayName, String id) {
    return DynatraceServiceDTO.builder().displayName(displayName).entityId(id).build();
  }
}
