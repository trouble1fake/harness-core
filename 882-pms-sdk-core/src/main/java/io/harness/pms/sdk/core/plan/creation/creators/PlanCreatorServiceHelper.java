/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.plan.creation.PlanCreationBlobResponseUtils;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.MergePlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanCreatorServiceHelper {
  public Optional<PartialPlanCreator<?>> findPlanCreator(List<PartialPlanCreator<?>> planCreators, YamlField field) {
    return planCreators.stream()
        .filter(planCreator -> {
          Map<String, Set<String>> supportedTypes = planCreator.getSupportedTypes();
          return PlanCreatorUtils.supportsField(supportedTypes, field);
        })
        .findFirst();
  }

  public Dependencies handlePlanCreationResponses(List<PlanCreationResponse> planCreationResponses,
      MergePlanCreationResponse finalResponse, String currentYaml, Dependencies dependencies,
      List<Map.Entry<String, String>> dependenciesList) {
    String updatedYaml = currentYaml;
    List<String> errorMessages = planCreationResponses.stream()
                                     .filter(resp -> resp != null && EmptyPredicate.isNotEmpty(resp.getErrorMessages()))
                                     .flatMap(resp -> resp.getErrorMessages().stream())
                                     .collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(errorMessages)) {
      finalResponse.setErrorMessages(errorMessages);
      return dependencies.toBuilder().clearDependencies().build();
    }

    Map<String, String> newDependencies = new HashMap<>();
    Map<String, Dependency> newMetadataDependency = new HashMap<>();
    for (int i = 0; i < dependenciesList.size(); i++) {
      Map.Entry<String, String> entry = dependenciesList.get(i);
      String fieldYamlPath = entry.getValue();
      PlanCreationResponse response = planCreationResponses.get(i);
      if (response == null) {
        finalResponse.addDependency(currentYaml, entry.getKey(), fieldYamlPath);
        finalResponse.addDependencyMetadata(
            currentYaml, entry.getKey(), dependencies.getDependencyMetadataMap().get(entry.getKey()));
        continue;
      }

      finalResponse.mergeWithoutDependencies(response);

      if (response.getDependencies() != null
          && EmptyPredicate.isNotEmpty(response.getDependencies().getDependenciesMap())) {
        newDependencies.putAll(response.getDependencies().getDependenciesMap());

        if (EmptyPredicate.isNotEmpty(response.getDependencies().getDependencyMetadataMap())) {
          newMetadataDependency.putAll(response.getDependencies().getDependencyMetadataMap());
        }
      }
      if (response.getYamlUpdates() != null && EmptyPredicate.isNotEmpty(response.getYamlUpdates().getFqnToYamlMap())) {
        updatedYaml = PlanCreationBlobResponseUtils.mergeYamlUpdates(
            currentYaml, finalResponse.getYamlUpdates().getFqnToYamlMap());
        finalResponse.updateYamlInDependencies(updatedYaml);
      }
    }
    return dependencies.toBuilder()
        .setYaml(updatedYaml)
        .clearDependencies()
        .putAllDependencies(newDependencies)
        .putAllDependencyMetadata(newMetadataDependency)
        .build();
  }

  public void decorateNodesWithStageFqn(YamlField field, PlanCreationResponse planForField) {
    String stageFqn = YamlUtils.getStageFqnPath(field.getNode());
    if (!EmptyPredicate.isEmpty(stageFqn)) {
      planForField.getNodes().forEach((k, v) -> v.setStageFqn(stageFqn));

      if (planForField.getPlanNode() != null) {
        planForField.getPlanNode().setStageFqn(stageFqn);
      }
    }
  }

  public Dependencies removeInitialDependencies(Dependencies dependencies, Dependencies initialDependencies) {
    if (isEmptyDependencies(initialDependencies) || isEmptyDependencies(dependencies)) {
      return dependencies;
    }

    Dependencies.Builder builder = dependencies.toBuilder();
    initialDependencies.getDependenciesMap().keySet().forEach(builder::removeDependencies);
    return builder.build();
  }

  protected boolean isEmptyDependencies(Dependencies dependencies) {
    return dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap());
  }
}
