/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.apis.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.DELETE_CONNECTOR_PERMISSION;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.EDIT_CONNECTOR_PERMISSION;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.VIEW_CONNECTOR_PERMISSION;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.utils.URLDecoderUtility.getDecodedString;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.clients.Resource;
import io.harness.accesscontrol.clients.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorCatalogueResponseDTO;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.connector.helper.ConnectorRbacHelper;
import io.harness.connector.services.ConnectorHeartbeatService;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.stats.ConnectorStatistics;
import io.harness.connector.utils.ConnectorAllowedFieldValues;
import io.harness.connector.utils.FieldValues;
import io.harness.data.validator.EntityIdentifier;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParameterResponse;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.CEAwsSetupConfig;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import com.google.inject.name.Named;
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
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotBlank;
import retrofit2.http.Body;

@Api("/connectors")
@Path("/connectors")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Tag(name = "Connectors", description = "This contains APIs related to Connectors as defined in Harness")
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
public class ConnectorResource {
  private static final String INCLUDE_ALL_CONNECTORS_ACCESSIBLE = "includeAllConnectorsAvailableAtScope";
  private final ConnectorService connectorService;
  private final ConnectorHeartbeatService connectorHeartbeatService;
  private final CEAwsSetupConfig ceAwsSetupConfig;
  private static final String CATEGORY_KEY = "category";
  private static final String SOURCE_CATEGORY_KEY = "source_category";
  private final AccessControlClient accessControlClient;
  private ConnectorRbacHelper connectorRbacHelper;

