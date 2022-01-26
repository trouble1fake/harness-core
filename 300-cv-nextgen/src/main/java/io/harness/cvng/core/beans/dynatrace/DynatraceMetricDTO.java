package io.harness.cvng.core.beans.dynatrace;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DynatraceMetricDTO {
  @NonNull String displayName;
  @NonNull String metricId;
  @NonNull String unit;
}
