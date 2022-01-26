package io.harness.cvng.core.beans.dynatrace;

import lombok.Value;

@Value
public class DynatraceSampleDataRequestDTO {
  String metricSelector;
  String serviceId;
}
