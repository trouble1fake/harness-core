/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.NexusRequestResponseMapper;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.nexus.service.NexusRegistryService;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class ArtifactoryArtifactTaskHandler extends DelegateArtifactTaskHandler<NexusArtifactDelegateRequest> {
  private final NexusRegistryService nexusRegistryService;
  private final SecretDecryptionService secretDecryptionService;

  //  @Override
  //  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(NexusArtifactDelegateRequest attributesRequest) {
  //    BuildDetailsInternal lastSuccessfulBuild;
  //    if (isRegex(attributesRequest)) {
  //      lastSuccessfulBuild = dockerRegistryService.getLastSuccessfulBuildFromRegex(
  //          DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest), attributesRequest.getImagePath(),
  //          attributesRequest.getTagRegex());
  //    } else {
  //      lastSuccessfulBuild =
  //          dockerRegistryService.verifyBuildNumber(DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest),
  //              attributesRequest.getImagePath(), attributesRequest.getTag());
  //    }
  //    DockerArtifactDelegateResponse dockerArtifactDelegateResponse =
  //        DockerRequestResponseMapper.toDockerResponse(lastSuccessfulBuild, attributesRequest);
  //    return getSuccessTaskExecutionResponse(Collections.singletonList(dockerArtifactDelegateResponse));
  //  }
  //
  @Override
  public ArtifactTaskExecutionResponse getBuilds(NexusArtifactDelegateRequest attributesRequest) {
    List<BuildDetailsInternal> builds = nexusRegistryService.getBuilds(
        NexusRequestResponseMapper.toNexusInternalConfig(attributesRequest), attributesRequest.getRepositoryName(),
        attributesRequest.getRepositoryPort(), attributesRequest.getImagePath(),
        attributesRequest.getRepositoryFormat(), NexusRegistryService.MAX_NO_OF_TAGS_PER_IMAGE);
    List<NexusArtifactDelegateResponse> nexusArtifactDelegateResponseList =
        builds.stream()
            .sorted(new BuildDetailsInternalComparatorDescending())
            .map(build -> NexusRequestResponseMapper.toNexusResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(nexusArtifactDelegateResponseList);
  }
  //
  //  @Override
  //  public ArtifactTaskExecutionResponse getLabels(DockerArtifactDelegateRequest attributesRequest) {
  //    List<Map<String, String>> labels =
  //        dockerRegistryService.getLabels(DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest),
  //            attributesRequest.getImagePath(), attributesRequest.getTagsList());
  //    return getSuccessTaskExecutionResponse(DockerRequestResponseMapper.toDockerResponse(labels, attributesRequest));
  //  }
  //
  //  @Override
  //  public ArtifactTaskExecutionResponse validateArtifactServer(DockerArtifactDelegateRequest attributesRequest) {
  //    boolean isServerValidated = dockerRegistryService.validateCredentials(
  //        DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest));
  //    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  //  }
  //
  //  @Override
  //  public ArtifactTaskExecutionResponse validateArtifactImage(DockerArtifactDelegateRequest attributesRequest) {
  //    boolean isArtifactImageValid = dockerRegistryService.verifyImageName(
  //        DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest), attributesRequest.getImagePath());
  //    return ArtifactTaskExecutionResponse.builder().isArtifactSourceValid(isArtifactImageValid).build();
  //  }
  //
  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<NexusArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(NexusArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getTagRegex());
  }

  public void decryptRequestDTOs(NexusArtifactDelegateRequest nexusRequest) {
    if (nexusRequest.getNexusConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(
          nexusRequest.getNexusConnectorDTO().getAuth().getCredentials(), nexusRequest.getEncryptedDataDetails());
    }
  }
}
