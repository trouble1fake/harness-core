/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fork;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class NGForkStepTest extends OrchestrationStepsTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject @InjectMocks private NGForkStep ngForkStep;
  private static final String CHILD_ID = generateUuid();

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestObtainChildren() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    ForkStepParameters stateParameters =
        ForkStepParameters.builder().parallelNodeIds(Collections.singleton(CHILD_ID)).build();
    ChildrenExecutableResponse childExecutableResponse =
        ngForkStep.obtainChildren(ambiance, stateParameters, inputPackage);

    assertThat(childExecutableResponse).isNotNull();
    assertThat(childExecutableResponse.getChildren(0).getChildNodeId()).isEqualTo(CHILD_ID);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestHandleChildResponse() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    ForkStepParameters stateParameters =
        ForkStepParameters.builder().parallelNodeIds(Collections.singleton(CHILD_ID)).build();

    Map<String, io.harness.tasks.ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put(CHILD_ID, StepResponseNotifyData.builder().nodeUuid(CHILD_ID).status(Status.FAILED).build())
            .build();
    StepResponse stepResponse = ngForkStep.handleChildrenResponse(ambiance, stateParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(ngForkStep.getStepParametersClass()).isEqualTo(ForkStepParameters.class);
  }
}
