package io.harness.cvng.core.beans.dynatrace;

import io.harness.cvng.beans.MetricPackDTO;
import lombok.Value;

import java.util.List;

@Value
public class DynatraceValidateDataRequestDTO {
    List<String> serviceMethodsIds;
    List<MetricPackDTO> metricPacks;
}
