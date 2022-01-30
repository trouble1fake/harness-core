/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.utils.FieldWithPlainTextOrSecretValueHelper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactoryRequestResponseMapper {
  public ArtifactoryConfigRequest toArtifactoryInternalConfig(ArtifactoryArtifactDelegateRequest request) {
    String password = "";
    String username = "";
    boolean hasCredentials = false;
    if (request.getArtifactoryConnectorDTO().getAuth() != null
        && request.getArtifactoryConnectorDTO().getAuth().getCredentials() != null) {
      ArtifactoryUsernamePasswordAuthDTO credentials =
          (ArtifactoryUsernamePasswordAuthDTO) request.getArtifactoryConnectorDTO().getAuth().getCredentials();
      if (credentials.getPasswordRef() != null) {
        password = EmptyPredicate.isNotEmpty(credentials.getPasswordRef().getDecryptedValue())
            ? new String(credentials.getPasswordRef().getDecryptedValue())
            : null;
      }
      username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          credentials.getUsername(), credentials.getUsernameRef());
      hasCredentials = true;
    }
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl(request.getArtifactoryConnectorDTO().getArtifactoryServerUrl())
        .username(username)
        .password(password.toCharArray())
        .hasCredentials(hasCredentials)
        .artifactoryDockerRepositoryServer(request.getDockerRepositoryServer())
        .build();
  }

  public ArtifactoryArtifactDelegateResponse toArtifactoryResponse(
      BuildDetailsInternal buildDetailsInternal, ArtifactoryArtifactDelegateRequest request) {
    return ArtifactoryArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal))
        .imagePath(request.getImagePath())
        .tag(buildDetailsInternal.getNumber())
        .sourceType(ArtifactSourceType.ARTIFACTORY_REGISTRY)
        .build();
  }

  public List<ArtifactoryArtifactDelegateResponse> toArtifactoryResponse(
      List<Map<String, String>> labelsList, ArtifactoryArtifactDelegateRequest request) {
    return IntStream.range(0, request.getTagsList().size())
        .mapToObj(i
            -> ArtifactoryArtifactDelegateResponse.builder()
                   .buildDetails(
                       ArtifactBuildDetailsMapper.toBuildDetailsNG(labelsList.get(i), request.getTagsList().get(i)))
                   .imagePath(request.getImagePath())
                   .sourceType(ArtifactSourceType.ARTIFACTORY_REGISTRY)
                   .build())
        .collect(Collectors.toList());
  }
}
