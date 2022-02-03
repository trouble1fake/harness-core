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
import io.harness.service.intfc.DelegateTaskService;

import software.wings.beans.TaskType;
import software.wings.service.intfc.AssignDelegateService;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateTaskValidationFailIterator implements MongoPersistenceIterator.Handler<DelegateTask> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DelegateTask> persistenceProvider;
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateTaskService delegateTaskService;

  public void registerIterators(int threadPoolSize) {
    PersistenceIteratorFactory.PumpExecutorOptions options = PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                                                 .interval(Duration.ofMinutes(1))
                                                                 .poolSize(threadPoolSize)
                                                                 .name("DelegateTaskValidation")
                                                                 .build();
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(options, DelegateTask.class,
        MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
            .clazz(DelegateTask.class)
            .fieldName(DelegateTaskKeys.taskValidationFailureCheckIteration)
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofSeconds(45))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .filterExpander(query
                -> query.filter(DelegateTaskKeys.status, QUEUED)
                       .field(DelegateTaskKeys.validatingDelegateIds)
                       .doesNotExist()
                       .field(DelegateTaskKeys.validationCompleteDelegateIds)
                       .exists())
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider));
  }
  @Override
  public void handle(DelegateTask delegateTask) {
    log.info("Check delegate Task {} with validation fail", delegateTask.getUuid());
    if (delegateTask.getValidationCompleteDelegateIds().containsAll(delegateTask.getEligibleToExecuteDelegateIds())) {
      log.info("Found delegate task with all validation completed but not assigned");
      try (AutoLogContext ignore = new TaskLogContext(delegateTask.getUuid(), delegateTask.getData().getTaskType(),
               TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
        // Check whether a whitelisted delegate is connected
        List<String> whitelistedDelegates = assignDelegateService.connectedWhitelistedDelegates(delegateTask);
        if (isNotEmpty(whitelistedDelegates)) {
          log.info("Waiting for task {} to be acquired by a whitelisted delegate: {}", delegateTask.getUuid(),
              whitelistedDelegates);
          return;
        }
        log.info("Failing task {} due to validation failed ", delegateTask.getUuid());
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
        delegateTaskService.processDelegateResponse(delegateTask.getAccountId(), null, delegateTask.getAccountId(),
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
