/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.KustomizeCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;

import software.wings.helpers.ext.kustomize.KustomizeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@AllArgsConstructor
@OwnedBy(CDP)
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
public class K8sTaskParameters implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander {
  private String accountId;
  private String appId;
  private String commandName;
  private String activityId;
  private K8sClusterConfig k8sClusterConfig;
  private String workflowExecutionId;
  @Expression(DISALLOW_SECRETS) private String releaseName;
  private Integer timeoutIntervalInMin;
  @NotEmpty private K8sTaskType commandType;
  private HelmVersion helmVersion;
  private Set<String> delegateSelectors;
  private boolean useLatestChartMuseumVersion;
  private boolean useLatestKustomizeVersion;
  private boolean useNewKubectlVersion;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    executionCapabilities.addAll(k8sClusterConfig.fetchRequiredExecutionCapabilities(maskingEvaluator));
    if (kustomizeValidationNeeded()) {
      executionCapabilities.add(
          KustomizeCapability.builder()
              .pluginRootDir(fetchKustomizeConfig((ManifestAwareTaskParams) this).getPluginRootDir())
              .build());
    }
    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(SelectorCapability.builder().selectors(delegateSelectors).build());
    }

    return executionCapabilities;
  }

  private boolean kustomizeValidationNeeded() {
    if (this instanceof ManifestAwareTaskParams) {
      return fetchKustomizeConfig((ManifestAwareTaskParams) this) != null;
    }
    return false;
  }

  private KustomizeConfig fetchKustomizeConfig(ManifestAwareTaskParams taskParams) {
    return taskParams.getK8sDelegateManifestConfig() != null
        ? taskParams.getK8sDelegateManifestConfig().getKustomizeConfig()
        : null;
  }
}
