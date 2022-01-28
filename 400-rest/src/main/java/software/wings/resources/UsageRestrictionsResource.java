/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;
import static software.wings.service.intfc.UsageRestrictionsService.UsageRestrictionsClient.ALL;
import static software.wings.service.intfc.UsageRestrictionsService.UsageRestrictionsClient.CONNECTORS;
import static software.wings.service.intfc.UsageRestrictionsService.UsageRestrictionsClient.SECRETS_MANAGEMENT;

import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;

import software.wings.beans.User;
import software.wings.beans.security.restrictions.RestrictionsSummary;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UsageRestrictions;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UsageRestrictionsService.UsageRestrictionsClient;
import software.wings.service.intfc.UserService;
import software.wings.settings.UsageRestrictionsReferenceSummary;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Usage Restrictions Resource class.
 *
 * @author rktummala
 */
@Api("usageRestrictions")
@Path("/usageRestrictions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Scope(ResourceType.USER)
public class UsageRestrictionsResource {
  private final UsageRestrictionsService usageRestrictionsService;
  private final UserService userService;

  /**
   * Instantiates a new Usage restrictions resource.
   *
   * @param usageRestrictionsService    the usageRestrictionsService
   */
  @Inject
  public UsageRestrictionsResource(UsageRestrictionsService usageRestrictionsService, UserService userService) {
    this.usageRestrictionsService = usageRestrictionsService;
    this.userService = userService;
  }

  /**
   * List the apps that the user has env update access.
   *
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("apps")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<RestrictionsSummary> listAppsWithEnvUpdatePermissions(
      @QueryParam("accountId") @NotEmpty String accountId) {
    RestrictionsSummary restrictionsSummary = usageRestrictionsService.listAppsWithEnvUpdatePermissions(accountId);
    return new RestResponse<>(restrictionsSummary);
  }

  /**
   * List the apps that the user has env update access.
   *
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("defaultRestrictions")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<UsageRestrictions> getDefaultRestrictions(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("appId") String appId, @QueryParam("envId") String envId) {
    UsageRestrictions defaultUsageRestrictions =
        usageRestrictionsService.getDefaultUsageRestrictions(accountId, appId, envId);
    return new RestResponse<>(defaultUsageRestrictions);
  }

  /**
   * List the apps that the user has env update access.
   *
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("editable/{entityId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> isEditable(@QueryParam("accountId") @NotEmpty String accountId,
      @PathParam("entityId") @NotEmpty String entityId, @QueryParam("entityType") @NotEmpty String entityType) {
    boolean isEditable = usageRestrictionsService.isEditable(accountId, entityId, entityType);
    return new RestResponse<>(isEditable);
  }

  @DELETE
  @Path("references/connectors")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> purgeDanglingReferencesForConnectors(
      @QueryParam("accountId") @NotEmpty String accountId) {
    verifyUserHasAccessToPurge(accountId, CONNECTORS);
    int count = usageRestrictionsService.purgeDanglingAppEnvReferences(accountId, CONNECTORS);
    return new RestResponse<>(count > 0);
  }

  @DELETE
  @Path("references/secrets")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> purgeDanglingReferencesForSecrets(@QueryParam("accountId") @NotEmpty String accountId) {
    verifyUserHasAccessToPurge(accountId, SECRETS_MANAGEMENT);
    int count = usageRestrictionsService.purgeDanglingAppEnvReferences(accountId, SECRETS_MANAGEMENT);
    return new RestResponse<>(count > 0);
  }

  @DELETE
  @Path("references/all")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Boolean> purgeDanglingReferences(@QueryParam("accountId") @NotEmpty String accountId) {
    verifyUserHasAccessToPurge(accountId, ALL);
    int count = usageRestrictionsService.purgeDanglingAppEnvReferences(accountId, ALL);
    return new RestResponse<>(count > 0);
  }

  @GET
  @Path("appReferences/{appId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<UsageRestrictionsReferenceSummary> getReferenceSummaryForApp(
      @QueryParam("accountId") @NotEmpty String accountId, @PathParam("appId") String appId) {
    return new RestResponse<>(usageRestrictionsService.getReferenceSummaryForApp(accountId, appId));
  }

  @GET
  @Path("envReferences/{envId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<UsageRestrictionsReferenceSummary> getReferenceSummaryForEnv(
      @QueryParam("accountId") @NotEmpty String accountId, @PathParam("envId") String envId) {
    return new RestResponse<>(usageRestrictionsService.getReferenceSummaryForEnv(accountId, envId));
  }

  private void verifyUserHasAccessToPurge(String accountId, UsageRestrictionsClient usageRestrictionsClient) {
    User user = UserThreadLocal.get();
    if (user == null) {
      throw new InvalidRequestException("Invalid User");
    }
    if (CONNECTORS.equals(usageRestrictionsClient) || ALL.equals(usageRestrictionsClient)) {
      if (!userService.hasPermission(accountId, MANAGE_CONNECTORS)
          || !userService.hasPermission(accountId, MANAGE_CLOUD_PROVIDERS)) {
        throw new InvalidRequestException(
            "User does not have permission to manage either connectors and/or cloud providers");
      }
    }
    if (SECRETS_MANAGEMENT.equals(usageRestrictionsClient) || ALL.equals(usageRestrictionsClient)) {
      if (!userService.hasPermission(accountId, MANAGE_SECRET_MANAGERS)
          || !userService.hasPermission(accountId, MANAGE_SECRET_MANAGERS)) {
        throw new InvalidRequestException(
            "User does not have permission to manage either secrets and/or secrets managers");
      }
    }
    if (!user.getUserRequestContext().getUserPermissionInfo().isHasAllAppAccess()) {
      throw new InvalidRequestException("User should have access to all applications to do this operation.");
    }
  }
}
