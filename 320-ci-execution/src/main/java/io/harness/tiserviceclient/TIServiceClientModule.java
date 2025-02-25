/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.tiserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.TIServiceConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;

@OwnedBy(HarnessTeam.CI)
public class TIServiceClientModule extends AbstractModule {
  TIServiceConfig tiServiceConfig;

  @Inject
  public TIServiceClientModule(TIServiceConfig tiServiceConfig) {
    this.tiServiceConfig = tiServiceConfig;
  }

  @Override
  protected void configure() {
    this.bind(TIServiceConfig.class).toInstance(this.tiServiceConfig);
    this.bind(TIServiceClient.class).toProvider(TIServiceClientFactory.class).in(Scopes.SINGLETON);
  }
}
