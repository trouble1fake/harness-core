/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ComparatorUtils;

import java.io.Serializable;
import java.util.Comparator;

@OwnedBy(CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
public class AlphanumComparator implements Comparator<String>, Serializable {
  @Override
  public int compare(String s1, String s2) {
    return ComparatorUtils.compare(s1, s2);
  }
}
