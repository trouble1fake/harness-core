package io.harness.cvng.beans.dynatrace;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DYNATRACE_METRIC_LIST_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
@EqualsAndHashCode(callSuper = true)
public class DynatraceMetricListRequest extends DynatraceRequest {
  private static final Long PAGE_SIZE = 500L;
  private static final String DEFAULT_METRIC_SELECTOR = "builtin:service.*";

  private static final String DSL =
      DataCollectionRequest.readDSL("dynatrace-metric-list.datacollection", DynatraceMetricListRequest.class);

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> commonEnvVariables = super.fetchDslEnvVariables();
    commonEnvVariables.put("metricSelector", DEFAULT_METRIC_SELECTOR);
    commonEnvVariables.put("pageSize", PAGE_SIZE);
    return commonEnvVariables;
  }
}
