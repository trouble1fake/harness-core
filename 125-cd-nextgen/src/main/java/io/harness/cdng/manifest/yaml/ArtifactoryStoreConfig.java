/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;
import com.fasterxml.jackson.annotation.JsonTypeName;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CDC;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.ARTIFACTORY)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("artifactoryStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.ArtifactoryStore")

public class ArtifactoryStoreConfig implements StoreConfig, Visitable, WithConnectorRef {
    @NotNull @Wither @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
    private ParameterField<String> connectorRef;
    @NotNull @Wither @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
    private ParameterField<String> repositoryPath;
    @NotNull @Wither @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
    private ParameterField<String> artifactName;
    //TODO: This one is going to be complicated I guess. If we need to retrieve all the versions from artifactory
    // it means that we need to do some kind of sync call to retrieve all the info from artifactory. IDK if the API
    // Allows that nor how to make sync calls to the delegate but I guess that this is how we can identify what versions
    // the client has. Probably something like this: https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-FileList
    // To retrieve the artifact we will use probably https://www.jfrog.com/confluence/display/JFROG/Artifactory+REST+API#ArtifactoryRESTAPI-RetrieveArtifact
    // Example: GET http://localhost:8081/artifactory/libs-release-local/ch/qos/logback/logback-classic/0.9.9/logback-classic-0.9.9.jar?skipUpdateStats=true
    // http://localhost:8081/artifactory/<REPOSITORY_PATH>/<ARTIFACT_NAME>_-.<VERSION>.xyz
    @NotNull @Wither @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
    private ParameterField<String> version;

    //TODO: For Terraform Git the varFiles contain a Path, which is a list of strings.
    // That makes sense for git as you can have several tfvar files in git, but here we were told that the tfvar files
    // will be also a single file (rar). If that is the case we can ignore the list of string as the varFile will be the same as
    // the tf file. But for those which have several files and are NOT in a compress format, would it make sense to let the user add
    // the files one by one? That means that we don't need to add a list of strings neither as everytime that the user adds a tfvar file
    // he will be adding the whole varFile block each time.
    /*
     varFiles:
        - varFile:
              type: Remote
              identifier: tfvarfile
              spec:
                  store:
                      type: Artifactory
                      spec:
                          repositoryPath: "FloraRepo-generic/backend_env_variables_1.2.tgz"
                          artifactName: "backend_env_variables_1"
                          version: 1.2
                          connectorRef: foobar
        - varFile:
              type: Remote
              identifier: lavir
              spec:
                  store:
                      type: Artifactory
                      spec:
                          repositoryPath: "FloraRepo-generic/backend_env_super_variables_1.8.tgz"
                          artifactName: "backend_env_super_variables"
                          version: 1.8
                          connectorRef: foobar
     */

    @Override
    public String getKind() {
        return ManifestStoreType.ARTIFACTORY;
    }

    @Override
    public StoreConfig cloneInternal() {
        return ArtifactoryStoreConfig.builder()
                .connectorRef(connectorRef)
                .artifactName(artifactName)
                .repositoryPath(repositoryPath)
                .version(version)
                .build();
    }

    @Override
    public ParameterField<String> getConnectorReference() {
        return connectorRef;
    }

    @Override
    public Map<String, ParameterField<String>> extractConnectorRefs() {
        Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
        connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
        return connectorRefMap;
    }

    @Override
    public StoreConfig applyOverrides(StoreConfig overrideConfig) {
        ArtifactoryStoreConfig ArtifactoryStore = (ArtifactoryStoreConfig) overrideConfig;
        ArtifactoryStoreConfig resultantArtifactoryStore = this;
        if (!ParameterField.isNull(ArtifactoryStore.getConnectorRef())) {
            resultantArtifactoryStore = resultantArtifactoryStore.withConnectorRef(ArtifactoryStore.getConnectorRef());
        }
        if (!ParameterField.isNull(ArtifactoryStore.getArtifactName())){
            resultantArtifactoryStore = resultantArtifactoryStore.withArtifactName(ArtifactoryStore.getArtifactName());
        }
        if (!ParameterField.isNull(ArtifactoryStore.getRepositoryPath())) {
            resultantArtifactoryStore = resultantArtifactoryStore.withRepositoryPath(ArtifactoryStore.getRepositoryPath());
        }
        if (!ParameterField.isNull(ArtifactoryStore.getVersion())){
            resultantArtifactoryStore = resultantArtifactoryStore.withVersion(ArtifactoryStore.getVersion());
        }
        return resultantArtifactoryStore;
    }
}
