package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.serializer.JsonUtils;

import com.google.gson.Gson;
import org.slf4j.Logger;

import java.lang.reflect.Type;

public interface DataCollectionRequestResultExtractor<ConnectorConfig extends ConnectorConfigDTO> {
  default<T> T performRequestAndGetDataResult(DataCollectionRequest<ConnectorConfig> dataCollectionRequest,
                                              OnboardingService onboardingService, Type type, ProjectParams projectParams, String connectorIdentifier,
                                              String tracingId, Logger logger) {

    OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                                                    .dataCollectionRequest(dataCollectionRequest)
                                                    .connectorIdentifier(connectorIdentifier)
                                                    .accountId(projectParams.getAccountIdentifier())
                                                    .orgIdentifier(projectParams.getOrgIdentifier())
                                                    .projectIdentifier(projectParams.getProjectIdentifier())
                                                    .tracingId(tracingId)
                                                    .build();
    logger.debug("Triggering {} onboarding request. TracingId: {}", dataCollectionRequest.getType(), tracingId);
    OnboardingResponseDTO response =
        onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
    logger.debug("{} onboarding request was successful. Tracing id: {}", dataCollectionRequest.getType(), tracingId);
    return new Gson().fromJson(JsonUtils.asJson(response.getResult()), type);
  }
}
