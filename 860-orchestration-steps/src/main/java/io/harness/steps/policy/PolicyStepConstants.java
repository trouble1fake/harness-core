package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public interface PolicyStepConstants {
  String POLICY_STEP_NAME = "Policy";
  String POLICY_STEP_CATEGORY = "Governance";
  String POLICY_STEP_FOLDER_PATH = "Governance";

  String CUSTOM_POLICY_STEP_TYPE = "Custom";
}
