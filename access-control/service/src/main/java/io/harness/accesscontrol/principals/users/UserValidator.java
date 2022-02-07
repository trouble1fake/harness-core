/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.PrincipalValidator;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.utils.RetryUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

@OwnedBy(PL)
@Slf4j
@Singleton
public class UserValidator implements PrincipalValidator {
  private final UserService userService;

  private static final RetryPolicy<Object> retryPolicy =
      RetryUtils.getRetryPolicy("Could not find the user with the given identifier on attempt %s",
          "Could not find the user with the given identifier", Lists.newArrayList(InvalidRequestException.class),
          Duration.ofSeconds(5), 3, log);

  @Inject
  public UserValidator(UserService userService) {
    this.userService = userService;
  }

  @Override
  public PrincipalType getPrincipalType() {
    return USER;
  }

  @Override
  public ValidationResult validatePrincipal(Principal principal, String scopeIdentifier) {
    String identifier = principal.getPrincipalIdentifier();
    Optional<User> userOpt = userService.get(identifier, scopeIdentifier);
    if (userOpt.isPresent()) {
      return ValidationResult.builder().valid(true).build();
    }
    return ValidationResult.builder()
        .valid(false)
        .errorMessage(
            String.format("user not found with the given identifier %s in the scope %s", identifier, scopeIdentifier))
        .build();
  }
}
