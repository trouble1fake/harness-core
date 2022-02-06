/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.artifactory;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.service.ArtifactoryRegistryServiceImpl;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.nexus.NexusRequest;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.PIPELINE)
public class ArtifactoryRegistryServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
                           .usingFilesUnderDirectory("960-api-services/src/test/resources")
                           .port(Options.DYNAMIC_PORT),
          false);
  @Mock private ArtifactoryClientImpl artifactoryClient;
  @InjectMocks ArtifactoryRegistryServiceImpl artifactoryRegistryService;

  private static String url;
  private static NexusRequest nexusConfig;

  private static String ARTIFACTORY_URL_HOSTNAME = "artifactory.harness.io";
  private static String ARTIFACTORY_URL = "https://" + ARTIFACTORY_URL_HOSTNAME;
  private static String ARTIFACTORY_USERNAME = "username";
  private static String ARTIFACTORY_PASSWORD = "password";
  ;
  private static int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  private static Map<String, List<BuildDetailsInternal>> buildDetailsData;

  @Before
  public void before() {
    buildDetailsData = new HashMap<>();

    List<BuildDetailsInternal> bdiList = new ArrayList<>();
    String repoUrl = "test1.artifactory.harness.io";
    String imageName = "superApp";
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "1.0"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.0"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "3.0"));
    buildDetailsData.put("bdi1", bdiList);

    bdiList = new ArrayList<>();
    repoUrl = "test2.artifactory.harness.io";
    imageName = "super/duper/app";
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.4.1"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.4.2"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.5"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.5.3"));
    buildDetailsData.put("bdi2", bdiList);

    bdiList = new ArrayList<>();
    repoUrl = "test2.artifactory.harness.io";
    imageName = "extra/megaapp";
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "a4"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "b23"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "latest"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "basic"));
    buildDetailsData.put("bdi3", bdiList);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuilds() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test1.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(artifactoryClient)
        .getArtifactsDetails(
            artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name(), MAX_NO_OF_TAGS_PER_IMAGE);

    List<BuildDetailsInternal> response = artifactoryRegistryService.getBuilds(
        artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name(), MAX_NO_OF_TAGS_PER_IMAGE);
    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(3);
    for (BuildDetailsInternal bdi : response) {
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE))
          .startsWith("test1.artifactory.harness.io/superApp:");
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE))
          .isNotEqualTo("test1.artifactory.harness.io/superApp:");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_1() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test1.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(artifactoryClient)
        .getArtifactsDetails(
            artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name(), MAX_NO_OF_TAGS_PER_IMAGE);

    BuildDetailsInternal response = artifactoryRegistryService.getLastSuccessfulBuildFromRegex(
        artifactoryInternalConfig, "test1", "superApp", RepositoryFormat.docker.name(), "[\\d]{1}.0");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("3.0");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test1.artifactory.harness.io/superApp:3.0");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_2() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test3.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi2"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "super/duper/app", RepositoryFormat.docker.name(),
            MAX_NO_OF_TAGS_PER_IMAGE);

    BuildDetailsInternal response =
        artifactoryRegistryService.getLastSuccessfulBuildFromRegex(artifactoryInternalConfig, "test2",
            "super/duper/app", RepositoryFormat.docker.name(), "[\\d]{1}.[\\d]{1}.[\\d]{1}");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("2.5.3");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test2.artifactory.harness.io/super/duper/app:2.5.3");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_3() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi3"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(),
            MAX_NO_OF_TAGS_PER_IMAGE);

    BuildDetailsInternal response = artifactoryRegistryService.getLastSuccessfulBuildFromRegex(
        artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(), "\\\\*");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("latest");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test2.artifactory.harness.io/extra/megaapp:latest");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_NoArtifactFound() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi3"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(),
            MAX_NO_OF_TAGS_PER_IMAGE);

    try {
      artifactoryRegistryService.getLastSuccessfulBuildFromRegex(
          artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(), "noArtifactFound");
    } catch (HintException hintException) {
      assertThat(hintException.getMessage()).isEqualTo("Could not get the last successful build");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testVerifyBuildNumber() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(buildDetailsData.get("bdi3"))
        .when(artifactoryClient)
        .getArtifactsDetails(artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(),
            MAX_NO_OF_TAGS_PER_IMAGE);

    BuildDetailsInternal response = artifactoryRegistryService.verifyBuildNumber(
        artifactoryInternalConfig, "test2", "extra/megaapp", RepositoryFormat.docker.name(), "b23");
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("b23");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("test2.artifactory.harness.io/extra/megaapp:b23");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void validateCredentials() throws IOException {
    ArtifactoryConfigRequest artifactoryInternalConfig = ArtifactoryConfigRequest.builder()
                                                             .artifactoryUrl(ARTIFACTORY_URL)
                                                             .username(ARTIFACTORY_USERNAME)
                                                             .password(ARTIFACTORY_PASSWORD.toCharArray())
                                                             .artifactRepositoryUrl("test2.artifactory.harness.io")
                                                             .build();

    doReturn(true).when(artifactoryClient).validateArtifactServer(artifactoryInternalConfig);

    boolean response = artifactoryRegistryService.validateCredentials(artifactoryInternalConfig);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(true);
  }

  private BuildDetailsInternal createBuildDetails(String repoUrl, String port, String imageName, String tag) {
    return BuildDetailsInternal.builder()
        .number(tag)
        .metadata(createBuildMetadata(repoUrl, port, imageName, tag))
        .build();
  }

  private String generateArtifactPullUrl(String hostname, String port, String imagePath, String imageTag) {
    return hostname + (isEmpty(port) ? "" : ":" + port) + "/" + imagePath + ":" + imageTag;
  }

  private Map<String, String> createBuildMetadata(String hostname, String port, String imagePath, String imageTag) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, generateArtifactPullUrl(hostname, port, imagePath, imageTag));
    metadata.put(ArtifactMetadataKeys.TAG, imageTag);
    return metadata;
  }
}
