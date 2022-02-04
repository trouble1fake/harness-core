/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.security.SecurityContextBuilder.EMAIL;
import static io.harness.security.SecurityContextBuilder.PRINCIPAL_NAME;
import static io.harness.security.SecurityContextBuilder.PRINCIPAL_TYPE;
import static io.harness.security.SecurityContextBuilder.USERNAME;
import static io.harness.security.dto.PrincipalType.SERVICE_ACCOUNT;

import io.harness.annotations.dev.OwnedBy;

import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SERVICE_ACCOUNT")
@TypeAlias("ServiceAccountPrincipal")
public class ServiceAccountPrincipal extends Principal {
  String email;
  String username;

  public ServiceAccountPrincipal(String name, String email, String username) {
    this.type = SERVICE_ACCOUNT;
    this.name = name;
    this.email = email;
    this.username = username;
  }

  @Override
  public Map<String, String> getJWTClaims() {
    Map<String, String> claims = new HashMap<>();
    claims.put(PRINCIPAL_TYPE, getType().toString());
    claims.put(PRINCIPAL_NAME, getName());
    claims.put(EMAIL, getEmail());
    claims.put(USERNAME, getUsername());
    return claims;
  }

  public static ServiceAccountPrincipal getPrincipal(Map<String, Claim> claims) {
    return new ServiceAccountPrincipal(
        claims.get(PRINCIPAL_NAME) == null ? null : claims.get(PRINCIPAL_NAME).asString(),
        claims.get(EMAIL) == null ? null : claims.get(EMAIL).asString(),
        claims.get(USERNAME) == null ? null : claims.get(USERNAME).asString());
  }
}
