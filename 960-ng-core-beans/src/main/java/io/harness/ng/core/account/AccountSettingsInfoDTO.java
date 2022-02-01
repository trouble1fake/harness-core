/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.account;

import io.harness.NGCommonEntityConstants;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

public class AccountSettingsInfoDTO {
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotEmpty String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;

  AccountSettingType type;

  @Valid @NotNull AccountSettingConfig accountSettingConfig;
}
