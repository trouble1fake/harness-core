/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.artifactory;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.utils.IdentifierRefHelper;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Api("artifacts")
@Path("/artifacts/artifactory")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ArtifactoryArtifactResource {
  private final ArtifactoryResourceService artifactoryResourceService;
  private final PipelineServiceClient pipelineServiceClient;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets artifactory artifact build details", nickname = "getBuildDetailsForArtifactoryArtifact")
  public ResponseDTO<ArtifactoryResponseDTO> getBuildDetails(@QueryParam("repository") String repository,
      @QueryParam("imagePath") String imagePath, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("dockerRepositoryServer") String dockerRepositoryServer,
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    ArtifactoryResponseDTO buildDetails = artifactoryResourceService.getBuildDetails(connectorRef, repository,
        imagePath, repositoryFormat, dockerRepositoryServer, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets artifactory artifact build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForArtifactoryArtifactWithYaml")
  public ResponseDTO<ArtifactoryResponseDTO>
  getBuildDetailsV2(@QueryParam("repository") String repository, @QueryParam("imagePath") String imagePath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("dockerRepositoryServer") String dockerRepositoryServer,
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull String runtimeInputYaml) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    imagePath = ArtifactResourceUtils.getResolvedImagePath(pipelineServiceClient, accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo);
    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    ArtifactoryResponseDTO buildDetails = artifactoryResourceService.getBuildDetails(connectorRef, repository,
        imagePath, repositoryFormat, dockerRepositoryServer, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(value = "Gets artifactory artifact last successful build",
      nickname = "getLastSuccessfulBuildForArtifactoryArtifact")
  public ResponseDTO<ArtifactoryBuildDetailsDTO>
  getLastSuccessfulBuild(@QueryParam("repository") String repository, @QueryParam("imagePath") String imagePath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("dockerRepositoryServer") String dockerRepositoryServer,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      ArtifactoryRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    ArtifactoryBuildDetailsDTO buildDetails = artifactoryResourceService.getSuccessfulBuild(connectorRef, repository,
        imagePath, repositoryFormat, dockerRepositoryServer, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate artifactory artifact server", nickname = "validateArtifactServerForArtifactory")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer =
        artifactoryResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }
}
