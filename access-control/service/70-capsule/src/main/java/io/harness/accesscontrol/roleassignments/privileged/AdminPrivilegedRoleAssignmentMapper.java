/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.privileged;

import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;

import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.ImmutableList;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class AdminPrivilegedRoleAssignmentMapper {
  public static final Map<String, String> roleToPrivilegedRole =
      Stream
          .of(new AbstractMap.SimpleEntry<>("_account_admin", "_super_account_admin"),
              new AbstractMap.SimpleEntry<>("_organization_admin", "_super_organization_admin"))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  public static final String ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP =
      "_all_resources_including_child_scopes";
  public static final String DEPRECATED_ALL_RESOURCES_RESOURCE_GROUP = "_all_resources";
  public static final List<String> MANAGED_RESOURCE_GROUP_IDENTIFIERS = ImmutableList.of(
      ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER,
      DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER, DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);

  public static Optional<PrivilegedRoleAssignment> buildAdminPrivilegedRoleAssignment(
      RoleAssignmentDBO roleAssignment) {
    if (roleToPrivilegedRole.containsKey(roleAssignment.getRoleIdentifier())
        && MANAGED_RESOURCE_GROUP_IDENTIFIERS.stream().anyMatch(
            resourceGroupIdentifier -> resourceGroupIdentifier.equals(roleAssignment.getResourceGroupIdentifier()))) {
      PrivilegedRoleAssignment privilegedRoleAssignment =
          PrivilegedRoleAssignment.builder()
              .principalIdentifier(roleAssignment.getPrincipalIdentifier())
              .principalType(roleAssignment.getPrincipalType())
              .roleIdentifier(roleToPrivilegedRole.get(roleAssignment.getRoleIdentifier()))
              .scopeIdentifier(roleAssignment.getScopeIdentifier())
              .linkedRoleAssignment(roleAssignment.getId())
              .managed(false)
              .global(false)
              .build();
      return Optional.of(privilegedRoleAssignment);
    }
    return Optional.empty();
  }
}
