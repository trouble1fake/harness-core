/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.delegate;

import static io.harness.beans.DelegateTask.Status.ABORTED;
import static io.harness.beans.DelegateTask.Status.ERROR;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateTaskAbortEvent.Builder.aDelegateTaskAbortEvent;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.MARKOM;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.service.impl.DelegateTaskServiceClassicImpl.TASK_CATEGORY_MAP;
import static software.wings.service.impl.DelegateTaskServiceClassicImpl.TASK_SELECTORS;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.beans.ExecutionStatus;
import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilityRequirement;
import io.harness.capability.CapabilityTaskSelectionDetails;
import io.harness.capability.HttpConnectionParameters;
import io.harness.capability.service.CapabilityService;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponse.ResponseCode;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.TaskSelectorMap;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.logstreaming.LogStreamingServiceConfig;
import io.harness.network.LocalhostUtils;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.rule.Cache;
import io.harness.rule.Owner;
import io.harness.selection.log.BatchDelegateSelectionLog;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateProfileObserver;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.service.intfc.DelegateTaskRetryObserver;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfo;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.FeatureTestHelper;
import software.wings.WingsBaseTest;
import software.wings.app.DelegateGrpcConfig;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.LicenseInfo;
import software.wings.beans.TaskType;
import software.wings.cdn.CdnConfig;
import software.wings.delegatetasks.cv.RateLimitExceededException;
import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.jre.JreConfig;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;
import software.wings.service.impl.DelegateTaskStatusObserver;
import software.wings.service.impl.infra.InfraDownloadService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.sm.ExecutionStatusData;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("software.wings.WingsBaseTest")
@BreakDependencyOn("software.wings.app.MainConfiguration")
public class DelegateTaskServiceClassicTest extends WingsBaseTest {
  private static final String VERSION = "1.0.0";
  private static final String DELEGATE_TYPE = "dockerType";

  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private AccountService accountService;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private BroadcasterFactory broadcasterFactory;
  @Mock private Broadcaster broadcaster;
  @Mock private AssignDelegateService assignDelegateService;
  @Mock private InfraDownloadService infraDownloadService;
  @Mock private VersionInfoManager versionInfoManager;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private DelegateGrpcConfig delegateGrpcConfig;
  @Mock private CapabilityService capabilityService;
  @Mock private DelegateSyncService delegateSyncService;
  @Mock private DelegateSelectionLogsService delegateSelectionLogsService;
  @Mock private AlertService alertService;

  @Inject private FeatureTestHelper featureTestHelper;
  @Inject private KryoSerializer kryoSerializer;

  private int port = LocalhostUtils.findFreePort();
  @Rule public WireMockRule wireMockRule = new WireMockRule(port);
  @Rule public ExpectedException thrown = ExpectedException.none();

  @InjectMocks @Inject private DelegateCache delegateCache;
  @InjectMocks @Inject private DelegateServiceImpl delegateService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @InjectMocks @Inject private DelegateTaskService delegateTaskService;
  @InjectMocks @Inject private DelegateTaskBroadcastHelper broadcastHelper;

  @Inject private HPersistence persistence;

  private final Subject<DelegateProfileObserver> delegateProfileSubject = mock(Subject.class);
  private final Subject<DelegateTaskRetryObserver> retryObserverSubject = mock(Subject.class);
  private final Subject<DelegateTaskStatusObserver> delegateTaskStatusObserverSubject = mock(Subject.class);
  private final Subject<DelegateObserver> subject = mock(Subject.class);

  private final Account account =
      anAccount().withLicenseInfo(LicenseInfo.builder().accountStatus(AccountStatus.ACTIVE).build()).build();

