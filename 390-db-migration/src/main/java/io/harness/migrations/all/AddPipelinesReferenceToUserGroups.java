package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Pipeline;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddPipelinesReferenceToUserGroups implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Inject private UserGroupService userGroupService;

  @Inject private PipelineService pipelineService;

  @Override
  public void migrate() {
    try {
      log.info("Start - Adding Pipeline references to user groups");
      try (HIterator<Pipeline> pipelineIterator =
               new HIterator<>(wingsPersistence.createQuery(Pipeline.class).fetch())) {
        while (pipelineIterator.hasNext()) {
          final Pipeline pipeline = pipelineIterator.next();
          Set<String> existingUserGroups = pipelineService.getUserGroups(pipeline);
          userGroupService.updateUserGroupParents(
              new HashSet<>(), existingUserGroups, pipeline.getAccountId(), pipeline.getUuid(), pipeline.getAppId());
        }
      }
      log.info("Adding Pipeline references to user groups finished successfully.");
    } catch (Exception ex) {
      log.error("Error while adding pipeline references in user groups", ex);
    }
  }
}
