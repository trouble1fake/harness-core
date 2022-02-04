/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.beans.recommendation;

import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties
@Schema(name = "FilterValues",
    description = "The applicable 'columns' values are 'name', 'resourceType', 'namespace', 'clusterName'")
public class FilterValuesDTO {
  List<String> columns;
  K8sRecommendationFilterDTO filter;
}
