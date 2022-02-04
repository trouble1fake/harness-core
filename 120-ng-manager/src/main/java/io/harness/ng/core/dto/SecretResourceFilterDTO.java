/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorCategory;
import io.harness.secretmanagerclient.SecretType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PL)
@Schema(name = "SecretResourceFilter", description = "This has the filter information for the Secret in Harness.")
public class SecretResourceFilterDTO {
  @Schema(description = NGResourceFilterConstants.IDENTIFIER_LIST) List<String> identifiers;
  @Schema(description = NGResourceFilterConstants.SEARCH_TERM) String searchTerm;
  @Schema(description = NGResourceFilterConstants.TYPE_LIST) List<SecretType> secretTypes;
  ConnectorCategory sourceCategory;
  @Schema(description = "This is true if secrets are filtered at every subsequent scope. Otherwise, it is false.")
  boolean includeSecretsFromEverySubScope;
}
