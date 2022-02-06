/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.harness;

import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ACCOUNT_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ORG_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.PROJECT_LEVEL_PARAM_NAME;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.annotations.dev.OwnedBy;

import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
@ValidateOnExecution
public class ScopeMapper {
  public static ScopeDTO toDTO(Scope scope) {
    if (scope == null) {
      return null;
    }
    HarnessScopeParams harnessScopeParams = toParams(scope);
    return ScopeDTO.builder()
        .accountIdentifier(harnessScopeParams.getAccountIdentifier())
        .orgIdentifier(harnessScopeParams.getOrgIdentifier())
        .projectIdentifier(harnessScopeParams.getProjectIdentifier())
        .build();
  }

  public static HarnessScopeParams toParams(@Valid Scope scope) {
    Map<String, String> params = new HashMap<>();
    Scope currentScope = scope;
    while (currentScope != null) {
      ScopeLevel scopeLevel = currentScope.getLevel();
      if (scopeLevel instanceof HarnessScopeLevel) {
        HarnessScopeLevel harnessScopeLevel = (HarnessScopeLevel) scopeLevel;
        params.put(harnessScopeLevel.getParamName(), currentScope.getInstanceId());
      }
      currentScope = currentScope.getParentScope();
    }
    return HarnessScopeParams.builder()
        .accountIdentifier(params.get(ACCOUNT_LEVEL_PARAM_NAME))
        .orgIdentifier(params.get(ORG_LEVEL_PARAM_NAME))
        .projectIdentifier(params.get(PROJECT_LEVEL_PARAM_NAME))
        .build();
  }

  public static Scope fromParams(@Valid @NotNull HarnessScopeParams harnessScopeParams) {
    Scope scope = null;
    if (isNotEmpty(harnessScopeParams.getAccountIdentifier())) {
      scope = Scope.builder()
                  .instanceId(harnessScopeParams.getAccountIdentifier())
                  .level(HarnessScopeLevel.ACCOUNT)
                  .build();
    }
    if (isNotEmpty(harnessScopeParams.getOrgIdentifier())) {
      scope = Scope.builder()
                  .instanceId(harnessScopeParams.getOrgIdentifier())
                  .level(HarnessScopeLevel.ORGANIZATION)
                  .parentScope(scope)
                  .build();
    }
    if (isNotEmpty(harnessScopeParams.getProjectIdentifier())) {
      scope = Scope.builder()
                  .instanceId(harnessScopeParams.getProjectIdentifier())
                  .level(HarnessScopeLevel.PROJECT)
                  .parentScope(scope)
                  .build();
    }
    return scope;
  }
}
