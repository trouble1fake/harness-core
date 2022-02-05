/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.OutputNGVariable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CIStepInfoUtils {
  public static String getPluginCustomStepImage(
      PluginCompatibleStep step, CIExecutionServiceConfig ciExecutionServiceConfig, Type infraType) {
    if (infraType == Type.K8) {
      return getK8PluginCustomStepImageConfig(step, ciExecutionServiceConfig).getImage();
    } else if (infraType == Type.VM) {
      return getVmPluginCustomStepImageConfig(step, ciExecutionServiceConfig);
    }
    return null;
  }

  public static ParameterField<Boolean> getPrivilegedMode(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        return ((SecurityStepInfo) step).getPrivileged();
      default:
        return null;
    }
  }

  public static List<String> getOutputVariables(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        List<OutputNGVariable> outputVars = ((SecurityStepInfo) step).getOutputVariables();

        if (isNotEmpty(outputVars)) {
          return outputVars.stream().map(OutputNGVariable::getName).collect(Collectors.toList());
        }

        return Collections.emptyList();
      default:
        return Collections.emptyList();
    }
  }

  public static ParameterField<ImagePullPolicy> getImagePullPolicy(PluginCompatibleStep step) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case SECURITY:
        return ((SecurityStepInfo) step).getImagePullPolicy();
      default:
        return null;
    }
  }

  public static List<String> getK8PluginCustomStepEntrypoint(
      PluginCompatibleStep step, CIExecutionServiceConfig ciExecutionServiceConfig) {
    return getK8PluginCustomStepImageConfig(step, ciExecutionServiceConfig).getEntrypoint();
  }

  private static StepImageConfig getK8PluginCustomStepImageConfig(
      PluginCompatibleStep step, CIExecutionServiceConfig ciExecutionServiceConfig) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case DOCKER:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushDockerRegistryConfig();
      case GCR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushGCRConfig();
      case ECR:
        return ciExecutionServiceConfig.getStepConfig().getBuildAndPushECRConfig();
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        return ciExecutionServiceConfig.getStepConfig().getCacheS3Config();
      case UPLOAD_S3:
        return ciExecutionServiceConfig.getStepConfig().getS3UploadConfig();
      case UPLOAD_GCS:
        return ciExecutionServiceConfig.getStepConfig().getGcsUploadConfig();
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        return ciExecutionServiceConfig.getStepConfig().getCacheGCSConfig();
      case SECURITY:
        return ciExecutionServiceConfig.getStepConfig().getSecurityConfig();
      case UPLOAD_ARTIFACTORY:
        return ciExecutionServiceConfig.getStepConfig().getArtifactoryUploadConfig();
      default:
        throw new IllegalStateException("Unexpected value: " + step.getStepType().getType());
    }
  }

  private static String getVmPluginCustomStepImageConfig(
      PluginCompatibleStep step, CIExecutionServiceConfig ciExecutionServiceConfig) {
    switch (step.getNonYamlInfo().getStepInfoType()) {
      case DOCKER:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getBuildAndPushDockerRegistry();
      case GCR:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getBuildAndPushGCR();
      case ECR:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getBuildAndPushECR();
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getCacheS3();
      case UPLOAD_S3:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getS3Upload();
      case UPLOAD_GCS:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getGcsUpload();
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getCacheGCS();
      case SECURITY:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getSecurity();
      case UPLOAD_ARTIFACTORY:
        return ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getArtifactoryUpload();
      default:
        throw new IllegalStateException("Unexpected value: " + step.getStepType().getType());
    }
  }
}