  @Inject
  public ConnectorResource(@Named("connectorDecoratorService") ConnectorService connectorService,
      ConnectorHeartbeatService connectorHeartbeatService, CEAwsSetupConfig ceAwsSetupConfig,
      AccessControlClient accessControlClient, ConnectorRbacHelper connectorRbacHelper) {
    this.connectorService = connectorService;
    this.connectorHeartbeatService = connectorHeartbeatService;
    this.ceAwsSetupConfig = ceAwsSetupConfig;
    this.accessControlClient = accessControlClient;
    this.connectorRbacHelper = connectorRbacHelper;
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get Connector", nickname = "getConnector")
  @Operation(operationId = "getConnector", summary = "Get the Connector by accountIdentifier and connectorIdentifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the connector with the requested accountIdentifier and connectorIdentifier")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = VIEW_CONNECTOR_PERMISSION)
  public ResponseDTO<ConnectorResponseDTO>
  get(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Connector Identifier") @EntityIdentifier @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String connectorIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    Optional<ConnectorResponseDTO> connectorResponseDTO =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
    if (!connectorResponseDTO.isPresent()) {
      throw new NotFoundException(String.format("Connector with identifier [%s] in project [%s], org [%s] not found",
          connectorIdentifier, projectIdentifier, orgIdentifier));
    }
    return ResponseDTO.newResponse(connectorResponseDTO.get());
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
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connectorIdentifier)) {
      return ResponseDTO.newResponse(false);
    }
    return ResponseDTO.newResponse(connectorService.validateTheIdentifierIsUnique(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
  }

  @GET
  @ApiOperation(value = "Gets Connector list", nickname = "getConnectorList")
  @Operation(operationId = "getConnectorList",
      summary = "Fetches the list of Connectors corresponding to the request's filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Connectors")
      })
  @Deprecated
  public ResponseDTO<PageResponse<ConnectorResponseDTO>>
  list(@Parameter(description = "Page number of navigation. The default value is 0") @QueryParam(
           NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Number of entries per page. The default value is 100") @QueryParam(
          NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") int size,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(
          description =
              "This would be used to filter Connectors. Any Connector having the specified string in its Name, ID and Tag would be filtered.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "Filter Connectors by type") @QueryParam(
          NGResourceFilterConstants.TYPE_KEY) ConnectorType type,
      @Parameter(description = "Filter Connectors by category") @QueryParam(CATEGORY_KEY) ConnectorCategory category,
      @Parameter(description = "Filter Connectors by Source Category. Available Source Categories are "
              + "CLOUD_PROVIDER, SECRET_MANAGER, CLOUD_COST, ARTIFACTORY, CODE_REPO,  "
              + "MONITORING and TICKETING") @QueryParam(SOURCE_CATEGORY_KEY) ConnectorCategory sourceCategory,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ResourceTypes.CONNECTOR, null), VIEW_CONNECTOR_PERMISSION);
    return ResponseDTO.newResponse(getNGPageResponse(connectorService.list(
        page, size, accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, type, category, sourceCategory)));
  }

  @POST
  @Path("/listV2")
  @ApiOperation(value = "Gets Connector list", nickname = "getConnectorListV2")
  @Operation(operationId = "getConnectorListV2",
      summary = "Fetches the list of Connectors corresponding to the request's filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Connectors")
      })
  public ResponseDTO<PageResponse<ConnectorResponseDTO>>
  list(@Parameter(description = "Page number of navigation. The default value is 0") @QueryParam(
           NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Number of entries per page. The default value is 100") @QueryParam(
          NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") int size,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(
          description =
              "This would be used to filter Connectors. Any Connector having the specified string in its Name, ID and Tag would be filtered.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(description = "Specify whether or not to include all the Connectors"
              + " accessible at the scope. For eg if set as true, at the Project scope we will get"
              + " org and account Connector also in the response") @QueryParam(INCLUDE_ALL_CONNECTORS_ACCESSIBLE)
      Boolean includeAllConnectorsAccessibleAtScope,
      @RequestBody(required = true, description = "Details of the filters applied")
      @Body ConnectorFilterPropertiesDTO connectorListFilter, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(
          description =
              "This when set to true along with GitSync enabled for the Connector, you can get one connector entity from each identifier. "
              + "The connector entity can belong to any branch") @QueryParam("getDistinctFromBranches")
      Boolean getDistinctFromBranches) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ResourceTypes.CONNECTOR, null), VIEW_CONNECTOR_PERMISSION);
    return ResponseDTO.newResponse(getNGPageResponse(
        connectorService.list(page, size, accountIdentifier, connectorListFilter, orgIdentifier, projectIdentifier,
            filterIdentifier, searchTerm, includeAllConnectorsAccessibleAtScope, getDistinctFromBranches)));
  }

  @POST
  @ApiOperation(value = "Creates a Connector", nickname = "createConnector")
  @Operation(operationId = "createConnector", summary = "Creates a Connector",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Connector")
      })
  public ResponseDTO<ConnectorResponseDTO>
  create(@RequestBody(required = true,
             description = "Details of the Connector to create") @Valid @NotNull ConnectorDTO connector,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, connector.getConnectorInfo().getOrgIdentifier(),
            connector.getConnectorInfo().getProjectIdentifier()),
        Resource.of(ResourceTypes.CONNECTOR, null), EDIT_CONNECTOR_PERMISSION);

    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connector.getConnectorInfo().getIdentifier())) {
      throw new InvalidRequestException(
          String.format("%s cannot be used as connector identifier", HARNESS_SECRET_MANAGER_IDENTIFIER), USER);
    }
    if (connector.getConnectorInfo().getConnectorType() == ConnectorType.LOCAL) {
      throw new InvalidRequestException("Local Secret Manager creation not supported", USER);
    }
    return ResponseDTO.newResponse(connectorService.create(connector, accountIdentifier));
  }

  @PUT
  @ApiOperation(value = "Updates a Connector", nickname = "updateConnector")
  @Operation(operationId = "updateConnector", summary = "Updates the Connector",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Connector")
      })
  public ResponseDTO<ConnectorResponseDTO>
  update(
      @RequestBody(required = true,
          description =
              "This is the updated Connector. Please provide values for all fields, not just the fields you are updating")
      @NotNull @Valid ConnectorDTO connector,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @BeanParam GitEntityUpdateInfoDTO gitEntityInfo) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, connector.getConnectorInfo().getOrgIdentifier(),
            connector.getConnectorInfo().getProjectIdentifier()),
        Resource.of(ResourceTypes.CONNECTOR, connector.getConnectorInfo().getIdentifier()), EDIT_CONNECTOR_PERMISSION);
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connector.getConnectorInfo().getIdentifier())) {
      throw new InvalidRequestException("Update operation not supported for Harness Secret Manager");
    }
    return ResponseDTO.newResponse(connectorService.update(connector, accountIdentifier));
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete a connector by identifier", nickname = "deleteConnector")
  @Operation(operationId = "deleteConnector", summary = "Deletes Connector by ID",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "It returns true if the Connector is deleted successfully and false if the Connector is not deleted")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = DELETE_CONNECTOR_PERMISSION)
  public ResponseDTO<Boolean>
  delete(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotBlank String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      @ProjectIdentifier @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Connector ID") @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @NotBlank
      @ResourceIdentifier String connectorIdentifier, @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo) {
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connectorIdentifier)) {
      throw new InvalidRequestException("Delete operation not supported for Harness Secret Manager");
    }
    return ResponseDTO.newResponse(
        connectorService.delete(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
  }

  @POST
  @Path("testConnection/{identifier}")
  @ApiOperation(value = "Test the connection", nickname = "getTestConnectionResult")
  @Operation(operationId = "getTestConnectionResult", summary = "Tests the connection of the Connector by ID",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Connector validation result")
      })
  public ResponseDTO<ConnectorValidationResult>
  testConnection(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Connector ID") @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      String connectorIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier)
        .map(connector
            -> connectorRbacHelper.checkSecretRuntimeAccessWithConnectorDTO(
                connector.getConnector(), accountIdentifier))
        .orElseThrow(()
                         -> new ConnectorNotFoundException(
                             String.format("No connector found with identifier %s", connectorIdentifier), USER));

    return ResponseDTO.newResponse(
        connectorService.testConnection(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
  }

  @POST
  @Hidden
  @InternalApi
  @Path("testConnectionInternal/{identifier}")
  @ApiOperation(value = "Test the connection internal api", nickname = "getTestConnectionResultInternal")
  @Operation(operationId = "getTestConnectionResultInternal",
      summary = "Tests the connection of the connector by Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the connector validation result")
      })
  public ResponseDTO<ConnectorValidationResult>
  testConnectionInternal(@NotBlank @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) String connectorIdentifier) {
    connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier)
        .map(connector
            -> connectorRbacHelper.checkSecretRuntimeAccessWithConnectorDTO(
                connector.getConnector(), accountIdentifier))
        .orElseThrow(()
                         -> new ConnectorNotFoundException(
                             String.format("No connector found with identifier %s", connectorIdentifier), USER));

    return ResponseDTO.newResponse(
        connectorService.testConnection(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
  }

  @POST
  @Path("testGitRepoConnection/{identifier}")
  @ApiOperation(value = "Test the connection", nickname = "getTestGitRepoConnectionResult")
  @Operation(operationId = "getTestGitRepoConnectionResult", summary = "Tests the Git Repo connection",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Connector validation result")
      })
  public ResponseDTO<ConnectorValidationResult>
  testGitRepoConnection(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "URL of the repository, specify only in the case of Account Type"
              + " Git Connector") @QueryParam(NGCommonEntityConstants.REPO_URL) String repoURL,
      @Parameter(description = "Connector ID") @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @EntityIdentifier String connectorIdentifier) {
    return ResponseDTO.newResponse(connectorService.testGitRepoConnection(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, getDecodedString(repoURL)));
  }

  @GET
  @Path("catalogue")
  @ApiOperation(value = "Get Connector Catalogue", nickname = "getConnectorCatalogue")
  @Operation(operationId = "getConnectorCatalogue", summary = "Gets the Connector catalogue by Account Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Connector catalogue response")
      })
  public ResponseDTO<ConnectorCatalogueResponseDTO>
  getConnectorCatalogue(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(connectorService.getConnectorCatalogue());
  }

  @GET
  @Path("/stats")
  @ApiOperation(value = "Get Connectors statistics", nickname = "getConnectorStatistics")
  @Operation(operationId = "getConnectorStatistics",
      summary = "Gets the connector's statistics by Account Identifier, Project Identifier and Organization Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Connector's statistics")
      })
  public ResponseDTO<ConnectorStatistics>
  getConnectorStats(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ResourceTypes.CONNECTOR, null), VIEW_CONNECTOR_PERMISSION);
    return ResponseDTO.newResponse(
        connectorService.getConnectorStatistics(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @POST
  @Path("/listbyfqn")
  @ApiOperation(value = "Gets Connector list", nickname = "listConnectorByFQN")
  @Operation(operationId = "listConnectorByFQN",
      summary = "Get the list of connectors by FQN satisfying the criteria (if any) in the request",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Connectors")
      })
  public ResponseDTO<List<ConnectorResponseDTO>>
  listConnectorByFQN(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(
          required = true, description = "List of ConnectorsFQN as strings") @Body List<String> connectorsFQN) {
    return ResponseDTO.newResponse(connectorService.listbyFQN(accountIdentifier, connectorsFQN));
  }

  @GET
  @Hidden
  @Path("{identifier}/validation-params")
  @ApiOperation(hidden = true, value = "Gets connector validation params")
  @InternalApi
  @Produces("application/x-kryo")
  public ResponseDTO<ConnectorValidationParameterResponse> getConnectorValidationParams(
      @Parameter(description = "Connector ID") @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String connectorIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    return ResponseDTO.newResponse(connectorHeartbeatService.getConnectorValidationParams(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier));
  }

  // TODO(UTSAV): will be moved to 340-ce-nextgen
  @POST
  @Path("/getceawstemplateurl")
  @ApiOperation(value = "Get CCM Aws Connector Template URL Environment Wise", nickname = "getCEAwsTemplate")
  @Operation(deprecated = true, operationId = "getCEAwsTemplate", summary = "Get the Template URL of connector",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the cloud formation template URL to configure the Cloud Cost AWS Connector")
      })
  @Deprecated
  public ResponseDTO<String>
  getCEAwsTemplate(@Parameter(description = "Specify whether or not to enable events") @QueryParam(
                       NGCommonEntityConstants.IS_EVENTS_ENABLED) Boolean eventsEnabled,
      @Parameter(description = "Specify whether or not to enable CUR") @QueryParam(
          NGCommonEntityConstants.IS_CUR_ENABLED) Boolean curEnabled,
      @Parameter(description = "Specify whether or not to enable optimization") @QueryParam(
          NGCommonEntityConstants.IS_OPTIMIZATION_ENABLED) Boolean optimizationEnabled) {
    final String templateURL = ceAwsSetupConfig.getTemplateURL();
    return ResponseDTO.newResponse(templateURL);
  }

  @GET
  @Path("/fieldValues")
  @ApiOperation(value = "Get All Allowed field values for Connector Type", nickname = "getAllAllowedFieldValues")
  @Operation(operationId = "getAllAllowedFieldValues", summary = "Get the allowed field values by Connector Type",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all settings for the Connector type")
      })
  public ResponseDTO<FieldValues>
  getAllAllowedFieldValues(@Parameter(description = "Connector type") @NotNull @QueryParam(
      NGCommonEntityConstants.CONNECTOR_TYPE) ConnectorType connectorType) {
    return ResponseDTO.newResponse(ConnectorAllowedFieldValues.TYPE_TO_FIELDS.get(connectorType));
  }
}
