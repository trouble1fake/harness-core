/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import io.fabric8.utils.Lists;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static String ARTIFACTORY_URL_HOSTNAME = "artifactory.harness.io";
  private static String ARTIFACTORY_URL = "https://" + ARTIFACTORY_URL_HOSTNAME;
  private static String ARTIFACT_REPO_URL = "test.artifactory.harness.io";
  private static String ARTIFACTORY_USERNAME = "username";
  private static String ARTIFACTORY_PASSWORD = "password";
  private static String REPO_NAME = "repoName";
  private static String IMAGE_NAME = "imageName";
  private static String IMAGE_TAG = "imageTag";
  private static String IMAGE_TAG_REGEX = "\\*";
  private static int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  @Mock ArtifactoryRegistryService artifactoryRegistryService;
  @InjectMocks ArtifactoryArtifactTaskHandler artifactoryArtifactService;

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO = createArtifactoryCredentials();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = createArtifactoryConnector(artifactoryUsernamePasswordAuthDTO);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(createBuildMetadata()).build();
    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .imagePath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tag(IMAGE_TAG)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(buildDetailsInternal)
        .when(artifactoryRegistryService)
        .verifyBuildNumber(any(), any(), any(), any(), any());

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(ArtifactoryArtifactDelegateResponse.class);
    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(sourceAttributes.getTag());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(ARTIFACTORY_URL)
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number(IMAGE_TAG).build();
    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .imagePath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tagRegex(IMAGE_TAG_REGEX)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(buildDetailsInternal)
        .when(artifactoryRegistryService)
        .getLastSuccessfulBuildFromRegex(
            artifactoryInternalConfig, REPO_NAME, IMAGE_NAME, RepositoryFormat.docker.name(), IMAGE_TAG_REGEX);

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(ArtifactoryArtifactDelegateResponse.class);
    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(IMAGE_TAG);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(ARTIFACTORY_URL)
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, ARTIFACT_REPO_URL + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
    metadata.put(ArtifactMetadataKeys.TAG, IMAGE_TAG);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(metadata).build();
    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .imagePath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(Lists.newArrayList(buildDetailsInternal))
        .when(artifactoryRegistryService)
        .getBuilds(
            artifactoryInternalConfig, REPO_NAME, IMAGE_NAME, RepositoryFormat.docker.name(), MAX_NO_OF_TAGS_PER_IMAGE);

    ArtifactTaskExecutionResponse lastSuccessfulBuild = artifactoryArtifactService.getBuilds(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(ArtifactoryArtifactDelegateResponse.class);
    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(IMAGE_TAG);
    assertThat(attributes.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo(ARTIFACT_REPO_URL + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(ARTIFACTORY_URL)
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();

    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .imagePath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tag(IMAGE_TAG)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();
    doReturn(true).when(artifactoryRegistryService).validateCredentials(artifactoryInternalConfig);

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.validateArtifactServer(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.isArtifactServerValid()).isTrue();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsRegex() {
    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .imagePath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tag(IMAGE_TAG)
                                                              .tagRegex(IMAGE_TAG_REGEX)
                                                              .build();
    boolean regex = artifactoryArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();

    sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                           .repositoryName(REPO_NAME)
                           .imagePath(IMAGE_NAME)
                           .repositoryFormat(RepositoryFormat.docker.name())
                           .tagRegex(IMAGE_TAG_REGEX)
                           .build();
    regex = artifactoryArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();

    sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                           .repositoryName(REPO_NAME)
                           .imagePath(IMAGE_NAME)
                           .repositoryFormat(RepositoryFormat.docker.name())
                           .tag(IMAGE_TAG)
                           .build();
    regex = artifactoryArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isFalse();
  }

  private ArtifactoryUsernamePasswordAuthDTO createArtifactoryCredentials() {
    return ArtifactoryUsernamePasswordAuthDTO.builder()
        .username(ARTIFACTORY_USERNAME)
        .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
        .build();
  }

  private ArtifactoryConnectorDTO createArtifactoryConnector(
      ArtifactoryUsernamePasswordAuthDTO artifactoryCredentials) {
    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(ARTIFACTORY_URL)
        .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryCredentials).build())
        .build();
  }

  private Map<String, String> createBuildMetadata() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, ARTIFACTORY_URL_HOSTNAME + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
    metadata.put(ArtifactMetadataKeys.TAG, IMAGE_TAG);
    return metadata;
  }

  private ArtifactoryArtifactDelegateRequest createArtifactoryDelegateRequestWithTag(
      ArtifactoryConnectorDTO artifactoryConnectorDTO) {
    return createArtifactoryDelegateRequest(artifactoryConnectorDTO, IMAGE_TAG, null, ARTIFACTORY_URL_HOSTNAME);
  }

  private ArtifactoryArtifactDelegateRequest createArtifactoryDelegateRequestWithTagRegex(
      ArtifactoryConnectorDTO artifactoryConnectorDTO) {
    return createArtifactoryDelegateRequest(artifactoryConnectorDTO, null, IMAGE_TAG_REGEX, ARTIFACTORY_URL_HOSTNAME);
  }

  private ArtifactoryArtifactDelegateRequest createArtifactoryDelegateRequest(
      ArtifactoryConnectorDTO artifactoryConnectorDTO, String tag, String tagRegex, String artifactRepoUrl) {
    return ArtifactoryArtifactDelegateRequest.builder()
        .repositoryName(REPO_NAME)
        .imagePath(IMAGE_NAME)
        .repositoryFormat(RepositoryFormat.docker.name())
        .tag(tag)
        .tagRegex(tagRegex)
        .artifactRepositoryUrl(artifactRepoUrl)
        .artifactoryConnectorDTO(artifactoryConnectorDTO)
        .build();
  }

  private ArtifactoryConfigRequest createArtifactoryConfigWithUrl(
      ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO,
      ArtifactoryConnectorDTO artifactoryConnectorDTO) {
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
        .username(artifactoryUsernamePasswordAuthDTO.getUsername())
        .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
        .hasCredentials(true)
        .artifactRepositoryUrl(ARTIFACT_REPO_URL)
        .build();
  }
}
