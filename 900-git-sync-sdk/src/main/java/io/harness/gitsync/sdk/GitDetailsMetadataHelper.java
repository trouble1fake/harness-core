package io.harness.gitsync.sdk;

import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.clients.YamlGitConfigClient;
import io.harness.gitsync.common.dtos.GitSyncConfigDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;

@Singleton
@Slf4j
public class GitDetailsMetadataHelper {
  @Inject YamlGitConfigClient yamlGitConfigClient;

  public void addRepoNameToListOfGitDetails(List<WithEntityGitDetails> withEntityGitDetailsList,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Map<String, String> repoIdToName = getRepoIdToName(accountIdentifier, orgIdentifier, projectIdentifier);
    for (WithEntityGitDetails withEntityGitDetails : withEntityGitDetailsList) {
      if (withEntityGitDetails.getGitDetails() != null
          && !StringUtils.isEmpty(withEntityGitDetails.getGitDetails().getRepoIdentifier())) {
        String repoIdentifier = withEntityGitDetails.getGitDetails().getRepoIdentifier();
        String repoName = repoIdToName.get(repoIdentifier);
        withEntityGitDetails.getGitDetails().setRepoName(repoName);
      }
    }
  }

  private Map<String, String> getRepoIdToName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      List<GitSyncConfigDTO> yamlGitConfigs =
          yamlGitConfigClient.getConfigs(accountIdentifier, orgIdentifier, projectIdentifier).execute().body();
      return yamlGitConfigs.stream().collect(
          Collectors.toMap(GitSyncConfigDTO::getIdentifier, GitSyncConfigDTO::getName));
    } catch (IOException e) {
      throw new InvalidRequestException("Could not fetch yamlGitConfigs while adding repo name to entity git details");
    }
  }
}
