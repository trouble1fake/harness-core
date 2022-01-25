/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import io.harness.connector.ConnectorInfoDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder

public class FileStoreCapability implements ExecutionCapability {
  ConnectorInfoDTO artifactoryConnectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  CapabilityType capabilityType = CapabilityType.FILESTORE;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    return "FILESTORE: "; // ?????
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
