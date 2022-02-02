/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.DelegateSelectionLogResponse;
import io.harness.selection.log.DelegateSelectionLog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OwnedBy(DEL)
public interface DelegateSelectionLogsService {
  void save(DelegateSelectionLog log);

  void logNoEligibleDelegatesToExecuteTask(String accountId, String taskId);

  void logEligibleDelegatesToExecuteTask(Set<String> delegateIds, String accountId, String taskId);

  void logNonSelectedDelegates(String accountId, String taskId, Map<String, List<String>> nonAssignableDelegates);

  void logBroadcastToDelegate(Set<String> delegateIds, String accountId, String taskId);

  void logTaskAssigned(String accountId, String delegateId, String taskId);

  void logTaskValidationFailed(String accountId, String taskId, String failureMessage);

  List<DelegateSelectionLogParams> fetchTaskSelectionLogs(String accountId, String taskId);

  Map<String,List<DelegateSelectionLogParams>> fetchTaskSelectionLogsGroupByAssessment(String accountId, String taskId);

  DelegateSelectionLogResponse fetchTaskSelectionLogsData(String accountId, String taskId);

  Optional<DelegateSelectionLogParams> fetchSelectedDelegateForTask(String accountId, String taskId);
}
