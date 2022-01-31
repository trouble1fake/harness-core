/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.mappers;

import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactoryResourceMapperTest extends CategoryTest {
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToArtifactoryResponse() {
    List<ArtifactoryArtifactDelegateResponse> artifactoryArtifactDelegateResponses =
        Lists.newArrayList(ArtifactoryArtifactDelegateResponse.builder()
                               .buildDetails(ArtifactBuildDetailsNG.builder().build())
                               .imagePath("/")
                               .build());
    ArtifactoryResponseDTO nexusResponseDTO =
        ArtifactoryResourceMapper.toArtifactoryResponse(artifactoryArtifactDelegateResponses);
    assertThat(nexusResponseDTO).isNotNull();
    assertThat(nexusResponseDTO.getBuildDetailsList()).isNotEmpty();
    assertThat(nexusResponseDTO.getBuildDetailsList().get(0))
        .isEqualTo(ArtifactoryBuildDetailsDTO.builder().imagePath("/").build());
  }
}
