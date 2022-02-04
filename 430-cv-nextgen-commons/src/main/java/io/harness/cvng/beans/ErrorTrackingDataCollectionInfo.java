/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.delegate.beans.connector.errortracking.ErrorTrackingConnectorDTO;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class ErrorTrackingDataCollectionInfo extends LogDataCollectionInfo<ErrorTrackingConnectorDTO> {
  private String serviceId;
  private String environmentId;
  private String versionId;

  @Override
  public Map<String, Object> getDslEnvVariables(ErrorTrackingConnectorDTO overOpsConnectorDTO) {
    Map<String, Object> map = new HashMap<>();
    map.put("sid", overOpsConnectorDTO.getSid());
    map.put("versionId", versionId == null ? "" : versionId);
    map.put("serviceId", serviceId);
    map.put("environmentId", environmentId);
    return map;
  }

  @Override
  public String getBaseUrl(ErrorTrackingConnectorDTO overOpsConnectorDTO) {
    String url = overOpsConnectorDTO.getUrl();
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "dashboard-api/eventlog";
    return url;
  }

  @Override
  public Map<String, String> collectionHeaders(ErrorTrackingConnectorDTO overOpsConnectorDTO) {
    Map<String, String> headers = new HashMap<>();
    headers.put("X-API-Key", new String(overOpsConnectorDTO.getApiKeyRef().getDecryptedValue()));
    return headers;
  }

  @Override
  public Map<String, String> collectionParams(ErrorTrackingConnectorDTO overOpsConnectorDTO) {
    return Collections.emptyMap();
  }
}
