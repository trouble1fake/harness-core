/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.data.validator.EntityIdentifier;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.schema.YamlSchemaIgnoreSubtype;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.beans.ConstructorProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("liteEngineTask")
@JsonIgnoreProperties(ignoreUnknown = true)
@YamlSchemaIgnoreSubtype
@TypeAlias("liteEngineTaskStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.InitializeStepInfo")
public class InitializeStepInfo implements CIStepInfo, WithConnectorRef {
  public static final int DEFAULT_RETRY = 0;
  public static final int DEFAULT_TIMEOUT = 600 * 1000;
  public static final String CALLBACK_IDS = "callbackIds";
  public static final String LOG_KEYS = "logKeys";

  @JsonIgnore
  public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.INITIALIZE_TASK).build();
  @JsonIgnore
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CIStepInfoType.INITIALIZE_TASK.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @JsonIgnore int timeout = DEFAULT_TIMEOUT;
  @NotNull @EntityIdentifier private String identifier;
  private String name;
  @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @NotNull BuildJobEnvInfo buildJobEnvInfo;
  @NotNull String accountId;
  @NotNull ExecutionElementConfig executionElementConfig;
  CodeBase ciCodebase;
  @NotNull boolean skipGitClone;
  @NotNull Infrastructure infrastructure;

  @Builder
  @ConstructorProperties({"accountId", "timeout", "identifier", "name", "retry", "buildJobEnvInfo",
      "executionElementConfig", "usePVC", "ciCodebase", "skipGitClone", "infrastructure", "runAsUser"})
  public InitializeStepInfo(String accountId, int timeout, String identifier, String name, Integer retry,
      BuildJobEnvInfo buildJobEnvInfo, ExecutionElementConfig executionElementConfig, boolean usePVC,
      CodeBase ciCodebase, boolean skipGitClone, Infrastructure infrastructure) {
    this.accountId = accountId;
    this.timeout = timeout;
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);

    this.buildJobEnvInfo = buildJobEnvInfo;
    this.executionElementConfig = executionElementConfig;
    this.ciCodebase = ciCodebase;
    this.skipGitClone = skipGitClone;
    this.infrastructure = infrastructure;
  }

  @Override
  public TypeInfo getNonYamlInfo() {
    return typeInfo;
  }

  @Override
  public StepType getStepType() {
    return STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public boolean skipUnresolvedExpressionsCheck() {
    return true;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      connectorRefMap.put(
          YAMLFieldNameConstants.CONNECTOR_REF, ((K8sDirectInfraYaml) infrastructure).getSpec().getConnectorRef());
    }

    if (!skipGitClone) {
      connectorRefMap.put(
          YAMLFieldNameConstants.CODEBASE_CONNECTOR_REF, ParameterField.createValueField(ciCodebase.getConnectorRef()));
    }

    return connectorRefMap;
  }
}
