/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.gitops;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.gitops.models.Agent;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

public class AgentResourceImplTest extends CategoryTest {
  @Mock private GitopsResourceClient gitopsResourceClient;
  @Inject @InjectMocks AgentResourceImpl agentResource;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getType() {
    assertThat(agentResource.getType()).isEqualTo("GITOPS_AGENT");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getValidScopeLevels() {
    assertThat(agentResource.getValidScopeLevels())
        .containsExactlyInAnyOrder(ScopeLevel.PROJECT, ScopeLevel.ORGANIZATION, ScopeLevel.ACCOUNT);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getEventFrameworkEntityType() {
    assertThat(agentResource.getEventFrameworkEntityType().get())
        .isEqualTo(EventsFrameworkMetadataConstants.GITOPS_AGENT_ENTITY);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getResourceInfoFromEvent() {}

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void validateEmptyResourceList() {
    assertThat(
        agentResource.validate(new ArrayList<>(), Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)))
        .isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    List<String> resourceIds = new ArrayList<>();
    List<Agent> agents = new ArrayList<>();
    for (int i = 0; i < 205; i++) {
      resourceIds.add(String.valueOf(i));
      agents.add(Agent.builder().identifier(String.valueOf(i * 2)).build());
    }

    Call call = Mockito.mock(Call.class);

    when(call.execute())
        .thenReturn(Response.success(PageResponse.<Agent>builder().content(agents.subList(0, 100)).build()))
        .thenReturn(Response.success(PageResponse.<Agent>builder().content(agents.subList(100, 200)).build()))
        .thenReturn(Response.success(PageResponse.<Agent>builder().content(agents.subList(200, 205)).build()));
    doReturn(call).when(gitopsResourceClient).listAgents(any(), any(), any(), anyInt(), anyInt());

    final List<Boolean> validate =
        agentResource.validate(resourceIds, Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER));
    assertThat(validate).hasSize(205);
    for (int i = 0; i < 205; i++) {
      if (i % 2 == 0 && validate.get(i) == Boolean.FALSE) {
        fail("even numbered agents should be valid");
      }
      if (i % 2 != 0 && validate.get(i) == Boolean.TRUE) {
        fail("odd numbered agents should be valid");
      }
    }
  }
}
