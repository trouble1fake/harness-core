/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifactory.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FetchFilesResult extends ArtifactoryBaseResult {

    // The git version contains more parameters I am simplying it out so far just to see what we will need
    // to put there later on

    @Builder
    public FetchFilesResult(String accountId) {
        super(accountId);

    }
}
