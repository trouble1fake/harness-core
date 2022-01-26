/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.utils;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitSyncConstants;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class GitSyncSdkUtils {
  public GitEntityFilePath getRootFolderAndFilePath(String completeFilePath) {
    String[] pathSplited = emptyIfNull(completeFilePath).split(GitSyncConstants.FOLDER_PATH);
    if (pathSplited.length != 2) {
      throw new InvalidRequestException(String.format(
          "The path %s doesn't contain the .harness folder, thus this file won't be processed", completeFilePath));
    }

    String folderPath = getGitFolderPath(pathSplited[0]);
    String filePath = pathSplited[1];
    return GitEntityFilePath.builder().rootFolder(folderPath).filePath(filePath).build();
  }

  private String getGitFolderPath(String folderPath) {
    String prefix = "";
    if (isBlank(folderPath) || folderPath.charAt(0) != '/') {
      prefix = "/";
    }

    return prefix + folderPath + GitSyncConstants.FOLDER_PATH;
  }
}
