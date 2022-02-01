/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accountSettings.resources;

import static io.harness.NGCommonEntityConstants.*;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.accountSettings.services.NGAccountSettingService;
import io.harness.ng.core.account.AccountSettingsDTO;
import io.harness.ng.core.account.AccountSettingsInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;

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
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//@Api("/account-setting")
//@Path("/account-setting")
//@Produces({"application/json", "text/yaml", "text/html"})
//@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
//@Tag(name = "AccountSetting", description = "This contains APIs related to Account Settings as defined in Harness")
//@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
//    content =
//    {
//      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
//      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
//    })
//@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
//    content =
//    {
//      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
//      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
//    })
//@ApiResponses(value =
//    {
//      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
//      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
//    })
//
@Api("account-setting")
@Path("account-setting")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
@NextGenManagerAuth
//@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AccountSettingResource {
  NGAccountSettingService accountSettingService;

  @GET
  @Path("/details")
  @ApiOperation(value = "Gets docker build details", nickname = "getBuildDetailsForDocker")
  public ResponseDTO<DockerResponseDTO> getBuildDetails(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(null);
  }

  @POST
  @ApiOperation(value = "Create a account setting", nickname = "postAccountSetting")
  @Operation(operationId = "postAccountSetting", summary = "Creates a account setting",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created account setting")
      })
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public ResponseDTO<AccountSettingsInfoDTO>
  create(@RequestBody(required = true, description = "Details of the ACcountSetting to create") @Valid
         @NotNull AccountSettingsDTO accountSettingsDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier

  ) {
    return ResponseDTO.newResponse(accountSettingService.create(accountIdentifier, accountSettingsDTO));
  }
}
