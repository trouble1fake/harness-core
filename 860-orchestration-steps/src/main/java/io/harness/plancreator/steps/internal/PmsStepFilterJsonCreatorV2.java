/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(PIPELINE)
public class PmsStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.HTTP, StepSpecTypeConstants.JIRA_CREATE,
        StepSpecTypeConstants.JIRA_UPDATE, StepSpecTypeConstants.JIRA_APPROVAL, StepSpecTypeConstants.HARNESS_APPROVAL,
        StepSpecTypeConstants.SHELL_SCRIPT, StepSpecTypeConstants.SERVICENOW_APPROVAL, StepSpecTypeConstants.BARRIER);
  }
}
