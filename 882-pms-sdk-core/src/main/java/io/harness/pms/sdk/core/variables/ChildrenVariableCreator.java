/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ChildrenVariableCreator implements VariableCreator {
  public abstract LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config);

  public abstract VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config);

  @Override
  public VariableCreationResponse createVariablesForField(VariableCreationContext ctx, YamlField field) {
    VariableCreationResponse finalResponse = VariableCreationResponse.builder().build();

    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodes =
        createVariablesForChildrenNodes(ctx, field);
    for (Map.Entry<String, VariableCreationResponse> entry : variablesForChildrenNodes.entrySet()) {
      finalResponse.addYamlProperties(entry.getValue().getYamlProperties());
      finalResponse.addResolvedDependencies(entry.getValue().getResolvedDependencies());
      finalResponse.addDependencies(entry.getValue().getDependencies());
    }
    VariableCreationResponse variablesForParentNode = createVariablesForParentNode(ctx, field);
    finalResponse.addYamlProperties(variablesForParentNode.getYamlProperties());
    finalResponse.addYamlOutputProperties(variablesForParentNode.getYamlOutputProperties());
    finalResponse.addResolvedDependencies(variablesForParentNode.getResolvedDependencies());
    finalResponse.addDependencies(variablesForParentNode.getDependencies());
    return finalResponse;
  }
}
