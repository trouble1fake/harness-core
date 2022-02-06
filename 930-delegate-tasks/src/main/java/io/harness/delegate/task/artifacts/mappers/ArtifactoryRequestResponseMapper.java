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

import lombok.experimental.UtilityClass;

@UtilityClass
public class ArtifactoryRequestResponseMapper {
  public ArtifactoryConfigRequest toArtifactoryInternalConfig(ArtifactoryArtifactDelegateRequest request) {
    char[] password = "".toCharArray();
    String username = "";
    boolean hasCredentials = false;
    if (request.getArtifactoryConnectorDTO().getAuth() != null
        && request.getArtifactoryConnectorDTO().getAuth().getCredentials() != null) {
      ArtifactoryUsernamePasswordAuthDTO credentials =
          (ArtifactoryUsernamePasswordAuthDTO) request.getArtifactoryConnectorDTO().getAuth().getCredentials();
      if (credentials.getPasswordRef() != null) {
        password = EmptyPredicate.isNotEmpty(credentials.getPasswordRef().getDecryptedValue())
            ? credentials.getPasswordRef().getDecryptedValue()
            : null;
      }
      username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          credentials.getUsername(), credentials.getUsernameRef());
      hasCredentials = true;
    }
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl(request.getArtifactoryConnectorDTO().getArtifactoryServerUrl())
        .username(username)
        .password(password)
        .hasCredentials(hasCredentials)
        .artifactRepositoryUrl(request.getArtifactRepositoryUrl())
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
}
