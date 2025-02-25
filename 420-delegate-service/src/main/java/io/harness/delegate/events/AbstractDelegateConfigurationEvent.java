/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.events;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.ResourceScope;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public abstract class AbstractDelegateConfigurationEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;

  @Override
  public ResourceScope getResourceScope() {
    Preconditions.checkNotNull(accountIdentifier);
    if (isNotEmpty(projectIdentifier) && isNotEmpty(orgIdentifier)) {
      return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier);
    }

    if (isEmpty(projectIdentifier) && isNotEmpty(orgIdentifier)) {
      return new OrgScope(accountIdentifier, orgIdentifier);
    }

    return new AccountScope(accountIdentifier);
  }
}
