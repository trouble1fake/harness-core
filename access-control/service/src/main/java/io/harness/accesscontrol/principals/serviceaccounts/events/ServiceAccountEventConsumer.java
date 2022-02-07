/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts.events;

import io.harness.accesscontrol.commons.events.EventConsumer;
import io.harness.accesscontrol.commons.events.EventFilter;
import io.harness.accesscontrol.commons.events.EventHandler;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class ServiceAccountEventConsumer implements EventConsumer {
  private final ServiceAccountEventFilter serviceAccountEventFilter;
  private final ServiceAccountEventHandler serviceAccountEventHandler;

  @Inject
  public ServiceAccountEventConsumer(
      ServiceAccountEventFilter serviceAccountEventFilter, ServiceAccountEventHandler serviceAccountEventHandler) {
    this.serviceAccountEventFilter = serviceAccountEventFilter;
    this.serviceAccountEventHandler = serviceAccountEventHandler;
  }

  @Override
  public EventFilter getEventFilter() {
    return serviceAccountEventFilter;
  }

  @Override
  public EventHandler getEventHandler() {
    return serviceAccountEventHandler;
  }
}
