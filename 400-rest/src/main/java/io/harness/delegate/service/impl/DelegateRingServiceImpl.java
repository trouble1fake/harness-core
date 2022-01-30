/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.delegate.service.intfc.DelegateRingService;
import io.harness.persistence.HPersistence;

import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;

public class DelegateRingServiceImpl implements DelegateRingService {
  private final HPersistence persistence;
  private final AccountService accountService;

  @Inject
  public DelegateRingServiceImpl(HPersistence persistence, AccountService accountService) {
    this.persistence = persistence;
    this.accountService = accountService;
  }

  @Override
  public String getDelegateImageTag(String accountId) {
    DelegateRing delegateRing = getDelegateRing(accountId);
    return delegateRing.getDelegateImageTag();
  }

  @Override
  public String getUpgraderImageTag(String accountId) {
    DelegateRing delegateRing = getDelegateRing(accountId);
    return delegateRing.getUpgraderImageTag();
  }

  @Override
  public DelegateConfiguration getDelegateConfiguration(String accountId) {
    DelegateRing delegateRing = getDelegateRing(accountId);
    return delegateRing.getDelegateConfiguration();
  }

  private DelegateRing getDelegateRing(String accountId) {
    String ringName = accountService.get(accountId).getRingName();
    return persistence.createQuery(DelegateRing.class).filter(DelegateRingKeys.ringName, ringName).get();
  }
}
