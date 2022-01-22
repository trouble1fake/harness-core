package io.harness.cvng.beans.dynatrace;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.MetricPackDTO;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.harness.annotations.dev.HarnessTeam.CV;

@JsonTypeName("DYNATRACE_SAMPLE_DATA_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class DynatraceSampleDataRequest extends DynatraceRequest {
    public static final String DSL = DataCollectionRequest.readDSL(
            "dynatrace-sample-data.datacollection", DynatraceSampleDataRequest.class);

    private static final String RESOLUTION_PARAM = "1m";
    private String serviceId;
    private long from;
    private long to;
    private String metricSelector;
    @Override
    public String getDSL() {
        return DSL;
    }

    @Override
    public Map<String, Object> fetchDslEnvVariables() {
        Map<String, Object> commonEnvVariables = super.fetchDslEnvVariables();
        commonEnvVariables.put("entitySelector", "type(\"dt.entity.service\"),entityId(".concat(serviceId).concat(")"));
        commonEnvVariables.put("resolution", "1m");
        commonEnvVariables.put("metricSelector", metricSelector);
        commonEnvVariables.put("from", from);
        commonEnvVariables.put("to", to);
        return commonEnvVariables;
    }
}
