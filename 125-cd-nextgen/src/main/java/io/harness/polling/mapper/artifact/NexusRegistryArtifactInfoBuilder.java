/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.NexusRegistryArtifactInfo;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

@OwnedBy(HarnessTeam.CDP)
public class NexusRegistryArtifactInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return NexusRegistryArtifactInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .repositoryName(pollingPayloadData.getNexusRegistryPayload().getRepository())
        .imagePath(pollingPayloadData.getNexusRegistryPayload().getImagePath())
        .repositoryFormat(pollingPayloadData.getNexusRegistryPayload().getFormat())
        .build();
  }
}
