/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.core.entities.SideKick;
import io.harness.cvng.core.entities.SideKick.SideKickData;
import io.harness.cvng.core.entities.SideKick.SidekickKeys;
import io.harness.cvng.core.entities.SideKick.Status;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
public class SideKickServiceImpl implements SideKickService {
  @Inject private HPersistence hPersistence;
  @Inject private Map<SideKick.Type, SideKickExecutor> typeSideKickExecutorMap;
  @Inject private Clock clock;
  @Override
  public void schedule(SideKickData sideKickData, Instant runAfter) {
    hPersistence.save(SideKick.builder().status(Status.QUEUED).runAfter(runAfter).sideKickData(sideKickData).build());
    log.info("Saved sidekick: {} run after: ", sideKickData, runAfter);
  }

  @Override
  public void processNext() {
    while (true) {
      boolean processed = processNextTask();
      if (!processed) {
        break;
      }
    }
  }
  private boolean processNextTask() {
    Query<SideKick> query = hPersistence.createQuery(SideKick.class)
                                .filter(SidekickKeys.status, Status.QUEUED)
                                .field(SidekickKeys.runAfter)
                                .lessThan(clock.instant())
                                .order(Sort.ascending(SidekickKeys.createdAt));
    UpdateOperations<SideKick> updateOperations = hPersistence.createUpdateOperations(SideKick.class)
                                                      .set(SidekickKeys.status, Status.RUNNING)
                                                      .set(SidekickKeys.lastUpdatedAt, clock.millis());
    SideKick nextTask = hPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
    if (nextTask != null) {
      log.info("Processing task: {}", nextTask);
      Status status = null;
      String exceptionStr = null;
      String stacktrace = null;
      try {
        typeSideKickExecutorMap.get(nextTask.getSideKickData().getType()).execute(nextTask.getSideKickData());
        status = Status.SUCCESS;
      } catch (Exception exception) {
        status = Status.FAILED;
        exceptionStr = exception.getMessage();
        stacktrace = ExceptionUtils.getStackTrace(exception);
      } finally {
        UpdateOperations<SideKick> statusUpdateOperations =
            hPersistence.createUpdateOperations(SideKick.class).set(SidekickKeys.status, status);
        if (exceptionStr != null) {
          statusUpdateOperations.set(SidekickKeys.exception, exceptionStr);
        }
        if (stacktrace != null) {
          statusUpdateOperations.set(SidekickKeys.stacktrace, stacktrace);
        }
        hPersistence.update(nextTask, statusUpdateOperations);
      }
    }
    return nextTask != null;
  }
}
