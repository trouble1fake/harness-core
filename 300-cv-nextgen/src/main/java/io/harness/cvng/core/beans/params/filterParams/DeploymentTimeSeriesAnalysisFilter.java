/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params.filterParams;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.util.List;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Data
@NoArgsConstructor
public class DeploymentTimeSeriesAnalysisFilter extends TimeSeriesAnalysisFilter {
  @QueryParam("hostNames") List<String> hostNames;
  @QueryParam("transactionNames") List<String> transactionNames;
  @DefaultValue("false") @QueryParam("anomalousNodesOnly") boolean anomalousNodesOnly;

  public boolean filterByHostNames() {
    return isNotEmpty(hostNames);
  }

  public boolean filterByTransactionNames() {
    return isNotEmpty(transactionNames);
  }
}
