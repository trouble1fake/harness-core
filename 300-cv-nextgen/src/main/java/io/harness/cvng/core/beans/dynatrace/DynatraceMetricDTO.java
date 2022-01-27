package io.harness.cvng.core.beans.dynatrace;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class DynatraceMetricDTO {
  @NonNull String displayName;
  @NonNull String metricId;
  @NonNull String unit;
}
