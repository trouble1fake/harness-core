/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
@ToString(exclude = {"fileContent"})
public class FileData implements NestedAnnotationResolver {
  String filePath;
  byte[] fileBytes;
  String fileName;
  @Expression(ALLOW_SECRETS) String fileContent;
}
