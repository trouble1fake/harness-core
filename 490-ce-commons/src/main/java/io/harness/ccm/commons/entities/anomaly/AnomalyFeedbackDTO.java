package io.harness.ccm.commons.entities.anomaly;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "AnomalyFeedback", description = "The query object for cost anomaly feedback")
public class AnomalyFeedbackDTO {
  AnomalyFeedback feedback;
}
