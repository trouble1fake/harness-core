/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.*;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.capability.ProcessExecutionCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifactory.ArtifactoryFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

@Value
@Builder
@Slf4j
@OwnedBy(CDP)
public class TerraformTaskNGParameters
    implements TaskParameters, ExecutionCapabilityDemander, NestedAnnotationResolver {
  @NonNull String accountId;
  String currentStateFileId;

  @NonNull TFTaskType taskType;
  @NonNull String entityId;
  String workspace;
  GitFetchFilesConfig configFile;
  ArtifactoryFetchFilesConfig fileStoreConfigFiles;
  @Expression(ALLOW_SECRETS) List<TerraformVarFileInfo> varFileInfos;
  @Expression(ALLOW_SECRETS) String backendConfig;
  @Expression(DISALLOW_SECRETS) List<String> targets;
  @Expression(ALLOW_SECRETS) Map<String, String> environmentVariables;
  boolean saveTerraformStateJson;
  long timeoutInMillis;

  // For plan
  TerraformCommand terraformCommand;

  // To aid in logging
  TerraformCommandUnit terraformCommandUnit;

  // For Apply when inheriting from plan
  EncryptionConfig encryptionConfig;
  EncryptedRecordData encryptedTfPlan;
  String planName;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (configFile != null) {
    capabilities = ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerraform(
        configFile.getGitStoreDelegateConfig().getEncryptedDataDetails(), maskingEvaluator);
    log.info("Adding Required Execution Capabilities for GitStores");
      capabilities.add(GitConnectionNGCapability.builder()
                           .gitConfig((GitConfigDTO) configFile.getGitStoreDelegateConfig().getGitConfigDTO())
                           .encryptedDataDetails(configFile.getGitStoreDelegateConfig().getEncryptedDataDetails())
                           .sshKeySpecDTO(configFile.getGitStoreDelegateConfig().getSshKeySpecDTO())
                           .build());

      GitConfigDTO gitConfigDTO = (GitConfigDTO) configFile.getGitStoreDelegateConfig().getGitConfigDTO();
      if (isNotEmpty(gitConfigDTO.getDelegateSelectors())) {
        capabilities.add(SelectorCapability.builder().selectors(gitConfigDTO.getDelegateSelectors()).build());
      }
    }
    if (fileStoreConfigFiles != null) {
      capabilities.addAll(ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerraform(
          fileStoreConfigFiles.getArtifactoryStoreDelegateConfig().getEncryptedDataDetails(), maskingEvaluator));
      log.info("Adding Required Execution Capabilities for ArtifactoryStores");
        capabilities.add(ArtifactoryCapability.builder()
                .artifactoryConnectorDTO(fileStoreConfigFiles.getArtifactoryStoreDelegateConfig().getArtifactoryConnector())
                .encryptedDataDetails(fileStoreConfigFiles.getArtifactoryStoreDelegateConfig().getEncryptedDataDetails())
                .build());
    }
    if (isNotEmpty(varFileInfos)) {
      for (TerraformVarFileInfo varFileInfo : varFileInfos) {
        if (varFileInfo instanceof RemoteTerraformVarFileInfo) {
          GitFetchFilesConfig gitFetchFilesConfig = ((RemoteTerraformVarFileInfo) varFileInfo).getGitFetchFilesConfig();
          if (gitFetchFilesConfig != null) {
            capabilities.add(
                    GitConnectionNGCapability.builder()
                            .gitConfig((GitConfigDTO) gitFetchFilesConfig.getGitStoreDelegateConfig().getGitConfigDTO())
                            .encryptedDataDetails(gitFetchFilesConfig.getGitStoreDelegateConfig().getEncryptedDataDetails())
                            .sshKeySpecDTO(gitFetchFilesConfig.getGitStoreDelegateConfig().getSshKeySpecDTO())
                            .build());

            GitConfigDTO gitConfigDTO = (GitConfigDTO) gitFetchFilesConfig.getGitStoreDelegateConfig().getGitConfigDTO();
            if (isNotEmpty(gitConfigDTO.getDelegateSelectors())) {
              capabilities.add(SelectorCapability.builder().selectors(gitConfigDTO.getDelegateSelectors()).build());
            }
          }
          ArtifactoryFetchFilesConfig artifactoryFetchFilesConfig = ((RemoteTerraformVarFileInfo) varFileInfo).getArtifactoryFetchFilesConfig();
          if (artifactoryFetchFilesConfig != null) {
            capabilities.add(ArtifactoryCapability.builder()
                    .artifactoryConnectorDTO(artifactoryFetchFilesConfig.getArtifactoryStoreDelegateConfig().getArtifactoryConnector())
                    .encryptedDataDetails(artifactoryFetchFilesConfig.getArtifactoryStoreDelegateConfig().getEncryptedDataDetails())
                    .build());

            ArtifactoryConnectorDTO artifactoryConnectorDTO = artifactoryFetchFilesConfig.getArtifactoryStoreDelegateConfig().getArtifactoryConnector();
            if (isNotEmpty(artifactoryConnectorDTO.getDelegateSelectors())) {
              capabilities.add(SelectorCapability.builder().selectors(artifactoryConnectorDTO.getDelegateSelectors()).build());
            }
          }
        }
      }
    }
    if (encryptionConfig != null) {
      capabilities.addAll(
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(encryptionConfig, null));
    }
    return capabilities;
  }
}
