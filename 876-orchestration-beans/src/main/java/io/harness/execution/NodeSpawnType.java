package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public enum NodeSpawnType {
  CHILD,
  CHILDREN,
  DEFAULT;
}
