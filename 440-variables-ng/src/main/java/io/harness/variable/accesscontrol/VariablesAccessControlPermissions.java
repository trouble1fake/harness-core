/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.variable.accesscontrol;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class VariablesAccessControlPermissions {
    public static final String VIEW_VARIABLE_PERMISSION = "core_variable_view";
    public static final String EDIT_VARIABLE_PERMISSION = "core_variable_edit";
    public static final String DELETE_VARIABLE_PERMISSION = "core_variable_delete";
    public static final String ACCESS_VARIABLE_PERMISSION = "core_variable_access";
}
