/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Value;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@Value
@Builder
@OwnedBy(CDP)
public class ArtifactoryStoreDelegateConfig implements StoreDelegateConfig {
    String repositoryPath;
    String artifactName;
    String version;

    //??????//
    ArtifactoryConnectorDTO artifactoryConnector;
    List<EncryptedDataDetail> encryptedDataDetails;
    List<EncryptedDataDetail> apiAuthEncryptedDataDetails;
    String manifestType;
    String manifestId;

    @Override
    public StoreDelegateConfigType getType() {
        return StoreDelegateConfigType.ARTIFACTORY;
    }
}
