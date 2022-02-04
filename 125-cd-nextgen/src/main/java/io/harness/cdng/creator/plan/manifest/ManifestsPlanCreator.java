/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.ManifestsListConfigWrapper;
import io.harness.cdng.manifest.steps.ManifestStepParameters;
import io.harness.cdng.manifest.steps.ManifestStepParameters.ManifestStepParametersBuilder;
import io.harness.cdng.manifest.steps.ManifestsStep;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.ManifestOverrideSetWrapper;
import io.harness.cdng.manifest.yaml.ManifestOverrideSets;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.utilities.ManifestsUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CDC)
public class ManifestsPlanCreator extends ChildrenPlanCreator<ManifestsListConfigWrapper> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ManifestsListConfigWrapper config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // This is the actual service config passed from service plan creator
    ServiceConfig serviceConfig = (ServiceConfig) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_CONFIG).toByteArray());

    List<ManifestConfigWrapper> manifestListConfig =
        serviceConfig.getServiceDefinition().getServiceSpec().getManifests();
    ManifestListBuilder manifestListBuilder = new ManifestListBuilder(manifestListConfig);
    manifestListBuilder.addOverrideSets(serviceConfig);
    manifestListBuilder.addStageOverrides(serviceConfig);
    ManifestList manifestList = manifestListBuilder.build();
    if (EmptyPredicate.isEmpty(manifestList.getManifests())) {
      return planCreationResponseMap;
    }

    YamlField manifestsYamlField = ctx.getCurrentField();

    List<YamlNode> yamlNodes = Optional.of(manifestsYamlField.getNode().asArray()).orElse(Collections.emptyList());
    Map<String, YamlNode> manifestIdentifierToYamlNodeMap = yamlNodes.stream().collect(
        Collectors.toMap(e -> e.getField(YamlTypes.MANIFEST_CONFIG).getNode().getIdentifier(), k -> k));

    for (Map.Entry<String, ManifestInfo> identifierToManifestInfoEntry : manifestList.getManifests().entrySet()) {
      addDependenciesForIndividualManifest(manifestsYamlField, identifierToManifestInfoEntry.getKey(),
          identifierToManifestInfoEntry.getValue().getParams(), manifestIdentifierToYamlNodeMap,
          planCreationResponseMap);
    }

    return planCreationResponseMap;
  }
  /*
     addDependenciesForIndividualManifest() -> adds dependencies for a particular manifests yaml field. Manifest yaml
     field is fetched with the help of manifestIdentifier and manifestIdentifierToYamlNodeMap. If the identifier is not
     present in manifestIdentifierToYamlNodeMap, we will return the fqn of the first manifest element in manifests
     array. This can happen in case of useFromStage.
   */
  public String addDependenciesForIndividualManifest(YamlField manifestsYamlField, String manifestIdentifier,
      ManifestStepParameters stepParameters, Map<String, YamlNode> manifestIdentifierToYamlNodeMap,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField individualManifest = ManifestsUtility.fetchIndividualManifestYamlField(
        manifestsYamlField, manifestIdentifier, manifestIdentifierToYamlNodeMap);

    String individualManifestPlanNodeId = UUIDGenerator.generateUuid();
    Map<String, ByteString> metadataDependency =
        prepareMetadataForIndividualManifestPlanCreator(individualManifestPlanNodeId, stepParameters);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(individualManifestPlanNodeId, individualManifest);
    PlanCreationResponseBuilder individualManifestPlanResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                individualManifestPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    planCreationResponseMap.put(individualManifestPlanNodeId, individualManifestPlanResponse.build());
    return individualManifestPlanNodeId;
  }

  public Map<String, ByteString> prepareMetadataForIndividualManifestPlanCreator(
      String individualManifestPlanNodeId, ManifestStepParameters stepParameters) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(individualManifestPlanNodeId)));
    metadataDependency.put(PlanCreatorConstants.MANIFEST_STEP_PARAMETER,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stepParameters)));
    return metadataDependency;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, ManifestsListConfigWrapper config, List<String> childrenNodeIds) {
    String manifestsId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());
    ForkStepParameters stepParameters = ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build();
    return PlanNode.builder()
        .uuid(manifestsId)
        .stepType(ManifestsStep.STEP_TYPE)
        .name(PlanCreatorConstants.MANIFESTS_NODE_NAME)
        .identifier(YamlTypes.MANIFEST_LIST_CONFIG)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<ManifestsListConfigWrapper> getFieldClass() {
    return ManifestsListConfigWrapper.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.MANIFEST_LIST_CONFIG, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Value
  private static class ManifestList {
    Map<String, ManifestInfo> manifests;
  }

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class ManifestListBuilder {
    Map<String, ManifestInfoBuilder> manifests;

    ManifestListBuilder(List<ManifestConfigWrapper> manifestConfigList) {
      if (manifestConfigList == null) {
        this.manifests = null;
        return;
      }

      this.manifests = new HashMap<>();
      if (EmptyPredicate.isNotEmpty(manifestConfigList)) {
        manifestConfigList.forEach(mcw -> {
          ManifestConfig mc = mcw.getManifest();
          if (manifests.containsKey(mc.getIdentifier())) {
            throw new InvalidRequestException(
                String.format("Duplicate identifier: [%s] in manifests", mc.getIdentifier()));
          }
          this.manifests.put(mc.getIdentifier(),
              new ManifestInfoBuilder(ManifestStepParameters.builder()
                                          .identifier(mc.getIdentifier())
                                          .type(mc.getType().getDisplayName())
                                          .spec(mc.getSpec())
                                          .order(this.manifests.size())));
        });
      }
    }

    ManifestList build() {
      return new ManifestList(manifests == null
              ? null
              : manifests.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build())));
    }

    void addOverrideSets(ServiceConfig serviceConfig) {
      if (serviceConfig.getStageOverrides() == null
          || ParameterField.isNull(serviceConfig.getStageOverrides().getUseManifestOverrideSets())) {
        return;
      }

      for (String useManifestOverrideSet : serviceConfig.getStageOverrides().getUseManifestOverrideSets().getValue()) {
        List<ManifestOverrideSets> manifestOverrideSetsList =
            serviceConfig.getServiceDefinition()
                .getServiceSpec()
                .getManifestOverrideSets()
                .stream()
                .map(ManifestOverrideSetWrapper::getOverrideSet)
                .filter(overrideSet -> overrideSet.getIdentifier().equals(useManifestOverrideSet))
                .collect(Collectors.toList());
        if (manifestOverrideSetsList.size() == 0) {
          throw new InvalidRequestException(
              String.format("Invalid identifier [%s] in manifest override sets", useManifestOverrideSet));
        }
        if (manifestOverrideSetsList.size() > 1) {
          throw new InvalidRequestException(
              String.format("Duplicate identifier [%s] in manifest override sets", useManifestOverrideSet));
        }

        List<ManifestConfigWrapper> manifestConfigList = manifestOverrideSetsList.get(0).getManifests();
        addOverrides(manifestConfigList, ManifestStepParametersBuilder::overrideSet);
      }
    }

    void addStageOverrides(ServiceConfig serviceConfig) {
      if (serviceConfig.getStageOverrides() == null || serviceConfig.getStageOverrides().getManifests() == null) {
        return;
      }
      List<ManifestConfigWrapper> manifestConfigList = serviceConfig.getStageOverrides().getManifests();
      addOverrides(manifestConfigList, ManifestStepParametersBuilder::stageOverride);
    }

    private void addOverrides(List<ManifestConfigWrapper> manifestConfigList,
        BiConsumer<ManifestStepParametersBuilder, ManifestAttributes> consumer) {
      if (EmptyPredicate.isEmpty(manifestConfigList)) {
        return;
      }

      for (ManifestConfigWrapper manifestConfigWrapper : manifestConfigList) {
        ManifestConfig mc = manifestConfigWrapper.getManifest();
        ManifestInfoBuilder manifestInfoBuilder = manifests.computeIfAbsent(mc.getIdentifier(),
            identifier
            -> new ManifestInfoBuilder(
                ManifestStepParameters.builder().order(manifests.size()).identifier(identifier)));
        consumer.accept(manifestInfoBuilder.getBuilder(), mc.getSpec());
      }
    }
  }

  @Value
  private static class ManifestInfo {
    ManifestStepParameters params;
  }

  @Value
  private static class ManifestInfoBuilder {
    ManifestStepParametersBuilder builder;

    ManifestInfo build() {
      return new ManifestInfo(builder.build());
    }
  }
}
