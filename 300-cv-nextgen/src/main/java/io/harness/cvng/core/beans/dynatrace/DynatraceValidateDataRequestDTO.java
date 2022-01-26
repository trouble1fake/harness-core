package io.harness.cvng.core.beans.dynatrace;

import io.harness.cvng.beans.MetricPackDTO;

import java.util.List;
import lombok.Value;

@Value
public class DynatraceValidateDataRequestDTO {
  List<String> serviceMethodsIds;
  List<MetricPackDTO> metricPacks;
}
