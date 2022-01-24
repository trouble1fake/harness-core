/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;


import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.List;

@Value
@Builder

public class ArtifactoryCapability implements ExecutionCapability{
    ArtifactoryConnectorDTO artifactoryConnectorDTO;
    List<EncryptedDataDetail> encryptedDataDetails;
    CapabilityType capabilityType = CapabilityType.ARTIFACTORY;

    @Override
    public EvaluationMode evaluationMode() {
        return EvaluationMode.AGENT;
    }

    @Override
    public String fetchCapabilityBasis() {
        return "ARTIFACTORY: "; // ?????
    }

    @Override
    public Duration getMaxValidityPeriod() {
        return Duration.ofHours(6);
    }

    @Override
    public Duration getPeriodUntilNextValidation() {
        return Duration.ofHours(4);
    }
}
