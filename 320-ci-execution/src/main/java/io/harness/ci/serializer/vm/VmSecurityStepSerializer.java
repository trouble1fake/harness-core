/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveJsonNodeMapParameter;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.common.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class VmSecurityStepSerializer {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject ConnectorUtils connectorUtils;

  public VmPluginStep serialize(SecurityStepInfo securityStepInfo, String identifier,
      ParameterField<Timeout> parameterFieldTimeout, String stepName, Ambiance ambiance) {
    Map<String, JsonNode> settings =
        resolveJsonNodeMapParameter("settings", "Security", identifier, securityStepInfo.getSettings(), false);
    Map<String, String> envVars = new HashMap<>();
    if (!isEmpty(settings)) {
      for (Map.Entry<String, JsonNode> entry : settings.entrySet()) {
        String key = PLUGIN_ENV_PREFIX + entry.getKey().toUpperCase();
        envVars.put(key, SerializerUtils.convertJsonNodeToString(entry.getKey(), entry.getValue()));
      }
    }
    if (!isEmpty(securityStepInfo.getEnvVariables())) {
      envVars.putAll(securityStepInfo.getEnvVariables());
    }

    String connectorIdentifier = RunTimeInputHandler.resolveStringParameter(
        "connectorRef", stepName, identifier, securityStepInfo.getConnectorRef(), false);

    String image =
        RunTimeInputHandler.resolveStringParameter("Image", stepName, identifier, securityStepInfo.getImage(), false);

    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, securityStepInfo.getDefaultTimeout());

    VmPluginStepBuilder pluginStepBuilder =
        VmPluginStep.builder().image(image).envVariables(envVars).timeoutSecs(timeout);

    // if the plugin type is git clone use default harnessImage Connector
    // else if the connector is given in plugin, use that.
    if (identifier.equals(GIT_CLONE_STEP_ID) && securityStepInfo.isHarnessManagedImage()) {
      String gitImage = ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getGitClone();
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      ConnectorDetails connectorDetails = connectorUtils.getDefaultInternalConnector(ngAccess);
      image = IntegrationStageUtils.getFullyQualifiedImageName(gitImage, connectorDetails);
      pluginStepBuilder.image(image);
      pluginStepBuilder.imageConnector(connectorDetails);
    } else if (!StringUtils.isEmpty(image) && !StringUtils.isEmpty(connectorIdentifier)) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
      pluginStepBuilder.imageConnector(connectorDetails);
    }

    if (securityStepInfo.getReports() != null) {
      if (securityStepInfo.getReports().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) securityStepInfo.getReports().getSpec();
        List<String> resolvedReport = junitTestReport.resolve(identifier, stepName);

        pluginStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }
    return pluginStepBuilder.build();
  }
}
