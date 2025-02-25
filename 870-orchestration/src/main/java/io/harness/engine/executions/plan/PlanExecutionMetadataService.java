/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecutionMetadata;

import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PlanExecutionMetadataService {
  Optional<PlanExecutionMetadata> findByPlanExecutionId(String planExecutionId);
  PlanExecutionMetadata save(PlanExecutionMetadata planExecutionMetadata);

  String getYamlFromPlanExecutionId(String planExecutionId);
}
