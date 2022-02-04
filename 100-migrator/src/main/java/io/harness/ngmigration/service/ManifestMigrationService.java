package io.harness.ngmigration.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.ngmigration.NGYamlFile;
import software.wings.service.intfc.ApplicationManifestService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ManifestMigrationService implements NgMigrationService {
  @Inject private ApplicationManifestService applicationManifestService;

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    ApplicationManifest appManifest = (ApplicationManifest) entity;
    CgEntityId cgEntityId = CgEntityId.builder().id(appManifest.getUuid()).type(NGMigrationEntityType.MANIFEST).build();
    CgEntityNode cgEntityNode = CgEntityNode.builder()
                                    .entityId(cgEntityId)
                                    .entity(appManifest)
                                    .id(appManifest.getUuid())
                                    .type(NGMigrationEntityType.MANIFEST)
                                    .build();
    Set<CgEntityId> children = new HashSet<>();
    if (appManifest.getGitFileConfig() != null && isNotEmpty(appManifest.getGitFileConfig().getConnectorId())) {
      children.add(CgEntityId.builder()
                       .id(appManifest.getGitFileConfig().getConnectorId())
                       .type(NGMigrationEntityType.CONNECTOR)
                       .build());
    }
    if (appManifest.getHelmChartConfig() != null && isNotEmpty(appManifest.getHelmChartConfig().getConnectorId())) {
      children.add(CgEntityId.builder()
                       .id(appManifest.getHelmChartConfig().getConnectorId())
                       .type(NGMigrationEntityType.CONNECTOR)
                       .build());
    }
    return DiscoveryNode.builder().children(children).entityNode(cgEntityNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(applicationManifestService.getById(appId, entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    return null;
  }

  @Override
  public void migrate(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {}

  @Override
  public List<NGYamlFile> getYamls(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    return null;
  }

  public List<ManifestConfigWrapper> getManifests(Set<CgEntityId> manifestEntityIds,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    if (isEmpty(manifestEntityIds)) {
      return Collections.emptyList();
    }

    List<ManifestConfigWrapper> ngManifests = new ArrayList<>();
    for (CgEntityId manifestEntityId : manifestEntityIds) {
      CgEntityNode manifestNode = entities.get(manifestEntityId);
      ApplicationManifest applicationManifest = (ApplicationManifest) manifestNode.getEntity();

      // TODO : move if-else logic to factory pattern
      if (applicationManifest.getKind() == AppManifestKind.K8S_MANIFEST
          && applicationManifest.getStoreType() == StoreType.Remote) {
        // TODO: get store from migrated connector entity

        GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
        NgEntityDetail connector = migratedEntities.get(
            CgEntityId.builder().id(gitFileConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build());

        K8sManifest k8sManifest =
            K8sManifest
                .builder()
                // TODO: There needs to be a logic to build identifier of the manifest
                .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid()))
                .skipResourceVersioning(
                    ParameterField.createValueField(applicationManifest.getSkipVersioningForAllK8sObjects()))
                .store(ParameterField.createValueField(StoreConfigWrapper.builder()
                                                           .type(StoreConfigType.GIT)
                                                           .spec(getGitStore(gitFileConfig, connector.getIdentifier()))
                                                           .build()))
                .build();
        ManifestConfigWrapper manifestConfigWrapper =
            ManifestConfigWrapper.builder()
                .manifest(ManifestConfig.builder()
                              .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid()))
                              .type(ManifestConfigType.K8_MANIFEST)
                              .spec(k8sManifest)
                              .build())
                .build();
        ngManifests.add(manifestConfigWrapper);
      } else if (applicationManifest.getKind() == AppManifestKind.VALUES
          && applicationManifest.getStoreType() == StoreType.Remote) {
        GitFileConfig gitFileConfig = applicationManifest.getGitFileConfig();
        NgEntityDetail connector = migratedEntities.get(
            CgEntityId.builder().id(gitFileConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build());

        ValuesManifest valuesManifest =
            ValuesManifest.builder()
                .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid()))
                .store(ParameterField.createValueField(StoreConfigWrapper.builder()
                                                           .type(StoreConfigType.GIT)
                                                           .spec(getGitStore(gitFileConfig, connector.getIdentifier()))
                                                           .build()))
                .build();
        ManifestConfigWrapper manifestConfigWrapper =
            ManifestConfigWrapper.builder()
                .manifest(ManifestConfig.builder()
                              .identifier(MigratorUtility.generateIdentifier(applicationManifest.getUuid()))
                              .type(ManifestConfigType.VALUES)
                              .spec(valuesManifest)
                              .build())
                .build();
        ngManifests.add(manifestConfigWrapper);
      } else {
        throw new UnsupportedOperationException(
            String.format("Only K8s and Values Manifest with Remote Store Supported. Found- Kind : [%s] Store: [%s]",
                applicationManifest.getKind(), applicationManifest.getStoreType()));
      }
    }
    return ngManifests;
  }

  // TODO: use scoped connectorRef ref
  private GitStore getGitStore(GitFileConfig gitFileConfig, String connectorRef) {
    return GitStore.builder()
        .branch(ParameterField.createValueField(gitFileConfig.getBranch()))
        .commitId(ParameterField.createValueField(gitFileConfig.getCommitId()))
        .connectorRef(ParameterField.createValueField(connectorRef))
        .gitFetchType(gitFileConfig.isUseBranch() ? FetchType.BRANCH : FetchType.COMMIT)
        .paths(ParameterField.createValueField(Collections.singletonList(gitFileConfig.getFilePath())))
        .repoName(ParameterField.createValueField(gitFileConfig.getRepoName()))
        .build();
  }
}
