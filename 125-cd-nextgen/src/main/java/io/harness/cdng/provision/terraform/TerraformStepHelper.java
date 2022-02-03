/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.provision.TerraformConstants.TF_DESTROY_NAME_PREFIX;
import static io.harness.provision.TerraformConstants.TF_NAME_PREFIX;
import static io.harness.validation.Validator.notEmptyCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.terraform.TerraformConfig.TerraformConfigBuilder;
import io.harness.cdng.provision.terraform.TerraformConfig.TerraformConfigKeys;
import io.harness.cdng.provision.terraform.TerraformInheritOutput.TerraformInheritOutputBuilder;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.artifactory.ArtifactoryFile;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.RestClientUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.validation.Validator;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TerraformStepHelper {
  private static final String INHERIT_OUTPUT_FORMAT = "tfInheritOutput_%s";
  private static final String TF_INHERIT_OUTPUT_FORMAT = "tfInheritOutput_%s_%s";
  public static final String TF_CONFIG_FILES = "TF_CONFIG_FILES";
  public static final String TF_VAR_FILES = "TF_VAR_FILES_%d";

  @Inject private HPersistence persistence;
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Inject private FileServiceClientFactory fileService;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject public TerraformConfigDAL terraformConfigDAL;

  public static List<EntityDetail> prepareEntityDetailsForVarFiles(
      String accountId, String orgIdentifier, String projectIdentifier, Map<String, TerraformVarFile> varFiles) {
    List<EntityDetail> entityDetailList = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(varFiles)) {
      for (Map.Entry<String, TerraformVarFile> varFileEntry : varFiles.entrySet()) {
        if (varFileEntry.getValue().getType().equals(TerraformVarFileTypes.Remote)) {
          String connectorRef = ((RemoteTerraformVarFileSpec) varFileEntry.getValue().getSpec())
                                    .getStore()
                                    .getSpec()
                                    .getConnectorReference()
                                    .getValue();
          IdentifierRef identifierRef =
              IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
          EntityDetail entityDetail =
              EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
          entityDetailList.add(entityDetail);
        }
      }
    }

    return entityDetailList;
  }

  public String generateFullIdentifier(String provisionerIdentifier, Ambiance ambiance) {
    if (Pattern.matches(NGRegexValidatorConstants.IDENTIFIER_PATTERN, provisionerIdentifier)) {
      return format("%s/%s/%s/%s", AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), provisionerIdentifier);
    } else {
      throw new InvalidRequestException(
          format("Provisioner Identifier cannot contain special characters or spaces: [%s]", provisionerIdentifier));
    }
  }

  private void validateGitStoreConfig(GitStoreConfig gitStoreConfig) {
    Validator.notNullCheck("Git Store Config is null", gitStoreConfig);
    FetchType gitFetchType = gitStoreConfig.getGitFetchType();
    switch (gitFetchType) {
      case BRANCH:
        Validator.notEmptyCheck("Branch is Empty in Git Store config",
            ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getBranch()));
        break;
      case COMMIT:
        Validator.notEmptyCheck("Commit Id is Empty in Git Store config",
            ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getCommitId()));
        break;
      default:
        throw new InvalidRequestException(format("Unrecognized git fetch type: [%s]", gitFetchType.name()));
    }
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(StoreConfig store, Ambiance ambiance, String identifier) {
    if (store == null || !ManifestStoreType.isInGitSubset(store.getKind())) {
      return null;
    }
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    validateGitStoreConfig(gitStoreConfig);
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = k8sStepHelper.getConnector(connectorId, ambiance);
    String validationMessage = "";
    if (identifier.equals(TerraformStepHelper.TF_CONFIG_FILES)) {
      validationMessage = "Config Files";
    } else {
      validationMessage = format("Var Files with identifier: %s", identifier);
    }
    // TODO: fix manifest part, remove k8s dependency
    k8sStepHelper.validateManifest(store.getKind(), connectorDTO, validationMessage);
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    SSHKeySpecDTO sshKeySpecDTO =
        gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, basicNGAccessObject);
    String repoName = gitStoreConfig.getRepoName() != null ? gitStoreConfig.getRepoName().getValue() : null;
    if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT) {
      String repoUrl = getGitRepoUrl(gitConfigDTO, repoName);
      gitConfigDTO.setUrl(repoUrl);
      gitConfigDTO.setGitConnectionType(GitConnectionType.REPO);
    }
    List<String> paths = new ArrayList<>();
    if (TF_CONFIG_FILES.equals(identifier)) {
      paths.add(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getFolderPath()));
    } else {
      paths.addAll(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getPaths()));
    }
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .sshKeySpecDTO(sshKeySpecDTO)
                                                        .encryptedDataDetails(encryptedDataDetails)
                                                        .fetchType(gitStoreConfig.getGitFetchType())
                                                        .branch(getParameterFieldValue(gitStoreConfig.getBranch()))
                                                        .commitId(getParameterFieldValue(gitStoreConfig.getCommitId()))
                                                        .paths(paths)
                                                        .connectorName(connectorDTO.getName())
                                                        .build();

    return GitFetchFilesConfig.builder()
        .identifier(identifier)
        .succeedIfFileNotFound(false)
        .gitStoreDelegateConfig(gitStoreDelegateConfig)
        .build();
  }

  public FileStoreFetchFilesConfig getFileFactoryFetchFilesConfig(
      StoreConfig store, Ambiance ambiance, String identifier) {
    if (store == null || !ManifestStoreType.ARTIFACTORY.equals(store.getKind())) {
      return null;
    }
    ArtifactoryStoreConfig artifactoryStoreConfig = (ArtifactoryStoreConfig) store;
    validateArtifactoryStoreConfig(artifactoryStoreConfig);
    String connectorId = artifactoryStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = k8sStepHelper.getConnector(connectorId, ambiance);
    String validationMessage = "";
    if (identifier.equals(TerraformStepHelper.TF_CONFIG_FILES)) {
      validationMessage = "Config Files";
    } else {
      validationMessage = format("Var Files with identifier: %s", identifier);
    }
    k8sStepHelper.validateManifest(store.getKind(), connectorDTO, validationMessage);
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    List<EncryptedDataDetail> encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
        basicNGAccessObject, ((ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig()).getAuth().getCredentials());
    return ArtifactoryStoreDelegateConfig.builder()
        .repositoryPath(artifactoryStoreConfig.getRepositoryPath().getValue())
        .identifier(identifier)
        .manifestStoreType(store.getKind())
        .connectorDTO(connectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .succeedIfFileNotFound(false)
        .artifacts(artifactoryStoreConfig.getArtifacts()
                       .stream()
                       .map(a
                           -> ArtifactoryFile.builder()
                                  .name(a.getArtifactFile().getName().getValue())
                                  .path(a.getArtifactFile().getPath().getValue())
                                  .build())
                       .collect(Collectors.toList()))
        .build();
  }

  private void validateArtifactoryStoreConfig(ArtifactoryStoreConfig artifactoryStoreConfig) {
    Validator.notNullCheck("artifactoryStoreConfig is null", artifactoryStoreConfig);
  }

  private String getGitRepoUrl(GitConfigDTO gitConfigDTO, String repoName) {
    repoName = trimToEmpty(repoName);
    notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
    String purgedRepoUrl = gitConfigDTO.getUrl().replaceAll("/*$", "");
    String purgedRepoName = repoName.replaceAll("^/*", "");
    return purgedRepoUrl + "/" + purgedRepoName;
  }

  public TerraformInheritOutput getSavedInheritOutput(String provisionerIdentifier, String command, Ambiance ambiance) {
    String fullEntityId = generateFullIdentifier(provisionerIdentifier, ambiance);
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(format(TF_INHERIT_OUTPUT_FORMAT, command, fullEntityId)));
    if (!output.isFound()) {
      // This is for backward compatibility as after we release this, there may be IN PROGRESS workflows which saved
      // inherit output  with old name. To be removed after some time.
      output = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(format(INHERIT_OUTPUT_FORMAT, fullEntityId)));
    }
    if (!output.isFound()) {
      throw new InvalidRequestException(
          format("Did not find any Plan step for provisioner identifier: [%s]", provisionerIdentifier));
    }

    return (TerraformInheritOutput) output.getOutput();
  }

  public void saveTerraformInheritOutput(TerraformPlanStepParameters planStepParameters,
      TerraformTaskNGResponse terraformTaskNGResponse, Ambiance ambiance) {
    validatePlanStepConfigFiles(planStepParameters);
    TerraformPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();
    TerraformInheritOutputBuilder builder = TerraformInheritOutput.builder().workspace(
        ParameterFieldHelper.getParameterFieldValue(configuration.getWorkspace()));
    Map<String, String> commitIdMap = terraformTaskNGResponse.getCommitIdForConfigFilesMap();
    builder
        .configFiles(getStoreConfigAtCommitId(
            configuration.getConfigFiles().getStore().getSpec(), commitIdMap.get(TF_CONFIG_FILES)))
        .varFileConfigs(toTerraformVarFileConfig(configuration.getVarFiles(), terraformTaskNGResponse, ambiance))
        .backendConfig(getBackendConfig(configuration.getBackendConfig()))
        .environmentVariables(getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
        .targets(ParameterFieldHelper.getParameterFieldValue(configuration.getTargets()))
        .encryptedTfPlan(terraformTaskNGResponse.getEncryptedTfPlan())
        .encryptionConfig(getEncryptionConfig(ambiance, planStepParameters))
        .planName(getTerraformPlanName(planStepParameters.getConfiguration().getCommand(), ambiance));
    String fullEntityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);
    String inheritOutputName =
        format(TF_INHERIT_OUTPUT_FORMAT, planStepParameters.getConfiguration().command.name(), fullEntityId);
    executionSweepingOutputService.consume(ambiance, inheritOutputName, builder.build(), StepOutcomeGroup.STAGE.name());
  }

  public String getTerraformPlanName(TerraformPlanCommand terraformPlanCommand, Ambiance ambiance) {
    String prefix = TerraformPlanCommand.DESTROY == terraformPlanCommand ? TF_DESTROY_NAME_PREFIX : TF_NAME_PREFIX;
    return format(prefix, ambiance.getPlanExecutionId()).replaceAll("_", "-");
  }

  public EncryptionConfig getEncryptionConfig(Ambiance ambiance, TerraformPlanStepParameters planStepParameters) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getConfiguration().getSecretManagerRef()),
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));

    return SecretManagerConfigMapper.fromDTO(secretManagerClientService.getSecretManager(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
        identifierRef.getIdentifier(), false));
  }

  public Map<String, String> getEnvironmentVariablesMap(Map<String, Object> inputVariables) {
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.keySet().forEach(
        key -> res.put(key, ((ParameterField<?>) inputVariables.get(key)).getValue().toString()));
    return res;
  }

  private GitStoreConfig getStoreConfigAtCommitId(StoreConfig storeConfig, String commitId) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig.cloneInternal();
    if (EmptyPredicate.isEmpty(commitId) || FetchType.COMMIT == gitStoreConfig.getGitFetchType()) {
      return gitStoreConfig;
    }
    ParameterField<String> commitIdField = ParameterField.createValueField(commitId);
    switch (storeConfig.getKind()) {
      case ManifestStoreType.BITBUCKET: {
        BitbucketStore bitbucketStore = (BitbucketStore) gitStoreConfig;
        bitbucketStore.setGitFetchType(FetchType.COMMIT);
        bitbucketStore.setCommitId(commitIdField);
        break;
      }
      case ManifestStoreType.GITLAB: {
        GitLabStore gitLabStore = (GitLabStore) gitStoreConfig;
        gitLabStore.setGitFetchType(FetchType.COMMIT);
        gitLabStore.setCommitId(commitIdField);
        break;
      }
      case ManifestStoreType.GIT: {
        GitStore gitStore = (GitStore) gitStoreConfig;
        gitStore.setGitFetchType(FetchType.COMMIT);
        gitStore.setCommitId(commitIdField);
        break;
      }
      case ManifestStoreType.GITHUB: {
        GithubStore githubStore = (GithubStore) gitStoreConfig;
        githubStore.setGitFetchType(FetchType.COMMIT);
        githubStore.setCommitId(commitIdField);
        break;
      }
      default: {
        log.warn(format("Unknown store kind: [%s]", storeConfig.getKind()));
        break;
      }
    }
    return gitStoreConfig;
  }

  public void saveRollbackDestroyConfigInherited(TerraformApplyStepParameters stepParameters, Ambiance ambiance) {
    TerraformInheritOutput inheritOutput = getSavedInheritOutput(
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), APPLY.name(), ambiance);

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(
                generateFullIdentifier(getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance))
            .pipelineExecutionId(ambiance.getPlanExecutionId())
            .configFiles(inheritOutput.getConfigFiles().toGitStoreConfigDTO())
            .varFileConfigs(inheritOutput.getVarFileConfigs())
            .backendConfig(inheritOutput.getBackendConfig())
            .environmentVariables(inheritOutput.getEnvironmentVariables())
            .workspace(inheritOutput.getWorkspace())
            .targets(inheritOutput.getTargets())
            .build();

    terraformConfigDAL.saveTerraformConfig(terraformConfig);
  }

  public void validateApplyStepParamsInline(TerraformApplyStepParameters stepParameters) {
    Validator.notNullCheck("Apply Step Parameters are null", stepParameters);
    Validator.notNullCheck("Apply Step configuration is NULL", stepParameters.getConfiguration());
  }

  public void validateApplyStepConfigFilesInline(TerraformApplyStepParameters stepParameters) {
    Validator.notNullCheck("Apply Step Parameters are null", stepParameters);
    Validator.notNullCheck("Apply Step configuration is NULL", stepParameters.getConfiguration());
    TerraformExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    Validator.notNullCheck("Apply Step Spec is NULL", spec);
    Validator.notNullCheck("Apply Step Spec does not have Config files", spec.getConfigFiles());
    Validator.notNullCheck("Apply Step Spec does not have Config files store", spec.getConfigFiles().getStore());
  }

  public void validateDestroyStepParamsInline(TerraformDestroyStepParameters stepParameters) {
    Validator.notNullCheck("Destroy Step Parameters are null", stepParameters);
    Validator.notNullCheck("Destroy Step configuration is NULL", stepParameters.getConfiguration());
  }

  public void validateDestroyStepConfigFilesInline(TerraformDestroyStepParameters stepParameters) {
    Validator.notNullCheck("Destroy Step Parameters are null", stepParameters);
    Validator.notNullCheck("Destroy Step configuration is NULL", stepParameters.getConfiguration());
    TerraformExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    Validator.notNullCheck("Destroy Step Spec is NULL", spec);
    Validator.notNullCheck("Destroy Step Spec does not have Config files", spec.getConfigFiles());
    Validator.notNullCheck("Destroy Step Spec does not have Config files store", spec.getConfigFiles().getStore());
  }

  public void validatePlanStepConfigFiles(TerraformPlanStepParameters stepParameters) {
    Validator.notNullCheck("Plan Step Parameters are null", stepParameters);
    Validator.notNullCheck("Plan Step configuration is NULL", stepParameters.getConfiguration());
    Validator.notNullCheck("Plan Step does not have Config files", stepParameters.getConfiguration().getConfigFiles());
    Validator.notNullCheck(
        "Plan Step does not have Config files store", stepParameters.getConfiguration().getConfigFiles().getStore());
    Validator.notNullCheck("Plan Step does not have Plan Command", stepParameters.getConfiguration().getCommand());
    Validator.notNullCheck(
        "Plan Step does not have Secret Manager Ref", stepParameters.getConfiguration().getSecretManagerRef());
  }

  public void saveRollbackDestroyConfigInline(
      TerraformApplyStepParameters stepParameters, TerraformTaskNGResponse response, Ambiance ambiance) {
    validateApplyStepConfigFilesInline(stepParameters);
    TerraformStepConfigurationParameters configuration = stepParameters.getConfiguration();
    TerraformExecutionDataParameters spec = configuration.getSpec();
    TerraformConfigBuilder builder =
        TerraformConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(generateFullIdentifier(
                ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance))
            .pipelineExecutionId(ambiance.getPlanExecutionId());

    Map<String, String> commitIdMap = response.getCommitIdForConfigFilesMap();
    builder
        .configFiles(
            getStoreConfigAtCommitId(spec.getConfigFiles().getStore().getSpec(), commitIdMap.get(TF_CONFIG_FILES))
                .toGitStoreConfigDTO())
        .varFileConfigs(toTerraformVarFileConfig(spec.getVarFiles(), response, ambiance))
        .backendConfig(getBackendConfig(spec.getBackendConfig()))
        .environmentVariables(getEnvironmentVariablesMap(spec.getEnvironmentVariables()))
        .workspace(ParameterFieldHelper.getParameterFieldValue(spec.getWorkspace()))
        .targets(ParameterFieldHelper.getParameterFieldValue(spec.getTargets()));

    terraformConfigDAL.saveTerraformConfig(builder.build());
  }

  public String getBackendConfig(TerraformBackendConfig backendConfig) {
    if (backendConfig != null) {
      TerraformBackendConfigSpec terraformBackendConfigSpec = backendConfig.getTerraformBackendConfigSpec();
      if (terraformBackendConfigSpec instanceof InlineTerraformBackendConfigSpec) {
        return ParameterFieldHelper.getParameterFieldValue(
            ((InlineTerraformBackendConfigSpec) terraformBackendConfigSpec).getContent());
      }
    }
    return null;
  }

  public TerraformConfig getLastSuccessfulApplyConfig(TerraformDestroyStepParameters parameters, Ambiance ambiance) {
    String entityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance);
    Query<TerraformConfig> query =
        persistence.createQuery(TerraformConfig.class)
            .filter(TerraformConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(TerraformConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(TerraformConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(TerraformConfigKeys.entityId, entityId)
            .order(Sort.descending(TerraformConfigKeys.createdAt));
    TerraformConfig terraformConfig = terraformConfigDAL.getTerraformConfig(query, ambiance);
    if (terraformConfig == null) {
      throw new InvalidRequestException(format("Terraform config for Last Apply not found: [%s]", entityId));
    }
    return terraformConfig;
  }

  public Map<String, Object> parseTerraformOutputs(String terraformOutputString) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (EmptyPredicate.isEmpty(terraformOutputString)) {
      return outputs;
    }
    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(terraformOutputString), typeRef);

      json.forEach((key, object) -> outputs.put(key, ((Map<String, Object>) object).get("value")));

    } catch (IOException exception) {
      log.error("", exception);
    }
    return outputs;
  }

  public String getLatestFileId(String entityId) {
    try {
      return RestClientUtils.getResponse(fileService.get().getLatestFileId(entityId, FileBucket.TERRAFORM_STATE));
    } catch (Exception exception) {
      String message = format("Unable to call fileservice to fetch latest file id for entityId: [%s]", entityId);
      throw new InvalidRequestException(message, exception);
    }
  }

  public void saveTerraformConfig(TerraformConfig rollbackConfig, Ambiance ambiance) {
    TerraformConfig terraformConfig = TerraformConfig.builder()
                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                          .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
                                          .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
                                          .entityId(rollbackConfig.getEntityId())
                                          .pipelineExecutionId(ambiance.getPlanExecutionId())
                                          .configFiles(rollbackConfig.getConfigFiles())
                                          .varFileConfigs(rollbackConfig.getVarFileConfigs())
                                          .backendConfig(rollbackConfig.getBackendConfig())
                                          .environmentVariables(rollbackConfig.getEnvironmentVariables())
                                          .workspace(rollbackConfig.getWorkspace())
                                          .targets(rollbackConfig.getTargets())
                                          .build();

    terraformConfigDAL.saveTerraformConfig(terraformConfig);
  }

  public void updateParentEntityIdAndVersion(String entityId, String stateFileId) {
    try {
      RestClientUtils.getResponse(fileService.get().updateParentEntityIdAndVersion(
          URLEncoder.encode(entityId, "UTF-8"), stateFileId, FileBucket.TERRAFORM_STATE));
    } catch (Exception ex) {
      log.error(format("EntityId and Version update failed for entityId: [%s], with error %s: ", entityId,
          ExceptionUtils.getMessage(ex)));
      throw new InvalidRequestException(
          format("Unable to update StateFile version for entityId: [%s], Please try re-running pipeline", entityId));
    }
  }

  // Conversion Methods
  public List<TerraformVarFileInfo> toTerraformVarFileInfo(
      Map<String, TerraformVarFile> varFilesMap, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
      int i = 0;
      for (TerraformVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content =
                ParameterFieldHelper.getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent(content).build());
            }
          } else if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              i++;
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              // Retrieve the files from the GIT stores
              GitFetchFilesConfig gitFetchFilesConfig =
                  getGitFetchFilesConfig(storeConfig, ambiance, format(TerraformStepHelper.TF_VAR_FILES, i));
              // And retrive the files from the Files stores
              FileStoreFetchFilesConfig fileFetchFilesConfig =
                  getFileFactoryFetchFilesConfig(storeConfig, ambiance, format(TerraformStepHelper.TF_VAR_FILES, i));
              varFileInfo.add(RemoteTerraformVarFileInfo.builder()
                                  .gitFetchFilesConfig(gitFetchFilesConfig)
                                  .filestoreFetchFilesConfig(fileFetchFilesConfig)
                                  .build());
            }
          }
        }
      }
      return varFileInfo;
    }
    return Collections.emptyList();
  }

  public List<TerraformVarFileConfig> toTerraformVarFileConfig(
      Map<String, TerraformVarFile> varFilesMap, TerraformTaskNGResponse response, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileConfig> varFileConfigs = new ArrayList<>();
      int i = 0;
      for (TerraformVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content =
                ParameterFieldHelper.getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              varFileConfigs.add(TerraformInlineVarFileConfig.builder().varFileContent(content).build());
            }
          } else if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              i++;
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              GitStoreConfigDTO gitStoreConfigDTO = getStoreConfigAtCommitId(
                  storeConfig, response.getCommitIdForConfigFilesMap().get(format(TF_VAR_FILES, i)))
                                                        .toGitStoreConfigDTO();
              varFileConfigs.add(TerraformRemoteVarFileConfig.builder().gitStoreConfigDTO(gitStoreConfigDTO).build());
            }
          }
        }
      }
      return varFileConfigs;
    }
    return Collections.emptyList();
  }

  public List<TerraformVarFileInfo> prepareTerraformVarFileInfo(
      List<TerraformVarFileConfig> varFileConfigs, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFileConfigs)) {
      int i = 0;
      List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
      for (TerraformVarFileConfig fileConfig : varFileConfigs) {
        if (fileConfig instanceof TerraformInlineVarFileConfig) {
          varFileInfo.add(InlineTerraformVarFileInfo.builder()
                              .varFileContent(((TerraformInlineVarFileConfig) fileConfig).getVarFileContent())
                              .build());
        } else if (fileConfig instanceof TerraformRemoteVarFileConfig) {
          i++;
          GitStoreConfig gitStoreConfig =
              ((TerraformRemoteVarFileConfig) fileConfig).getGitStoreConfigDTO().toGitStoreConfig();
          GitFetchFilesConfig gitFetchFilesConfig =
              getGitFetchFilesConfig(gitStoreConfig, ambiance, format(TerraformStepHelper.TF_VAR_FILES, i));
          StoreConfig fileStoreConfig = ((TerraformRemoteVarFileConfig) fileConfig).getFileStoreConfig();
          FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
              getFileFactoryFetchFilesConfig(fileStoreConfig, ambiance, format(TerraformStepHelper.TF_VAR_FILES, i));
          varFileInfo.add(RemoteTerraformVarFileInfo.builder()
                              .gitFetchFilesConfig(gitFetchFilesConfig)
                              .filestoreFetchFilesConfig(fileStoreFetchFilesConfig)
                              .build());
        }
      }
      return varFileInfo;
    }
    return Collections.emptyList();
  }
}
