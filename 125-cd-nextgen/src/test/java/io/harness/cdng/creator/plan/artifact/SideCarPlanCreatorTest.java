/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.artifact;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDC)
public class SideCarPlanCreatorTest extends CDNGTestBase {
  @Inject KryoSerializer kryoSerializer;
  @Inject @InjectMocks SideCarArtifactPlanCreator sidecarPlanCreator;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(sidecarPlanCreator.getFieldClass()).isEqualTo(SidecarArtifact.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = sidecarPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.SIDECAR_ARTIFACT_CONFIG)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SIDECAR_ARTIFACT_CONFIG).size()).isEqualTo(3);
    assertThat(
        supportedTypes.get(YamlTypes.SIDECAR_ARTIFACT_CONFIG).contains(ArtifactSourceConstants.DOCKER_REGISTRY_NAME))
        .isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SIDECAR_ARTIFACT_CONFIG).contains(ArtifactSourceConstants.ECR_NAME))
        .isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SIDECAR_ARTIFACT_CONFIG).contains(ArtifactSourceConstants.GCR_NAME))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetParentNode() {
    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    String uuid = UUIDGenerator.generateUuid();
    String identifier = "sidecar1";
    ArtifactStepParameters artifactStepParameters = ArtifactStepParameters.builder().identifier(identifier).build();

    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(uuid)));
    metadataDependency.put(
        PlanCreatorConstants.IDENTIFIER, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(identifier)));
    metadataDependency.put(PlanCreatorConstants.SIDECAR_STEP_PARAMETERS,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(artifactStepParameters)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();
    PlanCreationResponse sidecarPlanCreationResponse = sidecarPlanCreator.createPlanForField(ctx, null);
    PlanNode sidecarPlanNode = sidecarPlanCreationResponse.getPlanNode();
    assertThat(sidecarPlanNode.getUuid()).isEqualTo(uuid);
    assertThat(sidecarPlanNode.getIdentifier()).isEqualTo(identifier);
    assertThat(sidecarPlanNode.getStepParameters()).isEqualTo(artifactStepParameters);
  }
}