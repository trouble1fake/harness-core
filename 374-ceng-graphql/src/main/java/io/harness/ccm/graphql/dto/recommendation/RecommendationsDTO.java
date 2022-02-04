/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "Recommendations", description = "The Cloud Cost Recommendations list response")
public class RecommendationsDTO {
  List<RecommendationItemDTO> items;

  long offset;
  long limit;
}
