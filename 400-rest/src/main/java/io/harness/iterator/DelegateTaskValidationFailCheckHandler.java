package io.harness.iterator;

import static io.harness.beans.DelegateTask.DelegateTaskKeys;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.joining;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.TaskLogContext;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.beans.TaskType;
import software.wings.service.intfc.AssignDelegateService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class DelegateTaskValidationFailCheckHandler implements MongoPersistenceIterator.Handler<DelegateTask> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DelegateTask> persistenceProvider;
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private HPersistence persistence;

  private static final long TASK_VALIDATION_FAIL_TIMEOUT = 10;

  public void registerIterators(int threadPoolSize) {
    PersistenceIteratorFactory.PumpExecutorOptions options =
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .interval(Duration.ofSeconds(TASK_VALIDATION_FAIL_TIMEOUT))
            .poolSize(threadPoolSize)
            .name("DelegateTaskValidation")
            .build();
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(options, DelegateTask.class,
        MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
            .clazz(DelegateTask.class)
            .fieldName(DelegateTaskKeys.taskValidationFailureCheckIteration)
            .targetInterval(ofSeconds(TASK_VALIDATION_FAIL_TIMEOUT))
            .acceptableNoAlertDelay(ofSeconds(45))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .filterExpander(query
                -> query.filter(DelegateTaskKeys.status, QUEUED)
                       .field(DelegateTaskKeys.validationCompleteDelegateIds)
                       .exists())
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }
  @Override
  public void handle(DelegateTask delegateTask) {
    if (delegateTask.getValidationCompleteDelegateIds().containsAll(delegateTask.getEligibleToExecuteDelegateIds())) {
      log.info(
          "Found delegate task {} with validation completed by all delegates but not assigned", delegateTask.getUuid());
      try (AutoLogContext ignore = new TaskLogContext(delegateTask.getUuid(), delegateTask.getData().getTaskType(),
               TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
        // Check whether a whitelisted delegate is connected
        List<String> whitelistedDelegates = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
        if (isNotEmpty(whitelistedDelegates)) {
          log.info("Waiting for task {} to be acquired by a whitelisted delegate: {}", delegateTask.getUuid(),
              whitelistedDelegates);
          return;
        }
        log.info("Failing task {} due to validation failure ", delegateTask.getUuid());
        String errorMessage = generateCapabilitiesMessage(delegateTask);
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
        Query<DelegateTask> taskQuery = persistence.createQuery(DelegateTask.class)
                                            .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                            .filter(DelegateTaskKeys.uuid, delegateTask.getUuid());

        delegateTaskService.handleResponse(delegateTask, taskQuery,
            DelegateTaskResponse.builder()
                .accountId(delegateTask.getAccountId())
                .response(response)
                .responseCode(DelegateTaskResponse.ResponseCode.OK)
                .build());
      }
    }
  }

  private String generateCapabilitiesMessage(DelegateTask delegateTask) {
    final List<ExecutionCapability> executionCapabilities = delegateTask.getExecutionCapabilities();
    final StringBuilder stringBuilder = new StringBuilder("");

    if (isNotEmpty(executionCapabilities)) {
      stringBuilder.append(
          (executionCapabilities.size() > 4 ? executionCapabilities.subList(0, 4) : executionCapabilities)
              .stream()
              .map(ExecutionCapability::fetchCapabilityBasis)
              .collect(joining(", ")));
      if (executionCapabilities.size() > 4) {
        stringBuilder.append(", and ").append(executionCapabilities.size() - 4).append(" more...");
      }
    }
    return stringBuilder.toString();
  }
}