  @Before
  public void setUp() throws IllegalAccessException {
    CdnConfig cdnConfig = new CdnConfig();
    cdnConfig.setUrl("http://localhost:9500");
    when(subdomainUrlHelper.getDelegateMetadataUrl(any(), any(), any()))
        .thenReturn("http://localhost:" + port + "/delegateci.txt");
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    when(mainConfiguration.getScmVersion()).thenReturn("542f4642");
    when(mainConfiguration.getOcVersion()).thenReturn("v4.2.16");
    when(mainConfiguration.getCdnConfig()).thenReturn(cdnConfig);
    HashMap<String, JreConfig> jreConfigMap = new HashMap<>();
    jreConfigMap.put("openjdk8u242", getOpenjdkJreConfig());
    when(mainConfiguration.getCurrentJre()).thenReturn("openjdk8u242");
    when(mainConfiguration.getJreConfigs()).thenReturn(jreConfigMap);
    when(subdomainUrlHelper.getWatcherMetadataUrl(any(), any(), any()))
        .thenReturn("http://localhost:" + port + "/watcherci.txt");
    FileUploadLimit fileUploadLimit = new FileUploadLimit();
    fileUploadLimit.setProfileResultLimit(1000000000L);
    when(mainConfiguration.getFileUploadLimits()).thenReturn(fileUploadLimit);

    LogStreamingServiceConfig logSteamingServiceConfig =
        LogStreamingServiceConfig.builder().serviceToken("token").baseUrl("http://localhost:8079").build();
    when(mainConfiguration.getLogStreamingServiceConfig()).thenReturn(logSteamingServiceConfig);

    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setCriticalDelegateTaskRejectAtLimit(100000);
    portalConfig.setJwtNextGenManagerSecret("**********");
    when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(delegateGrpcConfig.getPort()).thenReturn(8080);

    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(singletonList("0.0.0")).build());
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(infraDownloadService.getDownloadUrlForDelegate(anyString(), any()))
        .thenReturn("http://localhost:" + port + "/builds/9/delegate.jar");
    when(infraDownloadService.getCdnWatcherBaseUrl()).thenReturn("http://localhost:9500/builds");
    wireMockRule.stubFor(get(urlEqualTo("/delegateci.txt"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("9.9.9 jobs/delegateci/9/delegate.jar")
                                             .withHeader("Content-Type", "text/plain")));

    wireMockRule.stubFor(head(urlEqualTo("/builds/9/delegate.jar")).willReturn(aResponse().withStatus(200)));

    wireMockRule.stubFor(get(urlEqualTo("/watcherci.txt"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("8.8.8 jobs/deploy-ci-watcher/8/watcher.jar")
                                             .withHeader("Content-Type", "text/plain")));

    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    when(versionInfoManager.getVersionInfo()).thenReturn(VersionInfo.builder().version(VERSION).build());
    //    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);

    FieldUtils.writeField(delegateService, "delegateProfileSubject", delegateProfileSubject, true);
    FieldUtils.writeField(delegateService, "subject", subject, true);
    FieldUtils.writeField(delegateTaskService, "retryObserverSubject", retryObserverSubject, true);
    FieldUtils.writeField(
        delegateTaskService, "delegateTaskStatusObserverSubject", delegateTaskStatusObserverSubject, true);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void shouldGetDelegateTaskEvents() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    List<DelegateTaskEvent> delegateTaskEvents =
        delegateTaskServiceClassic.getDelegateTaskEvents(delegateTask.getAccountId(), DELEGATE_ID, false);
    assertThat(delegateTaskEvents).hasSize(1);
    assertThat(delegateTaskEvents.get(0).getDelegateTaskId()).isEqualTo(delegateTask.getUuid());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTask() {
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .accountId(ACCOUNT_ID)
            .executionCapabilities(
                asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
                    "http://www.url.com", null)))
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, SERVICE_TEMPLATE_ID)
            .setupAbstraction(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD, ARTIFACT_STREAM_ID)
            .version(VERSION)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .build();
    delegateTaskServiceClassic.queueTask(delegateTask);
    delegateTask.setTaskActivityLogs(null);
    delegateTask.setBroadcastToDelegateIds(null);
    assertThat(persistence.createQuery(DelegateTask.class).filter(DelegateTaskKeys.uuid, delegateTask.getUuid()).get())
        .isEqualTo(delegateTask);
    verify(assignDelegateService, never()).getAccountDelegates(delegateTask.getAccountId());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWhenRankLimitIsNotReached() {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .accountId(ACCOUNT_ID)
            .executionCapabilities(
                asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
                    "http://www.url.com", null)))
            .rank(DelegateTaskRank.OPTIONAL)
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, SERVICE_TEMPLATE_ID)
            .setupAbstraction(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD, ARTIFACT_STREAM_ID)
            .version(VERSION)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .build();

    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setOptionalDelegateTaskRejectAtLimit(10000);
    when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.queueTask(delegateTask);
    delegateTask.setTaskActivityLogs(null);
    delegateTask.setBroadcastToDelegateIds(null);
    assertThat(persistence.createQuery(DelegateTask.class).filter(DelegateTaskKeys.uuid, delegateTask.getUuid()).get())
        .isEqualTo(delegateTask);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  @Ignore("Ignore until we replace noop with real exception")
  public void shouldNotSaveDelegateTaskWhenRankLimitIsReached() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(ACCOUNT_ID)
                                    .rank(DelegateTaskRank.IMPORTANT)
                                    .waitId(generateUuid())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                                    .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
                                    .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, SERVICE_TEMPLATE_ID)
                                    .setupAbstraction(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD, ARTIFACT_STREAM_ID)
                                    .version(VERSION)
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.HTTP.name())
                                              .parameters(new Object[] {})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();

    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setImportantDelegateTaskRejectAtLimit(0);
    when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    assertThatThrownBy(() -> delegateTaskServiceClassic.queueTask(delegateTask))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage("Rate limit exceeded for task rank IMPORTANT. Please try again later.");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldProcessDelegateTaskResponse() {
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    delegateTaskService.processDelegateResponse(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(),
        DelegateTaskResponse.builder()
            .accountId(ACCOUNT_ID)
            .response(ExecutionStatusData.builder().executionStatus(ExecutionStatus.SUCCESS).build())
            .responseCode(ResponseCode.OK)
            .build());
    assertThat(persistence.createQuery(DelegateTask.class).filter(DelegateTaskKeys.uuid, delegateTask.getUuid()).get())
        .isEqualTo(null);
    verify(waitNotifyEngine)
        .doneWith(
            delegateTask.getWaitId(), ExecutionStatusData.builder().executionStatus(ExecutionStatus.SUCCESS).build());
    verify(delegateTaskStatusObserverSubject).fireInform(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldProcessDelegateTaskResponseWithoutWaitId() {
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    delegateTaskService.processDelegateResponse(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(),
        DelegateTaskResponse.builder()
            .accountId(ACCOUNT_ID)
            .responseCode(ResponseCode.OK)
            .response(ExecutionStatusData.builder().executionStatus(ExecutionStatus.SUCCESS).build())
            .build());
    assertThat(persistence.createQuery(DelegateTask.class).filter(DelegateTaskKeys.uuid, delegateTask.getUuid()).get())
        .isEqualTo(null);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldProcessSyncDelegateTaskResponse() {
    thrown.expect(NoAvailableDelegatesException.class);
    DelegateTask delegateTask = saveDelegateTask(false, emptySet(), QUEUED);
    delegateTaskService.processDelegateResponse(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(),
        DelegateTaskResponse.builder()
            .accountId(ACCOUNT_ID)
            .responseCode(ResponseCode.OK)
            .response(ExecutionStatusData.builder().executionStatus(ExecutionStatus.SUCCESS).build())
            .build());
    delegateTask = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(delegateTask).isNull();
  }

  @Cache
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldAcquireTaskWhenQueued() {
    when(assignDelegateService.isWhitelisted(any(DelegateTask.class), any(String.class))).thenReturn(true);
    when(assignDelegateService.canAssign(
             any(BatchDelegateSelectionLog.class), any(String.class), any(DelegateTask.class)))
        .thenReturn(true);
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    Delegate delegate = createDelegateBuilder().build();
    delegate.setUuid(DELEGATE_ID);
    persistence.save(delegate);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    DelegateTaskPackage delegateTaskPackage =
        delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null);
    assertThat(delegateTaskPackage).isNotNull();
    assertThat(delegateTaskPackage.getDelegateTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskPackage.getDelegateId()).isEqualTo(DELEGATE_ID);
    assertThat(delegateTaskPackage.getAccountId()).isEqualTo(ACCOUNT_ID);

    delegateTask = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(delegateTask.getDelegateInstanceId()).isNull();
  }

  @Cache
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void onlyOneInstanceShouldAcquireTask() {
    when(assignDelegateService.isWhitelisted(any(DelegateTask.class), any(String.class))).thenReturn(true);
    when(assignDelegateService.canAssign(
             any(BatchDelegateSelectionLog.class), any(String.class), any(DelegateTask.class)))
        .thenReturn(true);
    Delegate delegate = createDelegateBuilder().build();
    delegate.setUuid(DELEGATE_ID);
    persistence.save(delegate);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    String delegateInstanceId = generateUuid();
    DelegateTaskPackage delegateTaskPackage = delegateTaskServiceClassic.acquireDelegateTask(
        ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), delegateInstanceId);
    assertThat(delegateTaskPackage).isNotNull();
    assertThat(delegateTaskPackage.getDelegateTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskPackage.getDelegateId()).isEqualTo(DELEGATE_ID);
    assertThat(delegateTaskPackage.getAccountId()).isEqualTo(ACCOUNT_ID);

    delegateTask = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(delegateTask.getDelegateInstanceId()).isEqualTo(delegateInstanceId);

    // other instance should not acquire
    delegateTaskPackage =
        delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), generateUuid());
    assertThat(delegateTaskPackage).isNull();
  }

  @Cache
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldAcquireTaskWhenQueued_notWhitelisted() {
    when(assignDelegateService.isWhitelisted(any(DelegateTask.class), any(String.class))).thenReturn(false);
    when(assignDelegateService.shouldValidate(any(DelegateTask.class), any(String.class))).thenReturn(true);
    when(assignDelegateService.canAssign(
             any(BatchDelegateSelectionLog.class), any(String.class), any(DelegateTask.class)))
        .thenReturn(true);
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    Delegate delegate = createDelegateBuilder().build();
    delegate.setUuid(DELEGATE_ID);
    persistence.save(delegate);
    delegateCache.get(ACCOUNT_ID, DELEGATE_ID, true);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    assertThat(delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null))
        .isNotNull();
  }

  @Cache
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldAcquireTaskWhenQueued_cannotAssign() {
    when(assignDelegateService.canAssign(
             any(BatchDelegateSelectionLog.class), any(String.class), any(DelegateTask.class)))
        .thenReturn(false);
    Delegate delegate = createDelegateBuilder().build();
    delegate.setUuid(DELEGATE_ID);
    persistence.save(delegate);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    assertThat(delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null))
        .isNull();
  }

  @Cache
  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldNotAcquireTaskWhenNoEligibleDelegates_cannotAssign() {
    when(assignDelegateService.canAssign(
             any(BatchDelegateSelectionLog.class), any(String.class), any(DelegateTask.class)))
        .thenReturn(false);
    when(assignDelegateService.retrieveActiveDelegates(eq(ACCOUNT_ID), any(BatchDelegateSelectionLog.class)))
        .thenReturn(singletonList(DELEGATE_ID));
    Delegate delegate = createDelegateBuilder().build();
    delegate.setUuid(DELEGATE_ID);
    persistence.save(delegate);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    assertThat(delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null))
        .isNull();
  }

  @Cache
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldAcquireTaskWhenQueued_blacklisted() {
    when(assignDelegateService.isWhitelisted(any(DelegateTask.class), any(String.class))).thenReturn(false);
    when(assignDelegateService.shouldValidate(any(DelegateTask.class), any(String.class))).thenReturn(false);
    when(assignDelegateService.canAssign(
             any(BatchDelegateSelectionLog.class), any(String.class), any(DelegateTask.class)))
        .thenReturn(true);
    Delegate delegate = createDelegateBuilder().build();
    delegate.setUuid(DELEGATE_ID);
    persistence.save(delegate);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    assertThat(delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null))
        .isNull();
  }

  @Cache
  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldNotAcquireTaskWhenAlreadyAcquired() {
    Delegate delegate = createDelegateBuilder().build();
    delegate.setUuid(DELEGATE_ID + "1");
    persistence.save(delegate);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    assertThat(
        delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, DELEGATE_ID + "1", delegateTask.getUuid(), null))
        .isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotAcquireTaskIfDelegateStatusNotEnabled() {
    String accountId = generateUuid();
    String delegateId = generateUuid();

    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(accountId);
    delegate.setUuid(delegateId);
    delegate.setStatus(DelegateInstanceStatus.WAITING_FOR_APPROVAL);
    persistence.save(delegate);

    DelegateTaskPackage delegateTaskPackage =
        delegateTaskServiceClassic.acquireDelegateTask(accountId, delegateId, generateUuid(), null);
    assertThat(delegateTaskPackage).isNull();

    delegate.setStatus(DelegateInstanceStatus.DELETED);
    persistence.save(delegate);

    delegateTaskPackage = delegateTaskServiceClassic.acquireDelegateTask(accountId, delegateId, generateUuid(), null);
    assertThat(delegateTaskPackage).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldNotAcquireTaskIfDelegateNotFoundInDb() {
    DelegateTaskPackage delegateTaskPackage =
        delegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, generateUuid(), generateUuid(), null);
    assertThat(delegateTaskPackage).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldFilterTaskForAccountOnAbort() {
    Delegate delegate = createDelegateBuilder().build();
    delegate.setAccountId(ACCOUNT_ID + "1");
    delegate.setUuid(DELEGATE_ID);
    persistence.save(delegate);
    DelegateTask delegateTask = saveDelegateTask(true, emptySet(), QUEUED);
    assertThat(delegateTaskServiceClassic.filter(DELEGATE_ID,
                   aDelegateTaskAbortEvent()
                       .withDelegateTaskId(delegateTask.getUuid())
                       .withAccountId(ACCOUNT_ID + "1")
                       .withSync(true)
                       .build()))
        .isFalse();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldReportConnectionResults_success() {
    createAccountDelegate();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(false, emptySet(), QUEUED);
    DelegateTaskPackage delegateTaskPackage =
        delegateTaskServiceClassic.reportConnectionResults(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null,
            singletonList(DelegateConnectionResult.builder()
                              .accountId(ACCOUNT_ID)
                              .delegateId(DELEGATE_ID)
                              .duration(100L)
                              .criteria("aaa")
                              .validated(true)
                              .build()));
    assertThat(delegateTaskPackage).isNotNull();
    assertThat(persistence.get(DelegateTask.class, delegateTask.getUuid()).getDelegateId()).isEqualTo(DELEGATE_ID);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldReportConnectionResults_fail() {
    thrown.expect(NoAvailableDelegatesException.class);
    DelegateTask delegateTask = saveDelegateTask(false, emptySet(), QUEUED);
    DelegateTaskPackage delegateTaskPackage =
        delegateTaskServiceClassic.reportConnectionResults(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null,
            singletonList(DelegateConnectionResult.builder()
                              .accountId(ACCOUNT_ID)
                              .delegateId(DELEGATE_ID)
                              .duration(100L)
                              .criteria("aaa")
                              .validated(false)
                              .build()));
    assertThat(delegateTaskPackage).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldReportConnectionResults_unavailable() {
    thrown.expect(NoAvailableDelegatesException.class);
    DelegateTask delegateTask = saveDelegateTask(false, emptySet(), STARTED);
    DelegateTaskPackage delegateTaskPackage =
        delegateTaskServiceClassic.reportConnectionResults(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), null,
            singletonList(DelegateConnectionResult.builder()
                              .accountId(ACCOUNT_ID)
                              .delegateId(DELEGATE_ID)
                              .duration(100L)
                              .criteria("aaa")
                              .validated(true)
                              .build()));
    assertThat(delegateTaskPackage).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldFailIfAllDelegatesFailed_all() {
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(true, ImmutableSet.of(DELEGATE_ID), QUEUED);
    delegateTask.setTaskActivityLogs(null);
    delegateTask.setBroadcastToDelegateIds(null);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(emptyList());

    delegateTaskServiceClassic.failIfAllDelegatesFailed(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), true);
    verify(assignDelegateService).connectedWhitelistedDelegates(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldFailIfAllDelegatesFailed_sync() {
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(false, ImmutableSet.of(DELEGATE_ID), QUEUED);
    delegateTask.setTaskActivityLogs(null);
    delegateTask.setBroadcastToDelegateIds(null);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(emptyList());
    delegateTaskServiceClassic.failIfAllDelegatesFailed(ACCOUNT_ID, "", delegateTask.getUuid(), true);
    verify(assignDelegateService).connectedWhitelistedDelegates(delegateTask);
    String expectedMessage =
        "No connected whitelisted delegates found for task and no eligible delegates could perform the required capabilities for this task: [ https://www.google.com ]\n"
        + "  -  The capabilities were tested by the following delegates: [ DELEGATE_ID ]\n"
        + "  -  Following delegates were validating but never returned: [  ]\n"
        + "  -  Other delegates (if any) may have been offline or were not eligible due to tag or scope restrictions.";
    RemoteMethodReturnValueData notifyResponse = (RemoteMethodReturnValueData) kryoSerializer.asInflatedObject(
        persistence.createQuery(DelegateSyncTaskResponse.class).get().getResponseData());

    assertThat(notifyResponse.getException().getMessage()).isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = MARKOM)
  @Category(UnitTests.class)
  public void shouldFailIfAllDelegatesFailedNoClientTools_sync() {
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(false, ImmutableSet.of(DELEGATE_ID), QUEUED);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(emptyList());
    delegateTaskServiceClassic.failIfAllDelegatesFailed(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), false);
    // verify(assignDelegateService).connectedWhitelistedDelegates(delegateTask);
    String expectedMessage =
        "No connected whitelisted delegates found for task and no eligible delegates could perform the required capabilities for this task: [ https://www.google.com ]\n"
        + "  -  The capabilities were tested by the following delegates: [ DELEGATE_ID ]\n"
        + "  -  Following delegates were validating but never returned: [  ]\n"
        + "  -  Other delegates (if any) may have been offline or were not eligible due to tag or scope restrictions."
        + "  -  This could be due to some client tools still being installed on the delegates. If this is the reason please retry in a few minutes.";
    RemoteMethodReturnValueData notifyResponse = (RemoteMethodReturnValueData) kryoSerializer.asInflatedObject(
        persistence.createQuery(DelegateSyncTaskResponse.class).get().getResponseData());

    assertThat(notifyResponse.getException().getMessage()).isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldFailIfAllDelegatesFailed_notAll() {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(ACCOUNT_ID)
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .version(VERSION)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validatingDelegateIds(ImmutableSet.of("delegate2"))
            .validationCompleteDelegateIds(ImmutableSet.of("delegate3"))
            .build();

    when(assignDelegateService.getEligibleDelegatesToExecuteTask(delegateTask, null))
        .thenReturn(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    delegateTaskServiceClassic.processDelegateTask(delegateTask, QUEUED);

    delegateTaskServiceClassic.failIfAllDelegatesFailed(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), true);
    delegateTask.setTaskActivityLogs(null);
    delegateTask.setBroadcastToDelegateIds(null);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isEqualTo(delegateTask);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldFailIfAllDelegatesFailed_whitelist() {
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(true, ImmutableSet.of(DELEGATE_ID), QUEUED);
    delegateTask.setTaskActivityLogs(null);
    delegateTask.setBroadcastToDelegateIds(null);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(singletonList("delegate2"));
    delegateTaskServiceClassic.failIfAllDelegatesFailed(ACCOUNT_ID, DELEGATE_ID, delegateTask.getUuid(), true);
    verify(assignDelegateService).connectedWhitelistedDelegates(delegateTask);
    delegateTask.setTaskActivityLogs(null);
    delegateTask.setBroadcastToDelegateIds(null);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isEqualTo(delegateTask);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExpireTask() {
    createAccountDelegate();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(true, ImmutableSet.of(DELEGATE_ID), QUEUED);

    delegateTaskServiceClassic.expireTask(ACCOUNT_ID, delegateTask.getUuid());
    assertThat(persistence.createQuery(DelegateTask.class).get().getStatus()).isEqualTo(ERROR);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldAbortTask() {
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    DelegateTask delegateTask = saveDelegateTask(true, ImmutableSet.of(DELEGATE_ID), QUEUED);

    DelegateTask oldTask = delegateTaskServiceClassic.abortTask(ACCOUNT_ID, delegateTask.getUuid());

    assertThat(oldTask.getUuid()).isEqualTo(delegateTask.getUuid());
    assertThat(oldTask.getStatus()).isEqualTo(QUEUED);
    assertThat(persistence.createQuery(DelegateTask.class).get().getStatus()).isEqualTo(ABORTED);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldScheduleSyncTaskThrowNoEligibleDelegatesException() {
    when(assignDelegateService.retrieveActiveDelegates(anyString(), any())).thenReturn(emptyList());
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    TaskData taskData = TaskData.builder().taskType(TaskType.HELM_COMMAND_TASK.name()).build();
    DelegateTask task = DelegateTask.builder().accountId(ACCOUNT_ID).delegateId(DELEGATE_ID).data(taskData).build();
    when(assignDelegateService.noInstalledDelegates(ACCOUNT_ID)).thenReturn(true);

    delegateTaskServiceClassic.scheduleSyncTask(task);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldScheduleSyncTaskThrowNoAvailableDelegatesException() {
    when(assignDelegateService.retrieveActiveDelegates(anyString(), any())).thenReturn(emptyList());
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    thrown.expect(NoAvailableDelegatesException.class);
    TaskData taskData = TaskData.builder().taskType(TaskType.HELM_COMMAND_TASK.name()).build();
    DelegateTask task = DelegateTask.builder().accountId(ACCOUNT_ID).delegateId(DELEGATE_ID).data(taskData).build();
    when(assignDelegateService.noInstalledDelegates(ACCOUNT_ID)).thenReturn(false);

    delegateTaskServiceClassic.scheduleSyncTask(task);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testConvertSelectorsToExecutionCapabilityTaskSelectors_TaskSelectors() {
    List<String> selectors = Arrays.asList("a", "b");

    DelegateTask delegateTask =
        DelegateTask.builder().accountId(ACCOUNT_ID).delegateId(DELEGATE_ID).tags(selectors).build();

    delegateTaskServiceClassic.convertToExecutionCapability(delegateTask);

    List<SelectorCapability> selectorsCapabilityList = delegateTask.getExecutionCapabilities()
                                                           .stream()
                                                           .filter(c -> c instanceof SelectorCapability)
                                                           .map(c -> (SelectorCapability) c)
                                                           .collect(Collectors.toList());

    assertThat(selectorsCapabilityList.get(0).getSelectorOrigin()).isEqualTo(TASK_SELECTORS);
    assertThat(selectorsCapabilityList.get(0).getSelectors()).isEqualTo(new HashSet<>(selectors));
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testConvertSelectorsToExecutionCapabilityTaskSelectors_TaskCategory() {
    Set<String> commandMapSelectors = new HashSet<>();
    commandMapSelectors.add("eee");
    TaskSelectorMap sampleMap = TaskSelectorMap.builder()
                                    .accountId(ACCOUNT_ID)
                                    .taskGroup(TaskGroup.HTTP)
                                    .selectors(commandMapSelectors)
                                    .build();
    persistence.save(sampleMap);

    // CG Task
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .delegateId(DELEGATE_ID)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).build())
                                    .build();

    delegateTaskServiceClassic.convertToExecutionCapability(delegateTask);

    List<SelectorCapability> selectorsCapabilityList = delegateTask.getExecutionCapabilities()
                                                           .stream()
                                                           .filter(c -> c instanceof SelectorCapability)
                                                           .map(c -> (SelectorCapability) c)
                                                           .collect(Collectors.toList());

    assertThat(selectorsCapabilityList.get(0).getSelectorOrigin()).isEqualTo(TASK_CATEGORY_MAP);
    assertThat(selectorsCapabilityList.get(0).getSelectors()).isEqualTo(sampleMap.getSelectors());

    // NG Task
    DelegateTask delegateTaskNg = DelegateTask.builder()
                                      .accountId(ACCOUNT_ID)
                                      .delegateId(DELEGATE_ID)
                                      .data(TaskData.builder().taskType(TaskType.HTTP.name()).build())
                                      .setupAbstraction("ng", "true")
                                      .build();

    delegateTaskServiceClassic.convertToExecutionCapability(delegateTaskNg);

    selectorsCapabilityList = delegateTaskNg.getExecutionCapabilities()
                                  .stream()
                                  .filter(c -> c instanceof SelectorCapability)
                                  .map(c -> (SelectorCapability) c)
                                  .collect(Collectors.toList());

    assertThat(selectorsCapabilityList).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateCapabilityTaskSelectionDetailsInstance() {
    DelegateTask task = DelegateTask.builder().build();
    CapabilityRequirement capabilityRequirement = buildCapabilityRequirement();

    // Test case with partial arguments
    delegateTaskServiceClassic.createCapabilityTaskSelectionDetailsInstance(task, capabilityRequirement, null);
    verify(capabilityService)
        .buildCapabilityTaskSelectionDetails(capabilityRequirement, null, task.getSetupAbstractions(), null, null);

    // Test case with all arguments
    task.setData(TaskData.builder().taskType(TaskType.HTTP.name()).build());

    Map<String, String> taskSetupAbstractions = new HashMap<>();
    taskSetupAbstractions.put("foo", "bar");
    task.setSetupAbstractions(taskSetupAbstractions);

    task.setExecutionCapabilities(Arrays.asList(
        SelectorCapability.builder().selectorOrigin("TASK_SELECTORS").selectors(Collections.singleton("sel1")).build(),
        HttpConnectionExecutionCapability.builder().url("https://google.com").build()));

    List<String> assignableDelegateIds = Arrays.asList("del1", "del2");

    delegateTaskServiceClassic.createCapabilityTaskSelectionDetailsInstance(
        task, capabilityRequirement, assignableDelegateIds);

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(capabilityService)
        .buildCapabilityTaskSelectionDetails(eq(capabilityRequirement), eq(TaskGroup.HTTP),
            eq(task.getSetupAbstractions()), captor.capture(), eq(assignableDelegateIds));
    List<SelectorCapability> selectorCapabilities = captor.getValue();
    assertThat(selectorCapabilities).hasSize(1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTask_Sync() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(DELEGATE_ID);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotSaveDelegateTaskWhenNoEligibleDelegate_sync() {
    DelegateTask delegateTask = getDelegateTask();
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    assertThat(persistence.get(DelegateTask.class, delegateTask.getUuid())).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotSaveDelegateTaskWhenNoActiveEligibleDelegate_sync() {
    DelegateTask delegateTask = getDelegateTask();
    thrown.expect(NoAvailableDelegatesException.class);
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));

    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    assertThat(persistence.get(DelegateTask.class, delegateTask.getUuid())).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTask_async() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.queueTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(DELEGATE_ID);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotSaveDelegateTaskWhenNoEligibleDelegate_async() {
    DelegateTask delegateTask = getDelegateTask();
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    delegateTaskServiceClassic.queueTask(delegateTask);
    assertThat(persistence.get(DelegateTask.class, delegateTask.getUuid())).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWhenNoActiveEligibleDelegate_async() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.queueTask(delegateTask);
    assertThat(persistence.get(DelegateTask.class, delegateTask.getUuid())).isNotNull();
    assertThat(persistence.get(DelegateTask.class, delegateTask.getUuid()).getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateQueuedEventForTaskPoll_async() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.queueTask(delegateTask);
    List<DelegateTaskEvent> delegateTaskEvents =
        delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, false);
    assertThat(delegateTaskEvents).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateQueuedEventForTaskPollWhenNoEligibleDelegates_async() {
    DelegateTask delegateTask = getDelegateTask();
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    delegateTaskServiceClassic.queueTask(delegateTask);
    List<DelegateTaskEvent> delegateTaskEvents =
        delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, false);
    assertThat(delegateTaskEvents).isEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateQueuedEventForTaskPollWhenNonEligibleDelegateAcquire_async() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.queueTask(delegateTask);
    List<DelegateTaskEvent> delegateTaskEvents =
        delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, "delegateid2", false);
    assertThat(delegateTaskEvents).isEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateQueuedEventForTaskPoll_sync() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.executeTask(delegateTask);
    List<DelegateTaskEvent> delegateTaskEvents =
        delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, true);
    assertThat(delegateTaskEvents).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateQueuedEventForTaskPollWhenNoEligibleDelegates_sync() {
    DelegateTask delegateTask = getDelegateTask();
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    delegateTaskServiceClassic.queueTask(delegateTask);
    List<DelegateTaskEvent> delegateTaskEvents =
        delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, DELEGATE_ID, true);
    assertThat(delegateTaskEvents).isEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateQueuedEventForTaskPollWhenNonEligibleDelegateAcquire_sync() {
    DelegateTask delegateTask = getDelegateTask();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(
             any(DelegateTask.class), any(BatchDelegateSelectionLog.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), anyString(), anyObject()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    delegateTaskServiceClassic.queueTask(delegateTask);
    List<DelegateTaskEvent> delegateTaskEvents =
        delegateTaskServiceClassic.getDelegateTaskEvents(ACCOUNT_ID, "delegateid2", true);
    assertThat(delegateTaskEvents).isEmpty();
  }

  private CapabilityRequirement buildCapabilityRequirement() {
    return CapabilityRequirement.builder()
        .accountId(generateUuid())
        .uuid(generateUuid())
        .validUntil(Date.from(Instant.now().plus(Duration.ofDays(30))))
        .capabilityType(CapabilityType.HTTP.name())
        .capabilityParameters(
            CapabilityParameters.newBuilder()
                .setHttpConnectionParameters(HttpConnectionParameters.newBuilder().setUrl("https://google.com"))
                .build())
        .build();
  }

  private CapabilityTaskSelectionDetails buildCapabilityTaskSelectionDetails() {
    Map<String, Set<String>> taskSelectors = new HashMap<>();
    taskSelectors.put("TASK_SELECTORS", Collections.singleton("value1"));
    taskSelectors.put("TASK_CATEGORY_MAP", Collections.singleton("value2"));

    Map<String, String> taskSetupAbstractions = new HashMap<>();
    taskSetupAbstractions.put("appId", "app1");
    taskSetupAbstractions.put("envId", "env1");
    taskSetupAbstractions.put("infrastructureMappingId", "infra1");
    taskSetupAbstractions.put("foo", "bar");

    return CapabilityTaskSelectionDetails.builder()
        .uuid(generateUuid())
        .accountId(generateUuid())
        .capabilityId(generateUuid())
        .taskGroup(TaskGroup.HTTP)
        .taskSelectors(taskSelectors)
        .taskSetupAbstractions(taskSetupAbstractions)
        .blocked(true)
        .validUntil(Date.from(Instant.now().plus(Duration.ofDays(30))))
        .build();
  }

  private DelegateTask saveDelegateTask(boolean async, Set<String> validatingTaskIds, DelegateTask.Status status) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(ACCOUNT_ID)
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .version(VERSION)
            .data(TaskData.builder()
                      .async(async)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validatingDelegateIds(validatingTaskIds)
            .validationCompleteDelegateIds(ImmutableSet.of(DELEGATE_ID))
            .build();

    when(assignDelegateService.getEligibleDelegatesToExecuteTask(delegateTask, null))
        .thenReturn(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    delegateTaskServiceClassic.processDelegateTask(delegateTask, status);
    return delegateTask;
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(DELEGATE_TYPE)
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  private static JreConfig getOpenjdkJreConfig() {
    return JreConfig.builder()
        .version("1.8.0_242")
        .jreDirectory("jdk8u242-b08-jre")
        .jreMacDirectory("jdk8u242-b08-jre")
        .jreTarPath("jre/openjdk-8u242/jre_x64_${OS}_8u242b08.tar.gz")
        .build();
  }

  private String createAccountDelegate() {
    Delegate delegate = createDelegateBuilder().build();
    persistence.save(delegate);
    return delegate.getUuid();
  }

  private DelegateTask getDelegateTask() {
    return DelegateTask.builder()
        .uuid(generateUuid())
        .accountId(ACCOUNT_ID)
        .waitId(generateUuid())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
        .version(VERSION)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.HTTP.name())
                  .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                  .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                  .build())
        .tags(new ArrayList<>())
        .build();
  }
}
