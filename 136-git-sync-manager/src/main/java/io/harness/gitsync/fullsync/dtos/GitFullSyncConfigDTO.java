/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.fullsync.dtos;

import io.harness.NGCommonEntityConstants;
import io.harness.gitsync.sdk.GitSyncApiConstants;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

// This class is being used as response dto for createFullSyncConfig request, comments are accordingly given

// I guess it is a common struct used for both request and response, which should never be the case.
// Currently, all descriptions seem off as they can't align with both request and response at same time
// Need to discuss, review and fix it
@Data
@Builder
@Schema(name = "GitFullSyncConfig", description = "This has config details specific to Git Full Sync with Harness")
public class GitFullSyncConfigDTO {
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) private String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) private String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) private String projectIdentifier;
  @Schema(description = GitSyncApiConstants.DEFAULT_BRANCH_PARAM_MESSAGE) private String baseBranch;
  // Entities should start with small letter
  @Schema(description = "Branch on which Entities will be pushed") private String branch;
  @Schema(description = "PR Title") private String prTitle;
  // Why are we specifying default value in a response dto?
  @Schema(description = "This checks whether to create a pull request. Its default value is False")
  private boolean createPullRequest;
  @Schema(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE) private String repoIdentifier;
  // Wrong way of description for a response
  // Why is this field required in response?
  @Schema(description = "Checks the new Branch") boolean isNewBranch;
  @Schema(description = "Target Branch for pull request") String targetBranch;
  // Again, wrong way of description for a response
  @Schema(description = "Root Folder Path where entities will be pushed") String rootFolder;
}
