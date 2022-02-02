/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedbackDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveQueryDTO;

import java.util.List;
import lombok.NonNull;

public interface AnomalyService {
  List<AnomalyData> listAnomalies(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery);
  List<PerspectiveAnomalyData> listPerspectiveAnomalies(
      @NonNull String accountIdentifier, PerspectiveQueryDTO perspectiveQuery);
  Boolean updateAnomalyFeedback(@NonNull String accountIdentifier, String anomalyId, AnomalyFeedbackDTO feedback);
}
