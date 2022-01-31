/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.globalkms.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.EDIT_CONNECTOR_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_EDIT_PERMISSION;
import static io.harness.secrets.SecretPermissions.SECRET_RESOURCE_TYPE;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.clients.Resource;
import io.harness.accesscontrol.clients.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.globalkms.dto.ConnectorSecretRequestDTO;
import io.harness.ng.core.globalkms.dto.ConnectorSecretResponseDTO;
import io.harness.ng.core.globalkms.services.GlobalKmsService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@Slf4j
@OwnedBy(HarnessTeam.PL)
@Api("globalKms")
@Path("/globalKms")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "GlobalKms", description = "This contains APIs related to Harness inbuilt global GCP KMS secret manger")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class)) })
@Hidden
public class NgGlobalKmsResource {
  private final GlobalKmsService globalKmsService;
  private final AccessControlClient accessControlClient;

  @Inject
  public NgGlobalKmsResource(GlobalKmsService globalKmsService, AccessControlClient accessControlClient) {
    this.globalKmsService = globalKmsService;
    this.accessControlClient = accessControlClient;
  }

  @PUT
  @ApiOperation(
      value = "Updates the Harness global GCP KMS Connector", nickname = "updateGcpKmsConnector", hidden = true)
  @Operation(operationId = "updateGcpKmsConnector", summary = "Updates the Harness Global GCP KMS Connector",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Connector")
      },
      hidden = true)
  @Hidden
  public ResponseDTO<ConnectorSecretResponseDTO>
  update(@RequestBody(required = true,
             description = "Details of the Connector and Secret") @Valid @NotNull ConnectorSecretRequestDTO dto,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, dto.getConnector().getConnectorInfo().getOrgIdentifier(),
            dto.getConnector().getConnectorInfo().getProjectIdentifier()),
        Resource.of(ResourceTypes.CONNECTOR, dto.getConnector().getConnectorInfo().getIdentifier()),
        EDIT_CONNECTOR_PERMISSION);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, dto.getSecret().getOrgIdentifier(), dto.getSecret().getProjectIdentifier()),
        Resource.of(SECRET_RESOURCE_TYPE, dto.getSecret().getIdentifier()), SECRET_EDIT_PERMISSION);
    return ResponseDTO.newResponse(globalKmsService.updateGlobalKms(dto.getConnector(), dto.getSecret()));
  }
}
