/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorValidationParams;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CV)
public class CVConnectorParamsProvider implements ConnectorValidationParamsProvider {
  @Inject EncryptionHelper encryptionHelper;

  @Override
  public ConnectorValidationParams getConnectorValidationParams(ConnectorInfoDTO connectorInfoDTO, String connectorName,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final List<DecryptableEntity> decryptableEntityList =
        connectorInfoDTO.getConnectorConfig().getDecryptableEntities();
    final List<List<EncryptedDataDetail>> encryptionDetails = new ArrayList<>();

    if (isNotEmpty(decryptableEntityList)) {
      decryptableEntityList.forEach(decryptableEntity
          -> encryptionDetails.add(encryptionHelper.getEncryptionDetail(
              decryptableEntity, accountIdentifier, orgIdentifier, projectIdentifier)));
    }

    return CVConnectorValidationParams.builder()
        .connectorConfigDTO(connectorInfoDTO.getConnectorConfig())
        .connectorName(connectorName)
        .encryptedDataDetails(encryptionDetails)
        .connectorType(connectorInfoDTO.getConnectorType())
        .build();
  }
}
