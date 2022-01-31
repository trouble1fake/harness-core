/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.kinds;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactoryStoreConfigTest extends CategoryTest {
  static final String MOCK_CONNECTOR_REF_OVERWRITE = "mockConnectorRefOverwrite";
  static final String MOCK_ARTIFACT_NAME_OVERWRITE = "mockArtifactNameOverwrite";
  static final String MOCK_REPOSITORY_PATH_OVERWRITE = "mockRepositoryPathOverwrite";
  static final String MOCK_VERSION_OVERWRITE = "mockVersionOverwrite";
  static final String MOCK_CONNECTOR_REF = "mockConnectorRef";
  static final String MOCK_ARTIFACT_NAME = "mockArtifactName";
  static final String MOCK_REPOSITORY_PATH = "mockRepositoryPath";
  static final String MOCK_VERSION = "mockVersion";

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)

  public void testApplyOverrides() {
    ArtifactoryStoreConfig origin =
        CreateMockArtifactoryStoreConfig(MOCK_CONNECTOR_REF, MOCK_ARTIFACT_NAME, MOCK_REPOSITORY_PATH, MOCK_VERSION);
    ArtifactoryStoreConfig override = CreateMockArtifactoryStoreConfig(MOCK_CONNECTOR_REF_OVERWRITE,
        MOCK_ARTIFACT_NAME_OVERWRITE, MOCK_REPOSITORY_PATH_OVERWRITE, MOCK_VERSION_OVERWRITE);
    checkArtifactoryStoreConfigParameters(origin, override, MOCK_CONNECTOR_REF_OVERWRITE, MOCK_ARTIFACT_NAME_OVERWRITE,
        MOCK_REPOSITORY_PATH_OVERWRITE, MOCK_VERSION_OVERWRITE);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)

  public void testApplyOverridesEmpty() {
    ArtifactoryStoreConfig origin =
        CreateMockArtifactoryStoreConfig(MOCK_CONNECTOR_REF, MOCK_ARTIFACT_NAME, MOCK_REPOSITORY_PATH, MOCK_VERSION);
    ArtifactoryStoreConfig override = ArtifactoryStoreConfig.builder().build();
    checkArtifactoryStoreConfigParameters(
        origin, override, MOCK_CONNECTOR_REF, MOCK_ARTIFACT_NAME, MOCK_REPOSITORY_PATH, MOCK_VERSION);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testClodeInternal() {
    ArtifactoryStoreConfig origin =
        CreateMockArtifactoryStoreConfig(MOCK_CONNECTOR_REF, MOCK_ARTIFACT_NAME, MOCK_REPOSITORY_PATH, MOCK_VERSION);

    ArtifactoryStoreConfig originClone = (ArtifactoryStoreConfig) origin.cloneInternal();

    assertThat(originClone.getArtifactName().getValue()).isEqualTo(MOCK_ARTIFACT_NAME);
    assertThat(originClone.getConnectorReference().getValue()).isEqualTo(MOCK_CONNECTOR_REF);
    assertThat(originClone.getRepositoryPath().getValue()).isEqualTo(MOCK_REPOSITORY_PATH);
    assertThat(originClone.getVersion().getValue()).isEqualTo(MOCK_VERSION);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractConnectorRefs() {
    ArtifactoryStoreConfig origin =
        CreateMockArtifactoryStoreConfig(MOCK_CONNECTOR_REF, MOCK_ARTIFACT_NAME, MOCK_REPOSITORY_PATH, MOCK_VERSION);
    assertThat(origin.extractConnectorRefs().get("connectorRef").getValue()).isEqualTo(MOCK_CONNECTOR_REF);
  }

  private void checkArtifactoryStoreConfigParameters(ArtifactoryStoreConfig origin, ArtifactoryStoreConfig override,
      String mockConnectorRef, String mockArtifactName, String mockRepositoryPath, String mockVersion) {
    ArtifactoryStoreConfig result = (ArtifactoryStoreConfig) origin.applyOverrides(override);
    assertThat(result.getConnectorReference().getValue()).isEqualTo(mockConnectorRef);
    assertThat(result.getArtifactName().getValue()).isEqualTo(mockArtifactName);
    assertThat(result.getRepositoryPath().getValue()).isEqualTo(mockRepositoryPath);
    assertThat(result.getVersion().getValue()).isEqualTo(mockVersion);
  }

  private ArtifactoryStoreConfig CreateMockArtifactoryStoreConfig(
      String mockConnector, String mockArtifact, String mockRepository, String mockVersion) {
    return ArtifactoryStoreConfig.builder()
        .connectorRef(ParameterField.createValueField(mockConnector))
        .artifactName(ParameterField.createValueField(mockArtifact))
        .repositoryPath(ParameterField.createValueField(mockRepository))
        .version(ParameterField.createValueField(mockVersion))
        .build();
  }
}
