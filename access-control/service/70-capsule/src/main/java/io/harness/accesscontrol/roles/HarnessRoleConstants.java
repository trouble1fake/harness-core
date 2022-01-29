/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class HarnessRoleConstants {
  public static final String ACCOUNT_ADMIN_ROLE = "_account_admin";
  public static final String ACCOUNT_VIEWER_ROLE = "_account_viewer";
  public static final String ORGANIZATION_ADMIN_ROLE = "_organization_admin";
  public static final String ORGANIZATION_VIEWER_ROLE = "_organization_viewer";
  public static final String PROJECT_ADMIN_ROLE = "_project_admin";
  public static final String PROJECT_VIEWER_ROLE = "_project_viewer";
}
