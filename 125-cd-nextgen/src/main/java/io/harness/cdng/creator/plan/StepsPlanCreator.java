/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.advisers.RollbackCustomAdviser;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.YamlTypes;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.common.NGSectionStepWithRollbackInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class StepsPlanCreator extends ChildrenPlanCreator<StepsExecutionConfig> {
  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StepsExecutionConfig config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stepYamlFields = ctx.getStepYamlFieldsFromStepsAsCurrentYamlField();
    for (YamlField stepYamlField : stepYamlFields) {
      Map<String, YamlField> stepYamlFieldMap = new HashMap<>();
      stepYamlFieldMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
      responseMap.put(stepYamlField.getNode().getUuid(),
          PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(stepYamlFieldMap)).build());
    }
    return responseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StepsExecutionConfig config, List<String> childrenNodeIds) {
    StepParameters stepParameters = NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).build();
    return PlanNode.builder()
        .uuid(ctx.getCurrentField().getNode().getUuid())
        .identifier(YAMLFieldNameConstants.STEPS)
        .stepType(NGSectionStepWithRollbackInfo.STEP_TYPE)
        .name(YAMLFieldNameConstants.STEPS)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainment(AdviserObtainment.newBuilder().setType(RollbackCustomAdviser.ADVISER_TYPE).build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  @Override
  public Class<StepsExecutionConfig> getFieldClass() {
    return StepsExecutionConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.STEPS, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }
}
