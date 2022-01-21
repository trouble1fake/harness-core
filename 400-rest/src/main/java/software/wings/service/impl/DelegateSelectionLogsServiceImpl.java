/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.DelegateSelectionLogResponse;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.selection.log.DelegateSelectionLog;
import io.harness.selection.log.DelegateSelectionLog.DelegateSelectionLogKeys;
import io.harness.selection.log.DelegateSelectionLogTaskMetadata;
import io.harness.service.intfc.DelegateCache;

import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("io.harness.beans.Cd1SetupFields")
@BreakDependencyOn("software.wings.beans.Application")
@BreakDependencyOn("software.wings.beans.Environment")
@BreakDependencyOn("software.wings.beans.Service")
@OwnedBy(DEL)
public class DelegateSelectionLogsServiceImpl implements DelegateSelectionLogsService {
  @Inject private HPersistence persistence;
  @Inject private DelegateService delegateService;
  @Inject private DelegateCache delegateCache;
  @Inject private FeatureFlagService featureFlagService;

  private static final String SELECTED = "Selected";
  private static final String NON_SELECTED = "NonSelected";
  private static final String ASSIGNED = "Assigned";
  private static final String INFO = "Info";

  private static final String TASK_ASSIGNED = "Delegate assigned for task execution";
  public static final String NO_ELIGIBLE_DELEGATES = "No eligible delegate(s) in account to execute task";
  private static final String ELIGIBLE_DELEGATES = "Delegate(s) eligible to execute task";
  private static final String BROADCASTING_DELEGATES = "Broadcasting to delegate(s)";

  @Override
  public void save(DelegateSelectionLog selectionLog) {
    if (featureFlagService.isEnabled(FeatureName.DELEGATE_SELECTION_LOGS_DISABLED, selectionLog.getAccountId())) {
      return;
    }
    try {
      persistence.save(selectionLog);
    } catch (Exception exception) {
      log.error("Error while saving into Database ", exception);
    }
  }

  @Override
  public void logNoEligibleDelegatesToExecuteTask(final String accountId, final String taskId) {
    save(DelegateSelectionLog.builder()
             .accountId(accountId)
             .taskId(taskId)
             .conclusion(INFO)
             .message(NO_ELIGIBLE_DELEGATES)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public void logEligibleDelegatesToExecuteTask(final Set<String> delegateIds, String accountId, String taskId) {
    String message = String.format("%s : [%s]", ELIGIBLE_DELEGATES, String.join(", ", delegateIds));
    save(DelegateSelectionLog.builder()
             .accountId(accountId)
             .taskId(taskId)
             .delegateIds(delegateIds)
             .conclusion(SELECTED)
             .message(message)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public void logNonSelectedDelegates(String accountId, String taskId, List<String> nonAssignableDelegates) {
    nonAssignableDelegates.forEach(msg
        -> save(DelegateSelectionLog.builder()
                    .accountId(accountId)
                    .taskId(taskId)
                    .message(msg)
                    .conclusion(NON_SELECTED)
                    .build()));
  }

  @Override
  public void logBroadcastToDelegate(Set<String> delegateIds, String accountId, String taskId) {
    String message = String.format("%s : [%s]", BROADCASTING_DELEGATES, String.join(", ", delegateIds));
    save(DelegateSelectionLog.builder()
             .accountId(accountId)
             .taskId(taskId)
             .delegateIds(delegateIds)
             .conclusion(INFO)
             .message(message)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public void logTaskAssigned(String accountId, String delegateId, String taskId) {
    String message = String.format("%s : [%s]", TASK_ASSIGNED, delegateId);
    save(DelegateSelectionLog.builder()
             .accountId(accountId)
             .taskId(taskId)
             .conclusion(ASSIGNED)
             .message(message)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public List<DelegateSelectionLogParams> fetchTaskSelectionLogs(String accountId, String taskId) {
    List<DelegateSelectionLog> delegateSelectionLogsList = persistence.createQuery(DelegateSelectionLog.class)
                                                               .filter(DelegateSelectionLogKeys.accountId, accountId)
                                                               .filter(DelegateSelectionLogKeys.taskId, taskId)
                                                               .asList();
    return delegateSelectionLogsList.stream().map(this::buildSelectionLogParams).collect(Collectors.toList());
  }

  @Override
  public DelegateSelectionLogResponse fetchTaskSelectionLogsData(String accountId, String taskId) {
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchTaskSelectionLogs(accountId, taskId);
    DelegateSelectionLogTaskMetadata taskMetadata = persistence.createQuery(DelegateSelectionLogTaskMetadata.class)
                                                        .filter(DelegateSelectionLogKeys.accountId, accountId)
                                                        .filter(DelegateSelectionLogKeys.taskId, taskId)
                                                        .get();

    Map<String, String> previewSetupAbstractions = new HashMap<>();
    if (taskMetadata != null && taskMetadata.getSetupAbstractions() != null) {
      previewSetupAbstractions =
          taskMetadata.getSetupAbstractions()
              .entrySet()
              .stream()
              .filter(map
                  -> Cd1SetupFields.APPLICATION.equals(map.getKey()) || Cd1SetupFields.SERVICE.equals(map.getKey())
                      || Cd1SetupFields.ENVIRONMENT.equals(map.getKey())
                      || Cd1SetupFields.ENVIRONMENT_TYPE.equals(map.getKey()))
              .collect(Collectors.toMap(Map.Entry::getKey, map -> String.valueOf(map.getValue())));
    }

    return DelegateSelectionLogResponse.builder()
        .delegateSelectionLogs(delegateSelectionLogParams)
        .taskSetupAbstractions(previewSetupAbstractions)
        .build();
  }

  @Override
  public Optional<DelegateSelectionLogParams> fetchSelectedDelegateForTask(String accountId, String taskId) {
    DelegateSelectionLog delegateSelectionLog = persistence.createQuery(DelegateSelectionLog.class)
                                                    .filter(DelegateSelectionLogKeys.accountId, accountId)
                                                    .filter(DelegateSelectionLogKeys.taskId, taskId)
                                                    .filter(DelegateSelectionLogKeys.conclusion, SELECTED)
                                                    .get();
    if (delegateSelectionLog == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(buildSelectionLogParams(delegateSelectionLog));
  }

  private DelegateSelectionLogParams buildSelectionLogParams(DelegateSelectionLog selectionLog) {
    return DelegateSelectionLogParams.builder()
        .conclusion(selectionLog.getConclusion())
        .message(selectionLog.getMessage())
        .eventTimestamp(selectionLog.getEventTimestamp())
        .build();
  }
}
