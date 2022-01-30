/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.ArtifactoryRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class ArtifactoryArtifactTaskHandler extends DelegateArtifactTaskHandler<ArtifactoryArtifactDelegateRequest> {
  private final ArtifactoryRegistryService artifactoryRegistryService;
  private final SecretDecryptionService secretDecryptionService;

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(ArtifactoryArtifactDelegateRequest attributesRequest) {
    BuildDetailsInternal lastSuccessfulBuild;
    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild = artifactoryRegistryService.getLastSuccessfulBuildFromRegex(
          ArtifactoryRequestResponseMapper.toArtifactoryInternalConfig(attributesRequest),
          attributesRequest.getRepository(), attributesRequest.getImagePath(), attributesRequest.getRepositoryFormat(),
          attributesRequest.getTagRegex());
    } else {
      lastSuccessfulBuild = artifactoryRegistryService.verifyBuildNumber(
          ArtifactoryRequestResponseMapper.toArtifactoryInternalConfig(attributesRequest),
          attributesRequest.getRepository(), attributesRequest.getImagePath(), attributesRequest.getRepositoryFormat(),
          attributesRequest.getTag());
    }
    ArtifactoryArtifactDelegateResponse artifactoryArtifactDelegateResponse =
        ArtifactoryRequestResponseMapper.toArtifactoryResponse(lastSuccessfulBuild, attributesRequest);

    return getSuccessTaskExecutionResponse(Collections.singletonList(artifactoryArtifactDelegateResponse));
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(ArtifactoryArtifactDelegateRequest attributesRequest) {
    List<BuildDetailsInternal> builds = artifactoryRegistryService.getBuilds(
        ArtifactoryRequestResponseMapper.toArtifactoryInternalConfig(attributesRequest),
        attributesRequest.getRepository(), attributesRequest.getImagePath(), attributesRequest.getRepositoryFormat(),
        ArtifactoryRegistryService.MAX_NO_OF_TAGS_PER_IMAGE);
    List<ArtifactoryArtifactDelegateResponse> artifactoryArtifactDelegateResponseList =
        builds.stream()
            .sorted(new BuildDetailsInternalComparatorDescending())
            .map(build -> ArtifactoryRequestResponseMapper.toArtifactoryResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(artifactoryArtifactDelegateResponseList);
  }

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(ArtifactoryArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = artifactoryRegistryService.validateCredentials(
        ArtifactoryRequestResponseMapper.toArtifactoryInternalConfig(attributesRequest));
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<ArtifactoryArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(ArtifactoryArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getTagRegex());
  }

  public void decryptRequestDTOs(ArtifactoryArtifactDelegateRequest artifactoryRequest) {
    if (artifactoryRequest.getArtifactoryConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(artifactoryRequest.getArtifactoryConnectorDTO().getAuth().getCredentials(),
          artifactoryRequest.getEncryptedDataDetails());
    }
  }
}
