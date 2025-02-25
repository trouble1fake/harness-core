/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.parallel;

import io.harness.plancreator.stages.StageElementWrapperConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("parallelStageElementConfig")
public class ParallelStageElementConfig {
  @NotNull List<StageElementWrapperConfig> sections;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public ParallelStageElementConfig(List<StageElementWrapperConfig> sections) {
    this.sections = sections;
  }
}
