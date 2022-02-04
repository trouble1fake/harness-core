/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf.apprenaming;

import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues.CfInBuiltVariablesUpdateValuesBuilder;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConstants;

import java.util.List;
import java.util.TreeMap;
import org.cloudfoundry.operations.applications.ApplicationSummary;

/**
 * Before renaming the system will have following apps:-
 * OrderService_0
 * OrderService_INACTIVE
 * OrderService
 * OrderService_STAGE
 * <p>
 * After renaming
 * --------------
 * OrderService_0           -->   OrderService_0
 * OrderService_1           -->   OrderService_1
 * OrderService             -->   OrderService_INACTIVE
 * OrderService_INACTIVE    -->   OrderService
 * <p>
 * The app should be renamed in these order
 * OrderService             --> OrderService_interim
 * OrderService_INACTIVE    --> OrderService
 * OrderService_interim     --> OrderService_INACTIVE
 */
public class NonVersionToNonVersionOperator implements AppRenamingOperator {
  @Override
  public CfInBuiltVariablesUpdateValues renameApp(CfRouteUpdateRequestConfigData cfRouteUpdateConfigData,
      CfRequestConfig cfRequestConfig, LogCallback executionLogCallback, CfDeploymentManager pcfDeploymentManager,
      PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper) throws PivotalClientApiException {
    CfInBuiltVariablesUpdateValuesBuilder updateValuesBuilder = CfInBuiltVariablesUpdateValues.builder();
    String cfAppNamePrefix = cfRouteUpdateConfigData.getCfAppNamePrefix();
    List<ApplicationSummary> allReleases = pcfDeploymentManager.getPreviousReleases(cfRequestConfig, cfAppNamePrefix);

    TreeMap<AppType, AppRenamingData> appTypeApplicationSummaryMap =
        getAppsInTheRenamingOrder(cfRouteUpdateConfigData, allReleases);

    if (!appTypeApplicationSummaryMap.containsKey(AppType.ACTIVE)) {
      // first deployment in non-version -> non-version
      ApplicationSummary applicationSummary = appTypeApplicationSummaryMap.get(AppType.NEW).getAppSummary();
      pcfCommandTaskBaseHelper.renameApp(applicationSummary, cfRequestConfig, executionLogCallback, cfAppNamePrefix);
      updateValuesBuilder.newAppGuid(applicationSummary.getId());
      updateValuesBuilder.newAppName(cfAppNamePrefix);
      return updateValuesBuilder.build();
    }

    ApplicationSummary currentActiveApplicationSummary =
        appTypeApplicationSummaryMap.get(AppType.ACTIVE).getAppSummary();
    String intermediateName = PcfConstants.generateInterimAppName(cfAppNamePrefix);
    pcfCommandTaskBaseHelper.renameApp(
        currentActiveApplicationSummary, cfRequestConfig, executionLogCallback, intermediateName);

    ApplicationSummary newApplicationSummary = appTypeApplicationSummaryMap.get(AppType.NEW).getAppSummary();
    pcfCommandTaskBaseHelper.renameApp(newApplicationSummary, cfRequestConfig, executionLogCallback, cfAppNamePrefix);

    String inActiveName = cfAppNamePrefix + PcfConstants.INACTIVE_APP_NAME_SUFFIX;
    pcfCommandTaskBaseHelper.renameApp(
        currentActiveApplicationSummary, cfRequestConfig, executionLogCallback, inActiveName, intermediateName);

    updateValuesBuilder.newAppGuid(newApplicationSummary.getId());
    updateValuesBuilder.newAppName(cfAppNamePrefix);
    updateValuesBuilder.oldAppGuid(currentActiveApplicationSummary.getId());
    updateValuesBuilder.oldAppName(inActiveName);

    return updateValuesBuilder.build();
  }
}
