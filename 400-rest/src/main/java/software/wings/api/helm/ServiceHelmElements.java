/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.helm;

import io.harness.beans.SweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonTypeName("serviceHelmElements")
public class ServiceHelmElements implements SweepingOutput {
  public static final String SWEEPING_OUTPUT_NAME = "serviceHelmElements";

  List<ServiceHelmElement> helmElements;

  @Override
  public String getType() {
    return "serviceHelmElements";
  }
}
