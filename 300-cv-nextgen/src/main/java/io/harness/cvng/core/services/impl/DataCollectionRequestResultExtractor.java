package io.harness.cvng.core.services.impl;

import com.google.gson.Gson;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.serializer.JsonUtils;

import java.lang.reflect.Type;

public interface DataCollectionRequestResultExtractor<ConnectorConfig extends ConnectorConfigDTO> {

    default <T> T performRequestAndGetDataResult(DataCollectionRequest<ConnectorConfig> dataCollectionRequest,
                                                 OnboardingService onboardingService,
                                                 Type type, ProjectParams projectParams, String connectorIdentifier, String tracingId) {
        OnboardingRequestDTO onboardingRequestDTO = OnboardingRequestDTO.builder()
                .dataCollectionRequest(dataCollectionRequest)
                .connectorIdentifier(connectorIdentifier)
                .accountId(projectParams.getAccountIdentifier())
                .orgIdentifier(projectParams.getOrgIdentifier())
                .projectIdentifier(projectParams.getProjectIdentifier())
                .tracingId(tracingId)
                .build();

        OnboardingResponseDTO response =
                onboardingService.getOnboardingResponse(projectParams.getAccountIdentifier(), onboardingRequestDTO);
        return new Gson().fromJson(JsonUtils.asJson(response.getResult()), type);
    }
}
