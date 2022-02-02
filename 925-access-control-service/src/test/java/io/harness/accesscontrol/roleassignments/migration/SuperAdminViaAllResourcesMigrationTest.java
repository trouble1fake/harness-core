/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Mockito.mock;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class SuperAdminViaAllResourcesMigrationTest extends AccessControlTestBase {
  @Inject private RoleAssignmentRepository roleAssignmentRepository;
  @Mock private HarnessResourceGroupService harnessResourceGroupService;
  @Inject private ResourceGroupService resourceGroupService;
  @Inject private ResourceGroupRepository resourceGroupRepository;
  private SuperAdminViaAllResourcesMigration superAdminViaAllResourcesMigration;
  private static final String ACCOUNT_SCOPE_LEVEL = "account";
  private static final String ORGANIZATION_SCOPE_LEVEL = "organization";
  private static final String ACCOUNT_ADMIN_ROLE = "_account_admin";
  private static final String ORGANIZATION_ADMIN_ROLE = "_organization_admin";
  private static final String ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_account_level_resources";
  private static final String ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_organization_level_resources";
  private static final String ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER =
      "_all_resources_including_child_scopes";

  @Before
  public void setup() {
    harnessResourceGroupService = mock(HarnessResourceGroupService.class);
    superAdminViaAllResourcesMigration = new SuperAdminViaAllResourcesMigration(
        roleAssignmentRepository, harnessResourceGroupService, resourceGroupService);
    resourceGroupRepository.save(ResourceGroupDBO.builder()
                                     .id(randomAlphabetic(10))
                                     .identifier(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER)
                                     .resourceSelectors(Sets.newHashSet("/**/*/*"))
                                     .allowedScopeLevels(Sets.newHashSet(ACCOUNT_SCOPE_LEVEL, ORGANIZATION_SCOPE_LEVEL))
                                     .managed(true)
                                     .build());
  }

  private RoleAssignmentDBO getRoleAssignmentDBO(
      String roleIdentifier, String resourceGroupIdentifier, String scopeLevel) {
    return RoleAssignmentDBO.builder()
        .id(randomAlphabetic(10))
        .identifier(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .scopeLevel(scopeLevel)
        .roleIdentifier(roleIdentifier)
        .principalType(PrincipalType.USER)
        .principalIdentifier(randomAlphabetic(10))
        .resourceGroupIdentifier(resourceGroupIdentifier)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testMigrateAccountLevel() {
    testMigrateInternal(ACCOUNT_ADMIN_ROLE, ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER, ACCOUNT_SCOPE_LEVEL);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testMigrateOrganizationLevel() {
    testMigrateInternal(
        ORGANIZATION_ADMIN_ROLE, ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER, ORGANIZATION_SCOPE_LEVEL);
  }

  private void testMigrateInternal(String roleIdentifier, String resourceGroupIdentifier, String scopeLevel) {
    RoleAssignmentDBO roleAssignmentDBO = getRoleAssignmentDBO(roleIdentifier, resourceGroupIdentifier, scopeLevel);
    roleAssignmentRepository.save(roleAssignmentDBO);
    superAdminViaAllResourcesMigration.migrate();

    Criteria newAdminCriteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier)
                                    .is(roleIdentifier)
                                    .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                                    .is(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER);
    List<RoleAssignmentDBO> roleAssignmentDBOList =
        roleAssignmentRepository.findAll(newAdminCriteria, PageRequest.of(0, 1000)).getContent();
    assertEquals(1, roleAssignmentDBOList.size());
    RoleAssignmentDBO savedDBO = roleAssignmentDBOList.get(0);
    assertEquals(roleAssignmentDBO.getPrincipalIdentifier(), savedDBO.getPrincipalIdentifier());
    assertEquals(roleAssignmentDBO.getPrincipalType(), savedDBO.getPrincipalType());
    assertEquals(roleAssignmentDBO.getRoleIdentifier(), savedDBO.getRoleIdentifier());
    assertEquals(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER, savedDBO.getResourceGroupIdentifier());
    assertFalse(roleAssignmentDBO.getIdentifier().equals(savedDBO.getIdentifier()));
  }
}
