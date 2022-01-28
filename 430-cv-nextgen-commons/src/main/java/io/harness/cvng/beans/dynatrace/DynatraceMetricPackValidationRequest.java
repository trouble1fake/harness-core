package io.harness.cvng.beans.dynatrace;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.MetricPackDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DYNATRACE_VALIDATION_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class DynatraceMetricPackValidationRequest extends DynatraceRequest {
  public static final String DSL = DataCollectionRequest.readDSL(
      "dynatrace-metric-pack-validation.datacollection", DynatraceMetricPackValidationRequest.class);

  private static final String RESOLUTION_PARAM = "1m";
  private String serviceId;
  private List<String> serviceMethodsIds;
  private MetricPackDTO metricPack;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> commonEnvVariables = super.fetchDslEnvVariables();
    commonEnvVariables.put("from", "now-1h");
    commonEnvVariables.put("resolution", "1m");
    String serviceMethodsIdsParam;

    if (isNotEmpty(serviceMethodsIds)) {
      serviceMethodsIdsParam = serviceMethodsIds.stream()
                                   .map(serviceMethodId -> "\"".concat(serviceMethodId).concat("\""))
                                   .reduce((prev, next) -> prev.concat(",").concat(next))
                                   .orElse(null);
      commonEnvVariables.put(
          "entitySelector", "type(\"dt.entity.service_method\"),entityId(".concat(serviceMethodsIdsParam).concat(")"));
    } else {
      throw new IllegalArgumentException("Service methods IDs must be provided for Dynatrace data collection.");
    }
    List<Map<String, String>> metricsToValidate = metricPack.getMetrics()
                                                      .stream()
                                                      .map(metricDefinitionDTO -> {
                                                        Map<String, String> queryData = new HashMap<>();
                                                        queryData.put("query", metricDefinitionDTO.getPath());
                                                        queryData.put("metricName", metricDefinitionDTO.getName());
                                                        return queryData;
                                                      })
                                                      .collect(Collectors.toList());
    commonEnvVariables.put("metricsToValidate", metricsToValidate);
    return commonEnvVariables;
  }
}
