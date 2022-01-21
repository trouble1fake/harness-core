/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.FeatureName.DELEGATE_SELECTION_LOGS_DISABLED;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.BROADCASTING_DELEGATES;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_CG_NG_TASK_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_DELEGATE_SCOPE_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_OWNER;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_PROFILE_SCOPE_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_SELECTOR_TASK_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_TASK_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.ELIGIBLE_DELEGATES;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.NO_ELIGIBLE_DELEGATES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.selection.log.DelegateSelectionLog;

import software.wings.WingsBaseTest;
import software.wings.dl.WingsPersistence;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("io.harness.beans.Cd1SetupFields")
@BreakDependencyOn("software.wings.WingsBaseTest")
@BreakDependencyOn("software.wings.beans.Application")
@BreakDependencyOn("software.wings.beans.Environment")
@BreakDependencyOn("software.wings.beans.Service")
@BreakDependencyOn("software.wings.dl.WingsPersistence")
public class DelegateSelectionLogsServiceImplTest extends WingsBaseTest {
  private static final String DISCONNECTED = "Disconnected";
  private static final String INFO = "Info";
  private static final String REJECTED = "Rejected";
  private static final String SELECTED = "Selected";
  private static final String NON_SELECTED = "Non Selected";

  private static final String MISSING_SELECTOR_MESSAGE = "missing selector";

  @Inject protected WingsPersistence wingsPersistence;
  @Mock protected FeatureFlagService featureFlagService;
  @InjectMocks @Inject DelegateSelectionLogsServiceImpl delegateSelectionLogsService;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogNoEligibleDelegates() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(accountId, taskId);
    List<DelegateSelectionLogParams> delegateSelectionLogParams =
        delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, taskId);

    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(REJECTED);
    assertThat(delegateSelectionLogParams.get(0).getMessage()).isEqualTo(NO_ELIGIBLE_DELEGATES);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogEligibleDelegatesToExecuteTask() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    String delegateId = generateUuid();
    delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(Sets.newHashSet(delegateId), accountId, taskId);
    List<DelegateSelectionLogParams> delegateSelectionLogParams =
        delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, taskId);

    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(SELECTED);
    assertThat(delegateSelectionLogParams.get(0).getMessage()).startsWith(ELIGIBLE_DELEGATES);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogNonSelectedDelegates() {
    String taskId = generateUuid();
    String accountId = generateUuid();

    Map<String, List<String>> nonSelected = new HashMap<>();
    nonSelected.put(CAN_NOT_ASSIGN_CG_NG_TASK_GROUP, Lists.newArrayList(generateUuid()));
    nonSelected.put(CAN_NOT_ASSIGN_DELEGATE_SCOPE_GROUP, Lists.newArrayList(generateUuid()));
    nonSelected.put(CAN_NOT_ASSIGN_PROFILE_SCOPE_GROUP, Lists.newArrayList(generateUuid()));
    nonSelected.put(CAN_NOT_ASSIGN_SELECTOR_TASK_GROUP, Lists.newArrayList(generateUuid()));
    nonSelected.put(CAN_NOT_ASSIGN_TASK_GROUP, Lists.newArrayList(generateUuid()));
    nonSelected.put(CAN_NOT_ASSIGN_OWNER, Lists.newArrayList(generateUuid()));

    delegateSelectionLogsService.logNonSelectedDelegates(accountId, taskId, nonSelected);
    List<DelegateSelectionLogParams> delegateSelectionLogParams =
        delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, taskId);

    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(4);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(NON_SELECTED);
    assertThat(delegateSelectionLogParams.get(1).getConclusion()).isEqualTo(NON_SELECTED);
    assertThat(delegateSelectionLogParams.get(2).getConclusion()).isEqualTo(NON_SELECTED);
    assertThat(delegateSelectionLogParams.get(3).getConclusion()).isEqualTo(NON_SELECTED);

    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(1).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(2).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(3).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogBroadcastToDelegate() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    String delegateId = generateUuid();
    delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(delegateId), accountId, taskId);
    List<DelegateSelectionLogParams> delegateSelectionLogParams =
        delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, taskId);
    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(INFO);
    assertThat(delegateSelectionLogParams.get(0).getMessage()).startsWith(BROADCASTING_DELEGATES);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotLogNoEligibleDelegates() {
    assertThatCode(() -> delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(null, null))
        .doesNotThrowAnyException();
    assertThatCode(() -> delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(anyString(), anyString()))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogNoEligibleAvailableDelegates() {
    String taskId = generateUuid();
    String accountId = generateUuid();

    delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(accountId, taskId);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotGenerateSelectionLog() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(DELEGATE_SELECTION_LOGS_DISABLED, accountId)).thenReturn(true);
    assertThat(persistence.get(DelegateSelectionLog.class, taskId)).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotLogNoEligibleAvailableDelegates() {
    assertThatCode(() -> delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(null, null))
        .doesNotThrowAnyException();
    assertThatCode(() -> delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(null, null))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotLogEligibleDelegatesToExecuteTask() {
    assertThatCode(()
                       -> delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(
                           Sets.newHashSet("delegateId"), anyString(), anyString()))
        .doesNotThrowAnyException();
    assertThatCode(() -> delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(null, null, null))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotLogBroadcastToDelegate() {
    assertThatCode(() -> delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet("delegateId"), null, null))
        .doesNotThrowAnyException();
  }
}
