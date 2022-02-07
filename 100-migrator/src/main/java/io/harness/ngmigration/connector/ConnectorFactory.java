/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import static io.harness.delegate.beans.connector.docker.DockerAuthType.ANONYMOUS;
import static io.harness.delegate.beans.connector.docker.DockerAuthType.USER_PASSWORD;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO.builder;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.k8s.model.KubernetesClusterAuthType.NONE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthCredentialsDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO.KubernetesClusterConfigDTOBuilder;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesOpenIdConnectDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.UnsupportedOperationException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.shell.AuthenticationScheme;

import software.wings.beans.DockerConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class ConnectorFactory {
  public static ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    if (settingAttribute.getValue() instanceof DockerConfig) {
      return ConnectorType.DOCKER;
    }
    if (settingAttribute.getValue() instanceof KubernetesClusterConfig) {
      return ConnectorType.KUBERNETES_CLUSTER;
    }
    if (settingAttribute.getValue() instanceof GitConfig) {
      return ConnectorType.GIT;
    }
    throw new UnsupportedOperationException("Only support few connector types.");
  }

  public static ConnectorConfigDTO getConfigDTO(SettingAttribute settingAttribute, Set<CgEntityId> childEntities,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    // TODO: move to factory pattern
    if (settingAttribute.getValue() instanceof DockerConfig) {
      return fromDocker(settingAttribute);
    }
    if (settingAttribute.getValue() instanceof KubernetesClusterConfig) {
      return fromK8s(settingAttribute);
    }
    if (settingAttribute.getValue() instanceof GitConfig) {
      return fromGitConfig(settingAttribute, childEntities, migratedEntities);
    }
    throw new UnsupportedOperationException("Connector Not Supported");
  }

  private static ConnectorConfigDTO fromGitConfig(SettingAttribute settingAttribute, Set<CgEntityId> childEntities,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

    return GitConfigDTO.builder()
        .branchName(gitConfig.getBranch())
        .delegateSelectors(new HashSet<>(CollectionUtils.emptyIfNull(gitConfig.getDelegateSelectors())))
        .executeOnDelegate(true)
        .gitAuthType(getAuthType(gitConfig.getAuthenticationScheme()))
        .gitAuth(getGitAuth(gitConfig, childEntities, migratedEntities))
        .gitConnectionType(getGitConnectionType(gitConfig.getUrlType()))
        .url(gitConfig.getRepoUrl())
        .build();
  }

  private static GitAuthenticationDTO getGitAuth(
      GitConfig gitConfig, Set<CgEntityId> childEntities, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    if (gitConfig.getAuthenticationScheme() == AuthenticationScheme.HTTP_PASSWORD) {
      CgEntityId passwordRefEntityId =
          childEntities.stream()
              .filter(childEntity -> childEntity.getId().equals(gitConfig.getEncryptedPassword()))
              .findFirst()
              .get();
      String identifier = migratedEntities.get(passwordRefEntityId).getIdentifier();

      return GitHTTPAuthenticationDTO.builder()
          .username(gitConfig.getUsername())

          .passwordRef(
              // TODO: scope will come from inputs
              SecretRefData.builder().identifier(identifier).scope(Scope.PROJECT).build())
          .build();
    } else if (gitConfig.getAuthenticationScheme() == AuthenticationScheme.SSH_KEY) {
      return GitSSHAuthenticationDTO.builder()
          .encryptedSshKey(
              SecretRefData
                  .builder()
                  // TODO: identifier will come from inside ssh key ref setting attribute. It needs to be discovered and
                  // mapped to a secret. Ref of that secret will be used here.
                  .identifier(MigratorUtility.generateIdentifier(gitConfig.getSshSettingAttribute().getName()))
                  .scope(Scope.PROJECT)
                  .build())
          .build();
    } else {
      throw new UnsupportedOperationException("Unsupported git auth type: " + gitConfig.getAuthenticationScheme());
    }
  }

  private static GitConnectionType getGitConnectionType(GitConfig.UrlType urlType) {
    return urlType == GitConfig.UrlType.REPO ? GitConnectionType.REPO : GitConnectionType.ACCOUNT;
  }

  private static GitAuthType getAuthType(AuthenticationScheme authenticationScheme) {
    switch (authenticationScheme) {
      case HTTP_PASSWORD:
        return GitAuthType.HTTP;
      case SSH_KEY:
        return GitAuthType.SSH;
      default:
        throw new UnsupportedOperationException("Git auth Type not supported : " + authenticationScheme);
    }
  }

  public static String getSecretId(SettingAttribute settingAttribute) {
    // TODO: Move if-else logic to factory pattern
    if (settingAttribute.getValue() instanceof DockerConfig) {
      DockerConfig dockerConfig = (DockerConfig) settingAttribute.getValue();
      return dockerConfig.getEncryptedPassword();
    }
    if (settingAttribute.getValue() instanceof KubernetesClusterConfig) {
      KubernetesClusterConfig k8sConfig = (KubernetesClusterConfig) settingAttribute.getValue();
      return k8sConfig.getEncryptedPassword();
    }
    if (settingAttribute.getValue() instanceof GitConfig) {
      return ((GitConfig) settingAttribute.getValue()).getEncryptedPassword();
    }
    throw new UnsupportedOperationException("Connector Not Supported. Type: " + settingAttribute.getValue().getType());
  }

  private static Set<String> toSet(List<String> list) {
    if (EmptyPredicate.isEmpty(list)) {
      return new HashSet<>();
    }
    return new HashSet<>(list);
  }

  private static ConnectorConfigDTO fromDocker(SettingAttribute settingAttribute) {
    DockerConfig dockerConfig = (DockerConfig) settingAttribute.getValue();
    DockerAuthType dockerAuthType = StringUtils.isBlank(dockerConfig.getUsername()) ? ANONYMOUS : USER_PASSWORD;
    DockerAuthCredentialsDTO credentialsDTO = null;
    if (dockerAuthType.equals(USER_PASSWORD)) {
      credentialsDTO =
          DockerUserNamePasswordDTO.builder()
              .username(dockerConfig.getUsername())
              .passwordRef(
                  SecretRefData.builder().identifier(dockerConfig.getEncryptedPassword()).scope(Scope.PROJECT).build())
              .build();
    }
    return DockerConnectorDTO.builder()
        .dockerRegistryUrl(dockerConfig.getDockerRegistryUrl())
        .delegateSelectors(toSet(dockerConfig.getDelegateSelectors()))
        .providerType(DockerRegistryProviderType.OTHER)
        .auth(DockerAuthenticationDTO.builder().authType(dockerAuthType).credentials(credentialsDTO).build())
        .build();
  }

  private static ConnectorConfigDTO fromK8s(SettingAttribute settingAttribute) {
    KubernetesClusterConfig clusterConfig = (KubernetesClusterConfig) settingAttribute.getValue();
    KubernetesClusterAuthType authType = clusterConfig.getAuthType() == null ? NONE : clusterConfig.getAuthType();
    KubernetesClusterConfigDTOBuilder builder = builder().delegateSelectors(clusterConfig.getDelegateSelectors());
    KubernetesCredentialDTO credentialDTO;
    switch (authType) {
      case USER_PASSWORD:
        credentialDTO = KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .masterUrl(clusterConfig.getMasterUrl())
                                        .auth(KubernetesAuthDTO.builder()
                                                  .authType(KubernetesAuthType.USER_PASSWORD)
                                                  .credentials(KubernetesUserNamePasswordDTO.builder()
                                                                   .username(new String(clusterConfig.getUsername()))
                                                                   .passwordRef(null)
                                                                   .build())
                                                  .build())
                                        .build())
                            .build();
        break;
      case SERVICE_ACCOUNT:
        credentialDTO =
            KubernetesCredentialDTO.builder()
                .kubernetesCredentialType(MANUAL_CREDENTIALS)
                .config(KubernetesClusterDetailsDTO.builder()
                            .masterUrl(clusterConfig.getMasterUrl())
                            .auth(KubernetesAuthDTO.builder()
                                      .authType(KubernetesAuthType.SERVICE_ACCOUNT)
                                      .credentials(
                                          KubernetesServiceAccountDTO.builder().serviceAccountTokenRef(null).build())
                                      .build())
                            .build())
                .build();
        break;
      case OIDC:
        credentialDTO = KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .masterUrl(clusterConfig.getMasterUrl())
                                        .auth(KubernetesAuthDTO.builder()
                                                  .authType(KubernetesAuthType.OPEN_ID_CONNECT)
                                                  .credentials(KubernetesOpenIdConnectDTO.builder().build())
                                                  .build())
                                        .build())
                            .build();
        break;
      case NONE:
        credentialDTO = KubernetesCredentialDTO.builder().kubernetesCredentialType(INHERIT_FROM_DELEGATE).build();
        break;
      default:
        throw new UnsupportedOperationException("K8s Auth type not supported");
    }

    return builder.credential(credentialDTO).build();
  }
}
