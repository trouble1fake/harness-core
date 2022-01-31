/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.ng.core.rbac.ProjectPermissions.EDIT_PROJECT_PERMISSION;
import static io.harness.ng.core.rbac.ProjectPermissions.VIEW_PROJECT_PERMISSION;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.dtos.TriggerFullSyncResponseDTO;
import io.harness.gitsync.common.service.FullSyncTriggerService;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigService;
import io.harness.gitsync.core.fullsync.GitFullSyncEntityService;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigRequestDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncEntityInfoFilterDTO;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Api("/full-sync")
@Path("/full-sync")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = BAD_REQUEST_CODE, description = BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = INTERNAL_SERVER_ERROR_CODE, description = INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
    })
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class GitFullSyncResource {
  private final FullSyncTriggerService fullSyncTriggerService;
  private final GitFullSyncConfigService gitFullSyncConfigService;
  private final GitFullSyncEntityService gitFullSyncEntityService;
  // Add blank line here
  @POST
  @ApiOperation(value = "Triggers Full Sync", nickname = "triggerFullSync")
  @Operation(operationId = "triggerFullSync", summary = "Triggers Full Sync",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully triggered Full Sync") })
  public ResponseDTO<TriggerFullSyncResponseDTO>
  // method name shouldn't be create, its not creating anything but rather triggering an ops
  // @NotNull should be used with accountIdentifier instead of @NotEmpty
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      // Why are there 2 different @OrgIdentifier used?
      // Isn't there something similar for Account?
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    return ResponseDTO.newResponse(
        fullSyncTriggerService.triggerFullSync(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @POST
  @Path("/config")
  @ApiOperation(value = "Create a full sync configuration", nickname = "createGitFullSyncConfig")
  @Operation(operationId = "createGitFullSyncConfig", summary = "Creates a configuration for performing git full sync",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the configuration back along with the scope information")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = EDIT_PROJECT_PERMISSION)
  public ResponseDTO<GitFullSyncConfigDTO>
  createFullSyncConfig(
      // Use @NotNull
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      // Why multiple usages of @OrgIdentifier
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      // Why multiple usages of @ProjectIdentifier?
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier,
      @RequestBody(description = "Configuration body") @NotNull @Valid GitFullSyncConfigRequestDTO requestDTO) {
    return ResponseDTO.newResponse(
        gitFullSyncConfigService.createConfig(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO));
  }

  @PUT
  @Path("/config")
  @ApiOperation(value = "Update a full sync configuration", nickname = "updateGitFullSyncConfig")
  // Summary should be : "Updates configuration used for performing git full sync" ?
  @Operation(operationId = "updateGitFullSyncConfig", summary = "Updates a configuration for performing git full sync",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the configuration back along with the scope information")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = EDIT_PROJECT_PERMISSION)
  public ResponseDTO<GitFullSyncConfigDTO>
  updateFullSyncConfig(
      // Please refer same comments on query params as mentioned before
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier,
      @RequestBody(description = "Configuration body") @NotNull @Valid GitFullSyncConfigRequestDTO requestDTO) {
    return ResponseDTO.newResponse(
        gitFullSyncConfigService.updateConfig(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO));
  }

  @GET
  @Path("/config")
  @ApiOperation(value = "Get full sync configuration", nickname = "getGitFullSyncConfig")
  // Summary should be : "Get configuration used for performing git full sync" ?
  @Operation(operationId = "getGitFullSyncConfig", summary = "Get configuration for performing git full sync",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the configuration back along with the scope information")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<GitFullSyncConfigDTO>
  getFullSyncConfig(
      // check same comments as above
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier) {
    GitFullSyncConfigDTO gitFullSyncConfigDTO =
        gitFullSyncConfigService
            .get(accountIdentifier, orgIdentifier, projectIdentifier)
            // Why are we throwing exception here?
            // If git sync config is not found, simply return HTTP 200 saying no git sync config found.
            .orElseThrow(
                // Message can be better : "Git Full Sync Config not found at the scope provided" ?
                () -> new InvalidRequestException("Config not found at the scope provided", WingsException.USER));
    return ResponseDTO.newResponse(gitFullSyncConfigDTO);
  }

  @DELETE
  @Path("/config")
  @ApiOperation(value = "Delete full sync configuration", nickname = "deleteGitFullSyncConfig")
  // Summary should be : "Delete configuration used for performing git full sync" ?
  @Operation(operationId = "deleteGitFullSyncConfig", summary = "Delete configuration for performing git full sync",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true/false depending upon whether the operation was successful")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = EDIT_PROJECT_PERMISSION)
  public ResponseDTO<Boolean>
  deleteFullSyncConfig(
      // Check same comments
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier) {
    // Returning Boolean doesn't justify delete ops
    // If its false, then whats the cause? Is it genuinely false for some logic or is it some internal issue?
    // Ideally, we should send HTTP 404 if there's no config to delete
    // Also, we should return the config that's deleted. Simply returning true/false is sort of not a good response.
    return ResponseDTO.newResponse(
        gitFullSyncConfigService.delete(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  // Lets discuss on why its a blocker to make it a GET request and how can we try to resolve it
  // Using POST for GET requests isn't correct
  @POST
  @Path("/files")
  @ApiOperation(value = "List files in full sync along with their status", nickname = "listFullSyncFiles")
  @Operation(operationId = "listFullSyncFiles", summary = "List files in full sync along with their status",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "A list of files involved in full sync") })
  @NGAccessControlCheck(resourceType = ResourceTypes.PROJECT, permission = VIEW_PROJECT_PERMISSION)
  public ResponseDTO<PageResponse<GitFullSyncEntityInfoDTO>>
  listFiles(
      // Please maintain order of parameters in any request
      // Page request can't be the first parameter.
      @RequestBody(description = "Details of Page including: size, index, sort") @BeanParam PageRequest pageRequest,
      // Check same comments as previously mentioned
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @io.harness.accesscontrol.AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @io.
      harness.accesscontrol.ProjectIdentifier @ProjectIdentifier String projectIdentifier,
      // What is a search term? Do our customers understand it?
      @Parameter(description = GitSyncApiConstants.SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      // Why is it a required field?
      @RequestBody(description = "Filters like entityType and syncStatus",
          required = true) @Body GitFullSyncEntityInfoFilterDTO gitFullSyncEntityInfoFilterDTO) {
    return ResponseDTO.newResponse(gitFullSyncEntityService.list(
        accountIdentifier, orgIdentifier, projectIdentifier, pageRequest, searchTerm, gitFullSyncEntityInfoFilterDTO));
  }
}
