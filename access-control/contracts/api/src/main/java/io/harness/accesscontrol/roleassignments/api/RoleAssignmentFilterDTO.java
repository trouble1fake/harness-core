/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@ApiModel(value = "RoleAssignmentFilter")
@Schema(name = "RoleAssignmentFilter")
@OwnedBy(PL)
public class RoleAssignmentFilterDTO {
  @Schema(description = "Filter role assignments based on resource group identifiers") Set<String> resourceGroupFilter;
  @Schema(description = "Filter role assignments based on role identifiers") Set<String> roleFilter;
  @Schema(description = "Filter role assignments based on principal type") Set<PrincipalType> principalTypeFilter;
  @Schema(description = "Filter role assignments based on principals") Set<PrincipalDTO> principalFilter;
  @Schema(description = "Filter role assignments based on role assignments being harness managed")
  Set<Boolean> harnessManagedFilter;
  @Schema(description = "Filter role assignments based on whether they are enabled or disabled")
  Set<Boolean> disabledFilter;
}
