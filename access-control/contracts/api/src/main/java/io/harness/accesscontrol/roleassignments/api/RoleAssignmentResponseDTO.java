/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
@ApiModel(value = "RoleAssignmentResponse")
@Schema(name = "RoleAssignmentResponse")
public class RoleAssignmentResponseDTO {
  @ApiModelProperty(required = true) RoleAssignmentDTO roleAssignment;
  @ApiModelProperty(required = true) ScopeDTO scope;
  Long createdAt;
  Long lastModifiedAt;
  boolean harnessManaged;
}
