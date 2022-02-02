/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.filter;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static javax.ws.rs.Priorities.AUTHENTICATION;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION - 1)
@Slf4j
public class APIAuthTelemetryFilter implements ContainerRequestFilter {
  public static final String X_API_KEY = "X-Api-Key";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String BEARER = "Bearer";
  public static final String UNKNOWN = "Unknown";
  public static final String AUTH_TYPE = "auth_type";
  public static final String ACCOUNT_ID = "account_id";
  public static final String API_ENDPOINT = "api_endpoint";
  public static final String API_ENDPOINTS_AUTH_SCHEMES = "api_endpoints_auth_schemes";

  private TelemetryReporter telemetryReporter;
  private ExecutorService executorService;

  @Context @Setter @VisibleForTesting private ResourceInfo resourceInfo;

  public APIAuthTelemetryFilter(TelemetryReporter telemetryReporter, ExecutorService executorService) {
    this.telemetryReporter = telemetryReporter;
    this.executorService = executorService;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    Optional<String> authorizationOptional = getAuthorizationFromHeaders(containerRequestContext);
    Optional<String> apiKeyOptional = getApiKeyFromHeaders(containerRequestContext);
    Optional<String> accountIdentifierOptional = getAccountIdentifierFromUri(containerRequestContext);
    HashMap<String, Object> properties = new HashMap<>();

    if (accountIdentifierOptional.isPresent()) {
      if (authorizationOptional.isPresent()) {
        properties.put(AUTH_TYPE, BEARER);
      } else if (apiKeyOptional.isPresent()) {
        properties.put(AUTH_TYPE, X_API_KEY);
      } else {
        properties.put(AUTH_TYPE, UNKNOWN);
      }
      properties.put(ACCOUNT_ID, accountIdentifierOptional.get());
      properties.put(API_ENDPOINT, containerRequestContext.getUriInfo().getPath());

      executorService.submit(
          ()
              -> telemetryReporter.sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, accountIdentifierOptional.get(),
                  properties, Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
                  TelemetryOption.builder().sendForCommunity(false).build()));
    }
  }

  private Optional<String> getAuthorizationFromHeaders(ContainerRequestContext containerRequestContext) {
    String authorizationHeader = containerRequestContext.getHeaderString(AUTHORIZATION_HEADER);
    return StringUtils.isEmpty(authorizationHeader) ? Optional.empty() : Optional.of(authorizationHeader);
  }

  private Optional<String> getApiKeyFromHeaders(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(X_API_KEY);
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private Optional<String> getAccountIdentifierFromUri(ContainerRequestContext containerRequestContext) {
    String accountIdentifier =
        containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    return StringUtils.isEmpty(accountIdentifier) ? Optional.empty() : Optional.of(accountIdentifier);
  }
}
