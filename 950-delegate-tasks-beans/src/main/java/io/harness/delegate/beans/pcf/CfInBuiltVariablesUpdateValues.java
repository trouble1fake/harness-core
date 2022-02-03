package io.harness.delegate.beans.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class CfInBuiltVariablesUpdateValues {
  private String newAppName;
  private String newAppGuid;
  private String oldAppName;
  private String oldAppGuid;
}
