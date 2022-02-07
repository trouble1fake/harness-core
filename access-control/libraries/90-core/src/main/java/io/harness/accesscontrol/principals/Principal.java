/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
@Value
@Builder
@EqualsAndHashCode
public class Principal {
  @NotNull PrincipalType principalType;
  @NotEmpty String principalIdentifier;
}
