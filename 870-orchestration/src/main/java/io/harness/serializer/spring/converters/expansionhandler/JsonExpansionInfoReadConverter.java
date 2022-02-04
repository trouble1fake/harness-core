/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.expansionhandler;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.serializer.spring.ProtoReadConverter;

@OwnedBy(PIPELINE)
public class JsonExpansionInfoReadConverter extends ProtoReadConverter<JsonExpansionInfo> {
  public JsonExpansionInfoReadConverter() {
    super(JsonExpansionInfo.class);
  }
}
