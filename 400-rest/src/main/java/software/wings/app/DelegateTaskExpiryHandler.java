package software.wings.app;

import static io.harness.beans.DelegateTask.Status.ABORTED;
import static io.harness.beans.DelegateTask.Status.PARKED;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_EXPIRED;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.task.TaskLogContext;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ExceptionLogger;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfoManager;

import software.wings.beans.TaskType;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

@Slf4j
public class DelegateTaskExpiryHandler implements Runnable {
  private static final SecureRandom random = new SecureRandom();

  @Inject private HPersistence persistence;
  @Inject private Clock clock;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateService delegateService;
  @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Inject private ConfigurationController configurationController;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateMetricsService delegateMetricsService;

  private AtomicInteger clustering = new AtomicInteger(1);

  private static final FindOptions expiryLimit = new FindOptions().limit(100);

  @Override
  public void run() {
    if (getMaintenanceFlag()) {
      return;
    }

    try {
      if (configurationController.isPrimary()) {
        markTimedOutTasksAsFailed();
        markLongQueuedTasksAsFailed();
        markValidationFailedTasksFailed();
      }
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in the DelegateQueueTask call", exception);
    }
  }

  private void markTimedOutTasksAsFailed() {
    List<Key<DelegateTask>> longRunningTimedOutTaskKeys = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                                              .filter(DelegateTaskKeys.status, STARTED)
                                                              .field(DelegateTaskKeys.expiry)
                                                              .lessThan(currentTimeMillis())
                                                              .asKeyList(expiryLimit);

    if (!longRunningTimedOutTaskKeys.isEmpty()) {
      List<String> keyList = longRunningTimedOutTaskKeys.stream().map(key -> key.getId().toString()).collect(toList());
      log.info("Marking following timed out tasks as failed [{}]", keyList);
      endTasks(keyList);
    }
  }

  private void markLongQueuedTasksAsFailed() {
    // Find tasks which have been queued for too long
    Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                    .field(DelegateTaskKeys.status)
                                    .in(asList(QUEUED, PARKED, ABORTED))
                                    .field(DelegateTaskKeys.expiry)
                                    .lessThan(currentTimeMillis());

    // We usually pick from the top, but if we have full bucket we maybe slowing down
    // lets randomize a bit to increase the distribution
    int clusteringValue = clustering.get();
    if (clusteringValue > 1) {
      query.field(DelegateTaskKeys.createdAt).mod(clusteringValue, random.nextInt(clusteringValue));
    }

    List<Key<DelegateTask>> longQueuedTaskKeys = query.asKeyList(expiryLimit);
    clustering.set(longQueuedTaskKeys.size() == expiryLimit.getLimit() ? Math.min(16, clusteringValue * 2)
                                                                       : Math.max(1, clusteringValue / 2));

    if (!longQueuedTaskKeys.isEmpty()) {
      List<String> keyList = longQueuedTaskKeys.stream().map(key -> key.getId().toString()).collect(toList());
      log.info("Marking following long queued tasks as failed [{}]", keyList);
      endTasks(keyList);
    }
  }

