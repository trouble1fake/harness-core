package io.harness.iterator;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.task.TaskLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.selection.log.BatchDelegateSelectionLog;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfoManager;

import software.wings.beans.TaskType;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class DelegateTaskBroadcastingIterator implements MongoPersistenceIterator.Handler<DelegateTask> {
  @Inject private io.harness.iterator.PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DelegateTask> persistenceProvider;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private DelegateService delegateService;
  @Inject private HPersistence persistence;
  @Inject private Clock clock;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Inject private ConfigurationController configurationController;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateMetricsService delegateMetricsService;

  PersistenceIterator<DelegateTask> queueIterator;

  private static final Duration TASK_BROADCAST_INTERVAL = Duration.ofSeconds(5L);

  public void registerIterators(int threadPoolSize) {
    PersistenceIteratorFactory.PumpExecutorOptions options = PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                                                 .interval(TASK_BROADCAST_INTERVAL)
                                                                 .poolSize(threadPoolSize)
                                                                 .name("DelegateTaskRebroadcast")
                                                                 .build();
    long now = clock.millis();
    queueIterator = persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(options, DelegateTask.class,
        MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
            .clazz(DelegateTask.class)
            .fieldName(DelegateTask.DelegateTaskKeys.nextBroadcast)
            .filterExpander(query
                -> query.filter(DelegateTask.DelegateTaskKeys.status, QUEUED)
                       .field(DelegateTask.DelegateTaskKeys.nextBroadcast)
                       .lessThan(now)
                       .field(DelegateTask.DelegateTaskKeys.expiry)
                       .greaterThan(now)
                       .field(DelegateTask.DelegateTaskKeys.delegateId)
                       .doesNotExist())
            .targetInterval(TASK_BROADCAST_INTERVAL)
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(DelegateTask delegateTask) {
    LinkedList<String> eligibleDelegatesList = delegateTask.getEligibleToExecuteDelegateIds();
    // add connected eligible delegates to broadcast list. Also rotate the eligibleDelegatesList list
    List<String> broadcastToDelegates = Lists.newArrayList();
    int broadcastLimit = Math.min(eligibleDelegatesList.size(), 10);

    Iterator<String> delegateIdIterator = eligibleDelegatesList.iterator();

    while (delegateIdIterator.hasNext() && broadcastLimit > broadcastToDelegates.size()) {
      String delegateId = eligibleDelegatesList.removeFirst();
      broadcastToDelegates.add(delegateId);
      eligibleDelegatesList.addLast(delegateId);
    }
    long now = clock.millis();

    Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                    .filter(DelegateTask.DelegateTaskKeys.uuid, delegateTask.getUuid());

    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class)
            .set(DelegateTask.DelegateTaskKeys.lastBroadcastAt, now)
            .set(DelegateTask.DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount() + 1)
            .set(DelegateTask.DelegateTaskKeys.eligibleToExecuteDelegateIds, eligibleDelegatesList)
            .set(DelegateTask.DelegateTaskKeys.nextBroadcast, now + TimeUnit.SECONDS.toMillis(5));
    delegateTask = persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);

    delegateTask.setBroadcastToDelegateIds(broadcastToDelegates);

    BatchDelegateSelectionLog batch = delegateSelectionLogsService.createBatch(delegateTask);
    if (isNotEmpty(broadcastToDelegates)) {
      delegateSelectionLogsService.logBroadcastToDelegate(
          batch, Sets.newHashSet(broadcastToDelegates), delegateTask.getAccountId());
    }

    delegateSelectionLogsService.save(batch);

    try (AutoLogContext ignore1 = new TaskLogContext(delegateTask.getUuid(), delegateTask.getData().getTaskType(),
             TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(delegateTask.getAccountId(), OVERRIDE_ERROR)) {
      if (delegateTask.getBroadcastCount() > 1) {
        log.info("Rebroadcast queued task id {} on broadcast attempt: {} to {} ", delegateTask.getUuid(),
            delegateTask.getBroadcastCount(), delegateTask.getBroadcastToDelegateIds());
      } else {
        log.debug("Broadcast queued task id {}. Broadcast count: {}", delegateTask.getUuid(),
            delegateTask.getBroadcastCount());
      }
      broadcastHelper.rebroadcastDelegateTask(delegateTask);
    }
    log.info("{} tasks were rebroadcast" + delegateTask.getUuid());
  }

  @VisibleForTesting
  protected void rebroadcastUnassignedTasks() {
    // Re-broadcast queued tasks not picked up by any Delegate and not in process of validation
    long now = clock.millis();

    Query<DelegateTask> unassignedTasksQuery = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                                   .filter(DelegateTask.DelegateTaskKeys.status, QUEUED)
                                                   .field(DelegateTask.DelegateTaskKeys.nextBroadcast)
                                                   .lessThan(now)
                                                   .field(DelegateTask.DelegateTaskKeys.expiry)
                                                   .greaterThan(now)
                                                   .field(DelegateTask.DelegateTaskKeys.delegateId)
                                                   .doesNotExist();

    try (HIterator<DelegateTask> iterator = new HIterator<>(unassignedTasksQuery.fetch())) {
      int count = 0;
      while (iterator.hasNext()) {
        DelegateTask delegateTask = iterator.next();
        Query<DelegateTask> query =
            persistence.createQuery(DelegateTask.class, excludeAuthority)
                .filter(DelegateTask.DelegateTaskKeys.uuid, delegateTask.getUuid())
                .filter(DelegateTask.DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount());

        LinkedList<String> eligibleDelegatesList = delegateTask.getEligibleToExecuteDelegateIds();
        if (isEmpty(eligibleDelegatesList)) {
          log.info("No eligible delegates for task {}", delegateTask.getUuid());
          continue;
        }

        // add connected eligible delegates to broadcast list. Also rotate the eligibleDelegatesList list
        List<String> broadcastToDelegates = Lists.newArrayList();
        int broadcastLimit = Math.min(eligibleDelegatesList.size(), 10);

        Iterator<String> delegateIdIterator = eligibleDelegatesList.iterator();

        while (delegateIdIterator.hasNext() && broadcastLimit > broadcastToDelegates.size()) {
          String delegateId = eligibleDelegatesList.removeFirst();
          broadcastToDelegates.add(delegateId);
          eligibleDelegatesList.addLast(delegateId);
        }

        UpdateOperations<DelegateTask> updateOperations =
            persistence.createUpdateOperations(DelegateTask.class)
                .set(DelegateTask.DelegateTaskKeys.lastBroadcastAt, now)
                .set(DelegateTask.DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount() + 1)
                .set(DelegateTask.DelegateTaskKeys.eligibleToExecuteDelegateIds, eligibleDelegatesList)
                .set(DelegateTask.DelegateTaskKeys.nextBroadcast, now + TimeUnit.SECONDS.toMillis(5));
        delegateTask = persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);
        // update failed, means this was broadcast by some other manager
        if (delegateTask == null) {
          log.info("Cannot find delegate task, update failed on broadcast");
          continue;
        }
        delegateTask.setBroadcastToDelegateIds(broadcastToDelegates);

        BatchDelegateSelectionLog batch = delegateSelectionLogsService.createBatch(delegateTask);
        if (isNotEmpty(broadcastToDelegates)) {
          delegateSelectionLogsService.logBroadcastToDelegate(
              batch, Sets.newHashSet(broadcastToDelegates), delegateTask.getAccountId());
        }

        delegateSelectionLogsService.save(batch);

        try (AutoLogContext ignore1 = new TaskLogContext(delegateTask.getUuid(), delegateTask.getData().getTaskType(),
                 TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR);
             AutoLogContext ignore2 = new AccountLogContext(delegateTask.getAccountId(), OVERRIDE_ERROR)) {
          if (delegateTask.getBroadcastCount() > 1) {
            log.info("Rebroadcast queued task id {} on broadcast attempt: {} to {} ", delegateTask.getUuid(),
                delegateTask.getBroadcastCount(), delegateTask.getBroadcastToDelegateIds());
          } else {
            log.debug("Broadcast queued task id {}. Broadcast count: {}", delegateTask.getUuid(),
                delegateTask.getBroadcastCount());
          }
          broadcastHelper.rebroadcastDelegateTask(delegateTask);
          count++;
        }
      }
      log.info("{} tasks were rebroadcast", count);
    }
  }
}
