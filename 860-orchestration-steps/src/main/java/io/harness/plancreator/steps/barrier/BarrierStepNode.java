/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.barrier;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.http.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.BARRIER)
@TypeAlias("BarrierStepNode")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.plancreator.steps.barrier.BarrierStepNode")
public class BarrierStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.Barrier;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  BarrierStepInfo barrierStepInfo;

  @Override
  public String getType() {
    return StepSpecTypeConstants.BARRIER;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return barrierStepInfo;
  }

  // will re-iterate
  enum StepType {
    Barrier(StepSpecTypeConstants.BARRIER);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