  @VisibleForTesting
  public void endTasks(List<String> taskIds) {
    Map<String, DelegateTask> delegateTasks = new HashMap<>();
    Map<String, String> taskWaitIds = new HashMap<>();
    List<DelegateTask> tasksToExpire = new ArrayList<>();
    List<String> taskIdsToExpire = new ArrayList<>();
    try {
      List<DelegateTask> tasks = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                     .field(DelegateTaskKeys.uuid)
                                     .in(taskIds)
                                     .asList();

      for (DelegateTask task : tasks) {
        if (shouldExpireTask(task)) {
          tasksToExpire.add(task);
          taskIdsToExpire.add(task.getUuid());
          delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_EXPIRED);
        }
      }

      delegateTasks.putAll(tasksToExpire.stream().collect(toMap(DelegateTask::getUuid, identity())));
      taskWaitIds.putAll(tasksToExpire.stream()
                             .filter(task -> isNotEmpty(task.getWaitId()))
                             .collect(toMap(DelegateTask::getUuid, DelegateTask::getWaitId)));
    } catch (Exception e1) {
      log.error("Failed to deserialize {} tasks. Trying individually...", taskIds.size(), e1);
      for (String taskId : taskIds) {
        try {
          DelegateTask task =
              persistence.createQuery(DelegateTask.class, excludeAuthority).filter(DelegateTaskKeys.uuid, taskId).get();
          if (shouldExpireTask(task)) {
            taskIdsToExpire.add(taskId);
            delegateTasks.put(taskId, task);
            delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_EXPIRED);
            if (isNotEmpty(task.getWaitId())) {
              taskWaitIds.put(taskId, task.getWaitId());
            }
          }
        } catch (Exception e2) {
          log.error("Could not deserialize task {}. Trying again with only waitId field.", taskId, e2);
          taskIdsToExpire.add(taskId);
          try {
            String waitId = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                .filter(DelegateTaskKeys.uuid, taskId)
                                .project(DelegateTaskKeys.waitId, true)
                                .get()
                                .getWaitId();
            if (isNotEmpty(waitId)) {
              taskWaitIds.put(taskId, waitId);
            }
          } catch (Exception e3) {
            log.error(
                "Could not deserialize task {} with waitId only, giving up. Task will be deleted but notify not called.",
                taskId, e3);
          }
        }
      }
    }

    boolean deleted = persistence.deleteOnServer(
        persistence.createQuery(DelegateTask.class, excludeAuthority).field(DelegateTaskKeys.uuid).in(taskIdsToExpire));

    if (deleted) {
      taskIdsToExpire.forEach(taskId -> {
        if (taskWaitIds.containsKey(taskId)) {
          String errorMessage = delegateTasks.containsKey(taskId)
              ? assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTasks.get(taskId))
              : "Unable to determine proper error as delegate task could not be deserialized.";
          log.info("Marking task as failed - {}: {}", taskId, errorMessage);

          if (delegateTasks.get(taskId) != null) {
            delegateTaskService.handleResponse(delegateTasks.get(taskId), null,
                DelegateTaskResponse.builder()
                    .accountId(delegateTasks.get(taskId).getAccountId())
                    .responseCode(DelegateTaskResponse.ResponseCode.FAILED)
                    .response(ErrorNotifyResponseData.builder().errorMessage(errorMessage).build())
                    .build());
          }
        }
      });
    }
  }

  private boolean shouldExpireTask(DelegateTask task) {
    return !task.isForceExecute();
  }

  private void markValidationFailedTasksFailed() {
    long now = clock.millis();
    Query<DelegateTask> taskQuery = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                        .filter(DelegateTaskKeys.status, QUEUED)
                                        .field(DelegateTaskKeys.expiry)
                                        .greaterThan(now)
                                        .field(DelegateTaskKeys.validationCompleteDelegateIds)
                                        .exists()
                                        .field(DelegateTaskKeys.validatingDelegateIds)
                                        .doesNotExist()
                                        .field(DelegateTaskKeys.delegateId)
                                        .doesNotExist();
    try (HIterator<DelegateTask> iterator = new HIterator<>(taskQuery.fetch())) {
      while (iterator.hasNext()) {
        DelegateTask delegateTask = iterator.next();
        if (delegateTask.getValidationCompleteDelegateIds().size()
            == delegateTask.getEligibleToExecuteDelegateIds().size()) {
          List<String> whitelistedDelegates = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
          if (isNotEmpty(whitelistedDelegates)) {
            log.info("Waiting for task {} to be acquired by a whitelisted delegate: {}", delegateTask.getUuid(),
                whitelistedDelegates);
            return;
          }

          // String errorMessage = generateValidationError(delegateTask, areClientToolsInstalled);
          // log.info(errorMessage);
          DelegateResponseData response;
          if (delegateTask.getData().isAsync()) {
            response = ErrorNotifyResponseData.builder()
                           .failureTypes(EnumSet.of(FailureType.DELEGATE_PROVISIONING))
                           .errorMessage("errorMessage")
                           .build();
          } else {
            response = RemoteMethodReturnValueData.builder()
                           .exception(new InvalidRequestException("errorMessage", USER))
                           .build();
          }
          delegateTaskService.processDelegateResponse(delegateTask.getAccountId(), null, delegateTask.getUuid(),
              DelegateTaskResponse.builder()
                  .accountId(delegateTask.getAccountId())
                  .response(response)
                  .responseCode(DelegateTaskResponse.ResponseCode.OK)
                  .build());
        }
      }
    }
  }

  /*private void failIfAllDelegatesFailed(
      final String accountId, final String delegateId, final String taskId, final boolean areClientToolsInstalled) {
    DelegateTask delegateTask = getUnassignedDelegateTask(accountId, taskId, delegateId);
    if (delegateTask == null) {
      log.info("Task {} not found or was already assigned", taskId);
      return;
    }

    if (delegateTask.isForceExecute()) {
      log.debug("Task is set for force execution");
      return;
    }

    try (AutoLogContext ignore = new TaskLogContext(taskId, delegateTask.getData().getTaskType(),
             TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
      *//*  if (!isValidationComplete(delegateTask)) {
          log.info(
                  "Task {} is still being validated with delegate ids {} ", taskId,
        delegateTask.getValidatingDelegateIds()); return;
        }*//*
      // Check whether a whitelisted delegate is connected
      List<String> whitelistedDelegates = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
      if (isNotEmpty(whitelistedDelegates)) {
        log.info("Waiting for task {} to be acquired by a whitelisted delegate: {}", taskId, whitelistedDelegates);
        return;
      }


      String errorMessage = generateValidationError(delegateTask, areClientToolsInstalled);
      log.info(errorMessage);
      DelegateResponseData response;
      if (delegateTask.getData().isAsync()) {
        response = ErrorNotifyResponseData.builder()
                       .failureTypes(EnumSet.of(FailureType.DELEGATE_PROVISIONING))
                       .errorMessage(errorMessage)
                       .build();
      } else {
        response =
            RemoteMethodReturnValueData.builder().exception(new InvalidRequestException(errorMessage, USER)).build();
      }
      delegateTaskService.processDelegateResponse(accountId, null, taskId,
          DelegateTaskResponse.builder()
              .accountId(accountId)
              .response(response)
              .responseCode(DelegateTaskResponse.ResponseCode.OK)
              .build());
    }
  }*/
}
