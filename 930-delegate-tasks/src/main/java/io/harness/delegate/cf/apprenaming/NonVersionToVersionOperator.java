/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf.apprenaming;

import static io.harness.pcf.model.PcfConstants.DELIMITER;

import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues.CfInBuiltVariablesUpdateValuesBuilder;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.cloudfoundry.operations.applications.ApplicationSummary;

/**
 * Before renaming the system will have following apps:-
 * OrderService_0
 * OrderService_INACTIVE
 * OrderService
 *
 * After renaming
 * --------------
 * OrderService_0           -->   OrderService_0
 * OrderService_1           -->   OrderService_1 (Renamed during App Setup)
 * OrderService             -->   OrderService_2
 * OrderService_INACTIVE    -->   OrderService_3
 *
 * The app should be renamed in these order  --> ACTIVE --> NEW
 * OrderService             -->   OrderService_2
 * OrderService_INACTIVE    -->   OrderService_3
 */

public class NonVersionToVersionOperator implements AppRenamingOperator {
  @Override
  public CfInBuiltVariablesUpdateValues renameApp(CfRouteUpdateRequestConfigData cfRouteUpdateConfigData,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, CfDeploymentManager pcfDeploymentManager,
      PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper) throws PivotalClientApiException {
    CfInBuiltVariablesUpdateValuesBuilder updateValuesBuilder = CfInBuiltVariablesUpdateValues.builder();
    String cfAppNamePrefix = cfRouteUpdateConfigData.getCfAppNamePrefix();
    List<ApplicationSummary> allReleases = pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfAppNamePrefix);
    int maxVersion = PcfCommandTaskBaseHelper.getMaxVersion(allReleases);

    TreeMap<AppType, AppRenamingData> appTypeApplicationSummaryMap =
        getAppsInTheRenamingOrder(cfRouteUpdateConfigData, allReleases);

    int versionNumber = maxVersion;
    Set<Map.Entry<AppType, AppRenamingData>> entries = appTypeApplicationSummaryMap.entrySet();
    for (Map.Entry<AppType, AppRenamingData> entry : entries) {
      ApplicationSummary applicationSummary = entry.getValue().getAppSummary();
      String appNewName = cfAppNamePrefix + DELIMITER + ++versionNumber;
      pcfCommandTaskBaseHelper.renameApp(applicationSummary, cfRequestConfig, executionLogCallback, appNewName);

      if (AppType.NEW == entry.getKey()) {
        updateValuesBuilder.newAppGuid(applicationSummary.getId());
        updateValuesBuilder.newAppName(appNewName);
      } else if (AppType.ACTIVE == entry.getKey()) {
        updateValuesBuilder.oldAppGuid(applicationSummary.getId());
        updateValuesBuilder.oldAppName(appNewName);
      }
    }
    return updateValuesBuilder.build();
  }
}
