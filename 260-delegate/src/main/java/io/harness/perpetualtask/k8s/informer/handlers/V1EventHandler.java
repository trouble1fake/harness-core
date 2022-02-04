/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.informer.handlers;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.client.EventPublisher;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;

import io.kubernetes.client.openapi.models.V1Event;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class V1EventHandler extends BaseHandler<V1Event> {
  public V1EventHandler(EventPublisher eventPublisher, ClusterDetails clusterDetails) {
    super(eventPublisher, clusterDetails);
  }

  @Override
  String getKind() {
    return "Event";
  }

  @Override
  String getApiVersion() {
    return "v1";
  }
}
