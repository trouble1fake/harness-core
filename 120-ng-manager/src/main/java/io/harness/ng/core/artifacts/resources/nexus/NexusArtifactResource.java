/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.nexus;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
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
@Path("/artifacts/nexus")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class NexusArtifactResource {
  private final NexusResourceService nexusResourceService;
  private final PipelineServiceClient pipelineServiceClient;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets nexus artifact build details", nickname = "getBuildDetailsForNexusArtifact")
  public ResponseDTO<NexusResponseDTO> getBuildDetails(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") Integer repositoryPort, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("imagePath") String imagePath, @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    if (repositoryPort == null || repositoryPort.intValue() == 0) {
      repositoryPort = 8181;
    }
    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    NexusResponseDTO buildDetails = nexusResourceService.getBuildDetails(
        connectorRef, repository, repositoryPort, imagePath, repositoryFormat, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets nexus artifact build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForNexusArtifactWithYaml")
  public ResponseDTO<NexusResponseDTO>
  getBuildDetailsV2(@QueryParam("repository") String repository, @QueryParam("repositoryPort") Integer repositoryPort,
      @QueryParam("imagePath") String imagePath, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull String runtimeInputYaml) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    imagePath = ArtifactResourceUtils.getResolvedImagePath(pipelineServiceClient, accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo);

    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    NexusResponseDTO buildDetails = nexusResourceService.getBuildDetails(
        connectorRef, repository, repositoryPort, imagePath, repositoryFormat, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLabels")
  @ApiOperation(value = "Gets nexus artifact labels", nickname = "getLabelsForNexusArtifact")
  public ResponseDTO<NexusResponseDTO> getLabels(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") Integer repositoryPort, @QueryParam("imagePath") String imagePath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, NexusRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    NexusResponseDTO buildDetails = nexusResourceService.getLabels(connectorRef, repository, repositoryPort, imagePath,
        repositoryFormat, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(
      value = "Gets nexus artifact last successful build", nickname = "getLastSuccessfulBuildForNexusArtifact")
  public ResponseDTO<NexusBuildDetailsDTO>
  getLastSuccessfulBuild(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") Integer repositoryPort, @QueryParam("imagePath") String imagePath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, NexusRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    NexusBuildDetailsDTO buildDetails = nexusResourceService.getSuccessfulBuild(connectorRef, repository,
        repositoryPort, imagePath, repositoryFormat, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate nexus artifact server", nickname = "validateArtifactServerForNexus")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer =
        nexusResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @GET
  @Path("validateArtifactSource")
  @ApiOperation(value = "Validate nexus artifact image", nickname = "validateArtifactImageForNexusArtifact")
  public ResponseDTO<Boolean> validateArtifactImage(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") Integer repositoryPort, @QueryParam("imagePath") String imagePath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }
    boolean isValidArtifactImage = nexusResourceService.validateArtifactSource(
        repository, repositoryPort, imagePath, repositoryFormat, connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactImage);
  }

  @POST
  @Path("validateArtifact")
  @ApiOperation(value = "Validate nexus artifact with tag/tagregx if given", nickname = "validateArtifactForNexus")
  public ResponseDTO<Boolean> validateArtifact(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") Integer repositoryPort, @QueryParam("imagePath") String imagePath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, NexusRequestDTO requestDTO) {
    if (NGExpressionUtils.isRuntimeOrExpressionField(nexusConnectorIdentifier)) {
      throw new InvalidRequestException("ConnectorRef is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(imagePath)) {
      throw new InvalidRequestException("ImagePath is an expression/runtime input, please send fixed value.");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    if (!RepositoryFormat.docker.name().equals(repositoryFormat)) {
      repositoryFormat = RepositoryFormat.docker.name();
    }

    boolean isValidArtifact = false;
    if (!ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTag())
        && !ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTagRegex())) {
      isValidArtifact = nexusResourceService.validateArtifactSource(
          repository, repositoryPort, imagePath, repositoryFormat, connectorRef, orgIdentifier, projectIdentifier);
    } else {
      try {
        ResponseDTO<NexusBuildDetailsDTO> lastSuccessfulBuild =
            getLastSuccessfulBuild(repository, repositoryPort, imagePath, repositoryFormat, nexusConnectorIdentifier,
                accountId, orgIdentifier, projectIdentifier, requestDTO);
        if (lastSuccessfulBuild.getData() != null
            && EmptyPredicate.isNotEmpty(lastSuccessfulBuild.getData().getTag())) {
          isValidArtifact = true;
        }
      } catch (Exception e) {
        log.info("Not able to find any artifact with given parameters - " + requestDTO.toString() + " and imagePath - "
            + imagePath);
      }
    }
    return ResponseDTO.newResponse(isValidArtifact);
  }
}
