/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.remote.client.NGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.dto.UserPrincipal;
import io.harness.steps.approval.step.ApprovalInstanceResponseMapper;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(PowerMockRunner.class)
@OwnedBy(PIPELINE)
@PrepareForTest(NGRestUtils.class)
public class ApprovalResourceServiceImplTest extends CategoryTest {
  @Mock private ApprovalInstanceService approvalInstanceService;
  @Mock private ApprovalInstanceResponseMapper approvalInstanceResponseMapper;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private UserGroupClient userGroupClient;
  @Mock private CurrentUserHelper currentUserHelper;
  @Mock private UserClient userClient;
  ApprovalResourceServiceImpl approvalResourceService;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    approvalResourceService = new ApprovalResourceServiceImpl(approvalInstanceService, approvalInstanceResponseMapper,
        planExecutionService, userGroupClient, currentUserHelper, userClient);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGet() {
    String id = "dummy";
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO = ApprovalInstanceResponseDTO.builder().id(id).build();
    ApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setId(id);
    when(approvalInstanceService.get(id)).thenReturn(approvalInstance);
    when(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(approvalInstance))
        .thenReturn(approvalInstanceResponseDTO);
    assertEquals(approvalResourceService.get(id), approvalInstanceResponseDTO);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivity() throws IOException {
    PowerMockito.mockStatic(NGRestUtils.class);
    String uuid = "uuid";
    String id = "dummy";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid(uuid).build();
    List<String> userGroups = new ArrayList<>();
    userGroups.add("approver");
    HarnessApprovalInstance harnessApprovalInstance =
        HarnessApprovalInstance.builder().approvers(ApproversDTO.builder().userGroups(userGroups).build()).build();
    harnessApprovalInstance.setAmbiance(ambiance);
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO =
        HarnessApprovalActivityRequestDTO.builder().build();
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(harnessApprovalInstance);
    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("email@harness.io", "name", "user", "ACCOUNTID"));
    Call userCall = mock(Call.class);
    when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    // Should approve successfully
    approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO);

    harnessApprovalInstance.getApprovers().setUserGroups(Collections.emptyList());
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    harnessApprovalInstance.getApprovers().setDisallowPipelineExecutor(true);
    PlanExecution planExecution =
        PlanExecution.builder()
            .metadata(ExecutionMetadata.newBuilder()
                          .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                              .setTriggeredBy(TriggeredBy.newBuilder().setUuid(uuid).build())
                                              .build())
                          .build())
            .build();
    when(planExecutionService.get(any())).thenReturn(planExecution);
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    harnessApprovalInstance.setApprovalActivities(
        Collections.singletonList(HarnessApprovalActivity.builder().user(embeddedUser).build()));
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");
  }
}
