/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.accountSettings.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.ng.accountSettings.services.NGAccountSettingService;
import io.harness.ng.core.account.AccountSettingResponseDTO;
import io.harness.ng.core.account.AccountSettings;
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
import javax.ws.rs.*;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

@Api("/account-setting")
@Path("/account-setting")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Tag(name = "AccountSetting", description = "This contains APIs related to Account Settings as defined in Harness")
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
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AccountSettingResource {
  //    NGAccountSettingService accountSettingService;
  //
  //    @PUT
  //    @ApiOperation(value = "Updates Account Settings", nickname = "updateAccountSettings")
  //    @Operation(operationId = "updateAccountSettings", summary = "Updates Account Settings",
  //            responses =
  //                    {
  //                            @io.swagger.v3.oas.annotations.responses.
  //                                    ApiResponse(responseCode = "default", description = "Returns the updated Account
  //                                    Setting.")
  //                    })
  //    public ResponseDTO<AccountSettingResponseDTO>
  //    update(
  //            @RequestBody(required = true,
  //                    description =
  //                            "This is the updated Account Setting. Please provide values for all fields, not just the
  //                            fields you are updating")
  //            @NotNull @Valid AccountSettings accountSettings,
  //            @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
  //                    NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
  //            ) {
  //
  //        return ResponseDTO.newResponse(accountSettingService.update(accountSettings, accountIdentifier));
  //    }
}
