/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.repositories.yamlGitConfig;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.YamlGitConfig;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class YamlGitConfigRepositoryCustomImpl implements YamlGitConfigRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UpdateResult update(Query query, Update update) {
    return mongoTemplate.updateFirst(query, update, YamlGitConfig.class);
  }
}
