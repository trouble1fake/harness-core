/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.impl.AuditPermissionValidator;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.AuditFilterPropertiesDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.audit.mapper.AuditEventMapper;
import io.harness.beans.SortOrder;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;

import com.google.common.collect.ImmutableList;
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
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@Api("audits")
@Path("audits")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Audit", description = "This contains APIs related to Audit as defined in Harness")
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
@Slf4j
public class AuditResource {
  private final AuditService auditService;

  @Inject
  public AuditResource(AuditService auditService, AuditPermissionValidator auditPermissionValidator) {
    this.auditService = auditService;
    this.auditPermissionValidator = auditPermissionValidator;
  }

  private final AuditPermissionValidator auditPermissionValidator;

  @POST
  @ApiOperation(hidden = true, value = "Create an Audit", nickname = "postAudit")
  @Operation(operationId = "postAuditEvent", summary = "Creates an Audit Event",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns True if an Audit Event is successfully created. Else, it returns false.")
      },
      hidden = true)
  @InternalApi
  public ResponseDTO<Boolean>
  create(@RequestBody(description = "This has the information to create an Audit event.",
      required = true) @NotNull @Valid AuditEventDTO auditEventDTO) {
    long startTime = System.currentTimeMillis();
    ResponseDTO<Boolean> response = ResponseDTO.newResponse(auditService.create(auditEventDTO));
    log.info(String.format("Took %d milliseconds for create audit api for insertId %s.",
        System.currentTimeMillis() - startTime, auditEventDTO.getInsertId()));
    return response;
  }

  @POST
  @Path("/list")
  @ApiOperation(value = "Get Audit Event list", nickname = "getAuditEventList")
  @Operation(operationId = "getAuditEventList", summary = "List Audit Events",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "This has a paginated list of Audit Events.")
      })
  public ResponseDTO<PageResponse<AuditEventDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
       @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @BeanParam PageRequest pageRequest,
      @RequestBody(description = "This has the filter attributes for listing Audit Events")
      AuditFilterPropertiesDTO auditFilterPropertiesDTO) {
    long startTime = System.currentTimeMillis();
    if (auditFilterPropertiesDTO != null && isNotEmpty(auditFilterPropertiesDTO.getScopes())) {
      for (ResourceScopeDTO resourceScopeDTO : auditFilterPropertiesDTO.getScopes()) {
        auditPermissionValidator.validate(accountIdentifier, resourceScopeDTO);
      }
    } else {
      auditPermissionValidator.validate(
          accountIdentifier, ResourceScopeDTO.builder().accountIdentifier(accountIdentifier).build());
    }
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order = SortOrder.Builder.aSortOrder().withField(AuditEventKeys.timestamp, DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    Page<AuditEventDTO> audits =
        auditService.list(accountIdentifier, pageRequest, auditFilterPropertiesDTO).map(AuditEventMapper::toDTO);
    ResponseDTO<PageResponse<AuditEventDTO>> response = ResponseDTO.newResponse(getNGPageResponse(audits));
    log.info(String.format("Took %d milliseconds for list audit api.", System.currentTimeMillis() - startTime));
    return response;
  }
}
