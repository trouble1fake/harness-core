/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;

import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.annotations.ScimAPI;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.token.remote.TokenClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION)
@Slf4j
public class NextGenAuthenticationFilter extends JWTAuthenticationFilter {
  public static final String X_API_KEY = "X-Api-Key";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String delimiter = "\\.";

  private TokenClient tokenClient;
  @Context @Setter @VisibleForTesting private ResourceInfo resourceInfo;

  public NextGenAuthenticationFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping,
      @Named("PRIVILEGED") TokenClient tokenClient) {
    super(predicate, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
    this.tokenClient = tokenClient;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (!super.testRequestPredicate(containerRequestContext)) {
      // Predicate testing failed with the current request context
      return;
    }

    Optional<String> apiKeyOptional =
        isScimAPI() ? getApiKeyForScim(containerRequestContext) : getApiKeyFromHeaders(containerRequestContext);

    if (apiKeyOptional.isPresent()) {
      Optional<String> accountIdentifierOptional = getAccountIdentifierFromUri(containerRequestContext);
      if (!accountIdentifierOptional.isPresent()) {
        throw new InvalidRequestException("Account detail is not present in the request");
      }
      String accountIdentifier = accountIdentifierOptional.get();
      validateApiKey(accountIdentifier, apiKeyOptional.get());
    } else {
      super.filter(containerRequestContext);
    }
  }

  private boolean isScimAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ScimAPI.class) != null || resourceClass.getAnnotation(ScimAPI.class) != null;
  }

  private void validateApiKey(String accountIdentifier, String apiKey) {
    String[] splitToken = apiKey.split(delimiter);
    if (splitToken.length != 3) {
      log.warn("Token length not matching for API token");
      throw new InvalidRequestException("Invalid API Token");
    }
    if (EmptyPredicate.isNotEmpty(splitToken)) {
      TokenDTO tokenDTO = NGRestUtils.getResponse(tokenClient.getToken(splitToken[1]));

      if (tokenDTO != null) {
        if (!accountIdentifier.equals(tokenDTO.getAccountIdentifier())) {
          throw new InvalidRequestException("Invalid account token access");
        }
        if (!tokenDTO.getApiKeyType().getValue().equals(splitToken[0])) {
          log.warn("Invalid prefix for API token");
          throw new InvalidRequestException("Invalid API token");
        }
        if (!new BCryptPasswordEncoder($2A, 10).matches(splitToken[2], tokenDTO.getEncodedPassword())) {
          log.warn("Raw password not matching for API token");
          throw new InvalidRequestException("Invalid API token");
        }
        if (!tokenDTO.isValid()) {
          throw new InvalidRequestException("Incoming API token " + tokenDTO.getName() + " has expired");
        }
        Principal principal = null;
        if (tokenDTO.getApiKeyType() == ApiKeyType.SERVICE_ACCOUNT) {
          principal =
              new ServiceAccountPrincipal(tokenDTO.getParentIdentifier(), tokenDTO.getEmail(), tokenDTO.getUsername());
        }
        if (tokenDTO.getApiKeyType() == ApiKeyType.USER) {
          principal = new UserPrincipal(tokenDTO.getParentIdentifier(), tokenDTO.getEmail(), tokenDTO.getUsername(),
              tokenDTO.getAccountIdentifier());
        }
        SecurityContextBuilder.setContext(principal);
        SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      } else {
        throw new InvalidRequestException("Invalid API token");
      }
    } else {
      throw new InvalidRequestException("Invalid API token");
    }
  }

  private Optional<String> getApiKeyFromHeaders(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(X_API_KEY);
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private Optional<String> getApiKeyForScim(ContainerRequestContext containerRequestContext) {
    String apiKey = getBearerToken(containerRequestContext.getHeaderString(AUTHORIZATION_HEADER));
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private String getBearerToken(String authorizationHeader) {
    String bearerPrefix = "Bearer ";
    if (!authorizationHeader.contains(bearerPrefix)) {
      throw new UnauthorizedException("Bearer prefix not found", USER);
    }
    return authorizationHeader.substring(bearerPrefix.length()).trim();
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
