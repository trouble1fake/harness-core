/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.CHANGED_FILES;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.REGEX;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.TaskType.SCM_PATH_FILTER_EVALUATION_TASK;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskParams;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.ng.core.NGAccess;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class TriggerFilePathConditionFilterTest extends CategoryTest {
  @Mock private TaskExecutionUtils taskExecutionUtils;
  @Mock private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private NGTriggerService ngTriggerService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private ConnectorUtils connectorUtils;
  @Inject @InjectMocks private FilepathTriggerFilter filter;
  private static List<NGTriggerEntity> triggerEntities;

  String pushPayload = "{\"commits\": [\n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest1.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File1_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value1.yml\"\n"
      + "    ]\n"
      + "  }, \n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest2.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File2_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value2.yml\"\n"
      + "    ]\n"
      + "  }\n"
      + "]}";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPathFilterEvaluationNotNeeded() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder().webhookPayloadData(WebhookPayloadData.builder().build()).build();
    assertThat(filter.pathFilterEvaluationNotNeeded(filterRequestData)).isTrue();

    filterRequestData.setWebhookPayloadData(
        WebhookPayloadData.builder()
            .originalEvent(TriggerWebhookEvent.builder()
                               .sourceRepoType(WebhookTriggerType.AWS_CODECOMMIT.getEntityMetadataName())
                               .build())
            .build());
    assertThat(filter.pathFilterEvaluationNotNeeded(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testInitiateDeleteTaskAndEvaluateForPR() {
    // Init Data
    TriggerDetails triggerDetails = generateTriggerDetails();

    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(githubConnectorDTO)
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(kryoSerializer)
        .asInflatedObject(data);

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateDeleteTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(githubConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPrNumber()).isEqualTo(2);
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");

    // DelegateTask returns Error
    doReturn(ErrorNotifyResponseData.builder().errorMessage("error").build())
        .when(kryoSerializer)
        .asInflatedObject(data);
    assertThat(filter.initiateDeleteTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isFalse();
  }

  private TriggerDetails generateTriggerDetails() {
    NGTriggerEntity ngTriggerEntity =
        NGTriggerEntity.builder()
            .identifier("id")
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .git(GitMetadata.builder().connectorIdentifier("account.conn").build())
                                       .build())
                          .build())
            .build();
    return TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testInitiateDeleteTaskAndEvaluateForPush() {
    // Init Data
    TriggerDetails triggerDetails = generateTriggerDetails();

    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder().build();
    List<EncryptedDataDetail> encryptedDataDetails = emptyList();
    TriggerEventDataCondition pathCondition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("test").build();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(PushHook.newBuilder().setBefore("before").setAfter("after").setRef("ref").build())
            .build();
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder().parseWebhookResponse(parseWebhookResponse).build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder().accountId("acc").webhookPayloadData(webhookPayloadData).build();

    // Mock apis
    doReturn(ConnectorDetails.builder()
                 .connectorConfig(gitlabConnectorDTO)
                 .encryptedDataDetails(encryptedDataDetails)
                 .build())
        .when(connectorUtils)
        .getConnectorDetails(any(NGAccess.class), eq("account.conn"));

    byte[] data = new byte[0];
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().data(data).build());
    doReturn(ScmPathFilterEvaluationTaskResponse.builder().matched(true).build())
        .when(kryoSerializer)
        .asInflatedObject(data);

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    assertThat(filter.initiateDeleteTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition)).isTrue();
    verify(taskExecutionUtils, times(1)).executeSyncTask(argumentCaptor.capture());

    // Assert Delegate Task request object generated
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("acc");
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(SCM_PATH_FILTER_EVALUATION_TASK.toString());

    assertThat(delegateTaskRequest.getTaskParameters()).isNotNull();

    TaskParameters taskParameters = delegateTaskRequest.getTaskParameters();
    assertThat(ScmPathFilterEvaluationTaskParams.class.isAssignableFrom(taskParameters.getClass()));
    ScmPathFilterEvaluationTaskParams params = (ScmPathFilterEvaluationTaskParams) taskParameters;
    assertThat(params.getScmConnector()).isEqualTo(gitlabConnectorDTO);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getPreviousCommit()).isEqualTo("before");
    assertThat(params.getLatestCommit()).isEqualTo("after");
    assertThat(params.getBranch()).isEqualTo("ref");
    assertThat(params.getOperator()).isEqualTo(EQUALS.getValue());
    assertThat(params.getStandard()).isEqualTo("test");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void evaluateFromPushPayload() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build();

    TriggerEventDataCondition condition =
        TriggerEventDataCondition.builder().key(CHANGED_FILES).operator(EQUALS).value("spec/manifest1.yml").build();
    assertThat(filter.evaluateFromPushPayload(filterRequestData, condition)).isTrue();

    condition.setOperator(REGEX);
    condition.setValue("(^spec/manifest)[0-9](.yml1$)");
    assertThat(filter.evaluateFromPushPayload(filterRequestData, condition)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldEvaluateOnDelegate() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build();
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload("").sourceRepoType("Github").build();

    WebhookPayloadData webhookPayloadData = WebhookPayloadData.builder()
                                                .originalEvent(triggerWebhookEvent)
                                                .parseWebhookResponse(parseWebhookResponse)
                                                .build();
    FilterRequestData filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();

    // PR
    assertThat(filter.shouldEvaluateOnDelegate(filterRequestData)).isTrue();

    // Push Github , commits < 20
    List<Commit> commits = Arrays.asList(Commit.newBuilder().build(), Commit.newBuilder().build());
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnDelegate(filterRequestData)).isFalse();

    // Push gitlab , commits < 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnDelegate(filterRequestData)).isFalse();

    // Push Bitbucket , commits < 20
    commits = emptyList();
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Bitbucket");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnDelegate(filterRequestData)).isTrue();

    commits = new ArrayList<>();
    Commit commit = Commit.newBuilder().build();
    for (int i = 0; i < 20; i++) {
      commits.add(commit);
    }
    // Push Github , commits > 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("github");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnDelegate(filterRequestData)).isTrue();

    // Push gitlab , commits > 20
    parseWebhookResponse =
        ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().addAllCommits(commits).build()).build();
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    webhookPayloadData = WebhookPayloadData.builder()
                             .originalEvent(triggerWebhookEvent)
                             .parseWebhookResponse(parseWebhookResponse)
                             .build();
    filterRequestData = FilterRequestData.builder().webhookPayloadData(webhookPayloadData).build();
    assertThat(filter.shouldEvaluateOnDelegate(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetFilesFromPushPayload() {
    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").build();
    Set<String> filesFromPushPayload = filter.getFilesFromPushPayload(
        FilterRequestData.builder()
            .webhookPayloadData(WebhookPayloadData.builder().originalEvent(triggerWebhookEvent).build())
            .build());

    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
    triggerWebhookEvent.setSourceRepoType("GITHUB");
    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
    triggerWebhookEvent.setSourceRepoType("Gitlab");
    assertThat(filesFromPushPayload)
        .containsExactlyInAnyOrder("spec/manifest1.yml", "spec/manifest2.yml", "File1_Removed.txt", "File2_Removed.txt",
            "values/value1.yml", "values/value2.yml");
  }
}
