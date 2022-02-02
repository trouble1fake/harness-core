/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.variable.apis.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.clients.Resource;
import io.harness.accesscontrol.clients.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.variable.VariableDTO;
import io.harness.variable.VariableResponseDTO;
import io.harness.variable.impl.VariableServiceImpl;
import io.harness.variable.accesscontrol.ResourceTypes;
import io.harness.variable.accesscontrol.VariablesAccessControlPermissions;
import io.harness.data.validator.EntityIdentifier;
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
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotBlank;

@Api("/variables")
@Path("/variables")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Tag(name = "Variables", description = "This contains APIs related to Variables as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@NextGenManagerAuth
@OwnedBy(HarnessTeam.DX)
public class VariableResource {
  private final VariableServiceImpl variableService;
  private final AccessControlClient accessControlClient;

  @Inject
  public VariableResource(VariableServiceImpl variableService,
                          AccessControlClient accessControlClient) {
    this.variableService = variableService;
    this.accessControlClient = accessControlClient;
  }

  @GET
  @Path("validateUniqueIdentifier")
  @ApiOperation(value = "Validate Identifier is unique", nickname = "validateTheIdentifierIsUnique")
  @Operation(operationId = "validateTheIdentifierIsUnique",
          summary = "Validate the Connector by Account Identifier and Connector Identifier",
          responses =
                  {
                          @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
                                  description = "It returns true if the Identifier is unique and false if the Identifier is not unique")
                  })
  public ResponseDTO<Boolean>
  validateTheIdentifierIsUnique(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
                                @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
                                        NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
                                @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
                                        NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
                                @Parameter(description = "Connector ID") @QueryParam(
                                        NGCommonEntityConstants.IDENTIFIER_KEY) @EntityIdentifier String connectorIdentifier) {
    return ResponseDTO.newResponse(variableService.validateTheIdentifierIsUnique(
            accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
  }

  @POST
  @ApiOperation(value = "Creates a Variable", nickname = "createVariable")
  @Operation(operationId = "createVariable", summary = "Creates a Variable",
          responses =
                  {
                          @io.swagger.v3.oas.annotations.responses.
                                  ApiResponse(responseCode = "default", description = "Returns created Variable")
                  })
  public ResponseDTO<VariableResponseDTO>
  create(@RequestBody(required = true,
          description = "Details of the Variable to create") @Valid @NotNull VariableDTO variable,
         @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, variable.getVariableInfo().getOrgIdentifier(),
            variable.getVariableInfo().getProjectIdentifier()),
        Resource.of(ResourceTypes.VARIABLE, variable.getVariableInfo().getIdentifier()), VariablesAccessControlPermissions.EDIT_VARIABLE_PERMISSION);
    return ResponseDTO.newResponse(variableService.create(variable, accountIdentifier));
  }
}
