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
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRepoDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
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

  @GET
  @Path("getRepositoriesDetails")
  @ApiOperation(value = "Gets repository details", nickname = "getRepositoriesDetailsForArtifactory")
  public ResponseDTO<ArtifactoryRepoDetailsDTO> getRepositoriesDetails(
      @NotNull @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @QueryParam("repositoryType") @DefaultValue("any") String repositoryType,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ArtifactoryRepoDetailsDTO repoDetailsDTO =
        artifactoryResourceService.getRepositories(repositoryType, connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(repoDetailsDTO);
  }

  @GET
  @Path("getBuildsDetails")
  @ApiOperation(value = "Gets builds details", nickname = "getBuildsDetailsForArtifactory")
  public ResponseDTO<List<ArtifactoryArtifactBuildDetailsDTO>> getBuildsDetails(
      @NotNull @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam("repositoryName") String repositoryName, @QueryParam("filePath") String filePath,
      @NotNull @QueryParam("maxVersions") int maxVersions,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    List<ArtifactoryArtifactBuildDetailsDTO> buildDetails = artifactoryResourceService.getBuildDetails(
        repositoryName, filePath, maxVersions, connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }
}
