/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.beans.GitBranch;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.core.beans.GitFullSyncConfig;
import io.harness.gitsync.core.beans.GitFullSyncConfig.GitFullSyncConfigKeys;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigDTO;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigRequestDTO;
import io.harness.gitsync.fullsync.remote.mappers.GitFullSyncConfigMapper;
import io.harness.repositories.fullSync.GitFullSyncConfigRepository;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class GitFullSyncConfigServiceImpl implements GitFullSyncConfigService {
  private final GitFullSyncConfigRepository gitFullSyncConfigRepository;
  private final GitBranchService gitBranchService;
  private final YamlGitConfigService yamlGitConfigService;
  private final String ERROR_MSG_WHEN_CONFIG_EXIST =
      "A full sync config already exists for this account [%s], org [%s], project [%s]";

  @Override
  public GitFullSyncConfigDTO createConfig(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, GitFullSyncConfigRequestDTO dto) {
    validateBranch(accountIdentifier, orgIdentifier, projectIdentifier, dto.getRepoIdentifier(), dto.getBranch(),
        dto.isNewBranch());
    GitFullSyncConfig gitFullSyncConfig =
        GitFullSyncConfigMapper.fromDTO(accountIdentifier, orgIdentifier, projectIdentifier, dto);
    try {
      GitFullSyncConfig savedGitFullSyncConfig = gitFullSyncConfigRepository.save(gitFullSyncConfig);
      return GitFullSyncConfigMapper.toDTO(savedGitFullSyncConfig);
    } catch (DuplicateKeyException ex) {
      log.error(String.format(ERROR_MSG_WHEN_CONFIG_EXIST, accountIdentifier, orgIdentifier, projectIdentifier));
      throw new InvalidRequestException(
          String.format(ERROR_MSG_WHEN_CONFIG_EXIST, accountIdentifier, orgIdentifier, projectIdentifier));
    }
  }

  @Override
  public Optional<GitFullSyncConfigDTO> get(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getInternal(accountIdentifier, orgIdentifier, projectIdentifier).map(GitFullSyncConfigMapper::toDTO);
  }

  private Optional<GitFullSyncConfig> getInternal(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return gitFullSyncConfigRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public GitFullSyncConfigDTO updateConfig(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, GitFullSyncConfigRequestDTO dto) {
    validateBranch(accountIdentifier, orgIdentifier, projectIdentifier, dto.getRepoIdentifier(), dto.getBranch(),
        dto.isNewBranch());
    Criteria criteria = getCriteria(accountIdentifier, orgIdentifier, projectIdentifier);

    Update update = update(GitFullSyncConfigKeys.baseBranch, dto.getBaseBranch())
                        .set(GitFullSyncConfigKeys.branch, dto.getBranch())
                        .set(GitFullSyncConfigKeys.rootFolder, dto.getRootFolder())
                        .set(GitFullSyncConfigKeys.yamlGitConfigIdentifier, dto.getRepoIdentifier())
                        .set(GitFullSyncConfigKeys.prTitle, dto.getPrTitle())
                        .set(GitFullSyncConfigKeys.createPullRequest, dto.isCreatePullRequest())
                        .set(GitFullSyncConfigKeys.targetBranch, dto.getTargetBranch())
                        .set(GitFullSyncConfigKeys.isNewBranch, dto.isNewBranch());

    GitFullSyncConfig gitFullSyncConfig = gitFullSyncConfigRepository.update(criteria, update);
    if (gitFullSyncConfig == null) {
      throw new InvalidRequestException(
          "No configuration found with given parameters", ErrorCode.RESOURCE_NOT_FOUND, WingsException.USER);
    } else {
      return GitFullSyncConfigMapper.toDTO(gitFullSyncConfigRepository.save(gitFullSyncConfig));
    }
  }

  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Optional<GitFullSyncConfig> gitFullSyncConfigOptional =
        getInternal(accountIdentifier, orgIdentifier, projectIdentifier);
    if (gitFullSyncConfigOptional.isPresent()) {
      gitFullSyncConfigRepository.delete(gitFullSyncConfigOptional.get());
      return true;
    }
    return false;
  }

  private Criteria getCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return Criteria.where(GitFullSyncConfigKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(GitFullSyncConfigKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(GitFullSyncConfigKeys.projectIdentifier)
        .is(projectIdentifier);
  }

  private void validateBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlGitConfigId, String branch, boolean isNewBranch) {
    YamlGitConfigDTO yamlGitConfig =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountIdentifier, yamlGitConfigId);
    GitBranch gitBranch = gitBranchService.get(accountIdentifier, yamlGitConfig.getRepo(), branch);
    if (gitBranch == null && !isNewBranch) {
      throw new InvalidRequestException(String.format("Branch [%s] does not exist", branch));
    } else if (gitBranch != null && isNewBranch) {
      throw new InvalidRequestException(String.format("Branch [%s] already exist", branch));
    }
  }
}
