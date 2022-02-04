/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.recommendation;

import static io.harness.rule.OwnerRule.UTSAV;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.graphql.core.recommendation.RecommendationService;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsDTO;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewRule;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import graphql.com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.jooq.Condition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RecommendationsOverviewQueryV2Test extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String NAME = "name";
  private static final String CLUSTER_NAME = "clusterName";
  private static final Double MONTHLY_COST = 100D;
  private static final Double MONTHLY_SAVING = 40D;
  private static final String NAMESPACE = "namespace";
  private static final String ID = "id0";
  private static final String PERSPECTIVE_ID = "perspectiveId";

  private static final K8sRecommendationFilterDTO defaultFilter = K8sRecommendationFilterDTO.builder()
                                                                      .limit(GraphQLUtils.DEFAULT_LIMIT)
                                                                      .offset(GraphQLUtils.DEFAULT_OFFSET)
                                                                      .build();
  private ArgumentCaptor<Condition> conditionCaptor;

  @Mock private GraphQLUtils graphQLUtils;
  @Mock private RecommendationService recommendationService;
  @Mock private CEViewService viewService;
  @InjectMocks private RecommendationsOverviewQueryV2 overviewQuery;

  @Before
  public void setUp() throws Exception {
    when(graphQLUtils.getAccountIdentifier(any())).thenReturn(ACCOUNT_ID);

    conditionCaptor = ArgumentCaptor.forClass(Condition.class);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationsOverviewQueryWithZeroItems() {
    when(recommendationService.listAll(
             eq(ACCOUNT_ID), any(Condition.class), eq(GraphQLUtils.DEFAULT_OFFSET), eq(GraphQLUtils.DEFAULT_LIMIT)))
        .thenReturn(Collections.emptyList());

    K8sRecommendationFilterDTO filter = K8sRecommendationFilterDTO.builder()
                                            .limit(GraphQLUtils.DEFAULT_LIMIT)
                                            .offset(GraphQLUtils.DEFAULT_OFFSET)
                                            .build();
    final RecommendationsDTO recommendationsDTO = overviewQuery.recommendations(filter, null);

    assertRecommendationOverviewListResponse(recommendationsDTO);
    assertThat(recommendationsDTO.getItems()).isEmpty();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationsOverviewQueryWithIdFilter() {
    when(recommendationService.listAll(
             eq(ACCOUNT_ID), any(Condition.class), eq(GraphQLUtils.DEFAULT_OFFSET), eq(GraphQLUtils.DEFAULT_LIMIT)))
        .thenReturn(ImmutableList.of(createRecommendationItem("id0", ResourceType.WORKLOAD)));

    K8sRecommendationFilterDTO filter = K8sRecommendationFilterDTO.builder()
                                            .ids(singletonList(ID))
                                            .limit(GraphQLUtils.DEFAULT_LIMIT)
                                            .offset(GraphQLUtils.DEFAULT_OFFSET)
                                            .build();
    final RecommendationsDTO recommendationsDTO = overviewQuery.recommendations(filter, null);

    assertRecommendationOverviewListResponse(recommendationsDTO);
    assertThat(recommendationsDTO.getItems()).containsExactly(createRecommendationItem("id0"));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationsOverviewQueryWithNoFilter() {
    when(recommendationService.listAll(eq(ACCOUNT_ID), any(Condition.class), any(), any()))
        .thenReturn(ImmutableList.of(createRecommendationItem("id0"), createRecommendationItem("id1")));

    final RecommendationsDTO recommendationsDTO = overviewQuery.recommendations(defaultFilter, null);

    assertRecommendationOverviewListResponse(recommendationsDTO);
    assertThat(recommendationsDTO.getItems())
        .containsExactlyInAnyOrder(createRecommendationItem("id0"), createRecommendationItem("id1"));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testListQueryWithAllFiltersExceptId() {
    when(recommendationService.listAll(eq(ACCOUNT_ID), any(Condition.class), any(), any()))
        .thenReturn(ImmutableList.of(createRecommendationItem("id0"), createRecommendationItem("id1")));

    K8sRecommendationFilterDTO filter = K8sRecommendationFilterDTO.builder()
                                            .names(singletonList(NAME))
                                            .namespaces(singletonList(NAMESPACE))
                                            .clusterNames(singletonList(CLUSTER_NAME))
                                            .resourceTypes(singletonList(ResourceType.WORKLOAD))
                                            .minCost(0D)
                                            .minSaving(0D)
                                            .limit(GraphQLUtils.DEFAULT_LIMIT)
                                            .offset(GraphQLUtils.DEFAULT_OFFSET)
                                            .build();

    final RecommendationsDTO recommendationsDTO = overviewQuery.recommendations(filter, null);

    assertRecommendationOverviewListResponse(recommendationsDTO);
    assertThat(recommendationsDTO.getItems())
        .containsExactlyInAnyOrder(createRecommendationItem("id0"), createRecommendationItem("id1"));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testListQueryWithPerspectiveRuleFilters() {
    final QLCEViewFilter viewFilter = createViewFilter("name", QLCEViewFilterOperator.IN, NAME);

    final K8sRecommendationFilterDTO filter = createPerspectiveViewFilter(viewFilter);

    when(recommendationService.listAll(eq(ACCOUNT_ID), conditionCaptor.capture(), any(), any()))
        .thenReturn(ImmutableList.of(createRecommendationItem("id0"), createRecommendationItem("id1")));

    final RecommendationsDTO recommendationsDTO = overviewQuery.recommendations(filter, null);

    assertRecommendationOverviewListResponse(recommendationsDTO);
    assertThat(recommendationsDTO.getItems())
        .containsExactlyInAnyOrder(createRecommendationItem("id0"), createRecommendationItem("id1"));

    final Condition condition = conditionCaptor.getValue();

    assertThat(condition).isNotNull();
    assertThat(condition.toString()).contains("in").contains(NAME);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testListQueryWithPerspectiveMetadataFilters() {
    final K8sRecommendationFilterDTO filter = createPerspectiveMetadataFilter();

    when(recommendationService.listAll(eq(ACCOUNT_ID), conditionCaptor.capture(), any(), any()))
        .thenReturn(ImmutableList.of(createRecommendationItem("id0"), createRecommendationItem("id1")));

    when(viewService.get(eq(PERSPECTIVE_ID))).thenReturn(createCEView(NAME, ViewIdOperator.NOT_IN));

    final RecommendationsDTO recommendationsDTO = overviewQuery.recommendations(filter, null);

    assertRecommendationOverviewListResponse(recommendationsDTO);
    assertThat(recommendationsDTO.getItems())
        .containsExactlyInAnyOrder(createRecommendationItem("id0"), createRecommendationItem("id1"));

    final Condition condition = conditionCaptor.getValue();

    final String expectedCondition = "\"public\".\"ce_recommendations\".\"name\" not in ('name')";

    assertThat(condition).isNotNull();
    assertThat(condition.toString()).contains(expectedCondition);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testListQueryWithPerspectiveFiltersAllFields() {
    final K8sRecommendationFilterDTO filter = createPerspectiveExhaustiveFilter();

    when(recommendationService.listAll(eq(ACCOUNT_ID), conditionCaptor.capture(), any(), any()))
        .thenReturn(ImmutableList.of(createRecommendationItem("id0"), createRecommendationItem("id1")));

    when(viewService.get(eq(PERSPECTIVE_ID))).thenReturn(createCEView(NAME, ViewIdOperator.NOT_NULL));

    final RecommendationsDTO recommendationsDTO = overviewQuery.recommendations(filter, null);

    assertRecommendationOverviewListResponse(recommendationsDTO);
    assertThat(recommendationsDTO.getItems())
        .containsExactlyInAnyOrder(createRecommendationItem("id0"), createRecommendationItem("id1"));

    final Condition condition = conditionCaptor.getValue();

    final String expectedConditon = "  and (\n"
        + "    (\n"
        + "      \"public\".\"ce_recommendations\".\"clustername\" in ('clusterName')\n"
        + "      and \"public\".\"ce_recommendations\".\"namespace\" in ('namespace')\n"
        + "      and \"public\".\"ce_recommendations\".\"name\" not in ('name')\n"
        + "    )\n"
        + "    or \"public\".\"ce_recommendations\".\"name\" is not null\n"
        + "  )\n"
        + "  and \"public\".\"ce_recommendations\".\"clustername\" in ('clusterName')";

    assertThat(condition).isNotNull();
    assertThat(condition.toString()).contains(expectedConditon);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testListQueryWithMetadataFiltersViewIdNotPresent() {
    final K8sRecommendationFilterDTO filter = createPerspectiveMetadataFilter();

    when(viewService.get(eq(PERSPECTIVE_ID))).thenReturn(null);

    assertThatThrownBy(() -> overviewQuery.recommendations(filter, null))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(PERSPECTIVE_ID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationsOverviewListQueryWithPerspectiveFiltersThrowsError() {
    final QLCEViewFilter viewFilter = createViewFilter("randomField", QLCEViewFilterOperator.IN, NAME);

    final K8sRecommendationFilterDTO filter = createPerspectiveViewFilter(viewFilter);

    when(recommendationService.listAll(any(), any(), any(), any()))
        .thenReturn(ImmutableList.of(createRecommendationItem("id0"), createRecommendationItem("id1")));

    assertThatThrownBy(() -> overviewQuery.recommendations(filter, null))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("doesnt exist");
  }

  private static K8sRecommendationFilterDTO createPerspectiveViewFilter(QLCEViewFilter viewFilter) {
    final List<QLCEViewFilterWrapper> perspectiveFilters =
        ImmutableList.of(QLCEViewFilterWrapper.builder()
                             .ruleFilter(QLCEViewRule.builder().conditions(ImmutableList.of(viewFilter)).build())
                             .build());

    return buildFilter(perspectiveFilters);
  }

  private static K8sRecommendationFilterDTO createPerspectiveMetadataFilter() {
    final List<QLCEViewFilterWrapper> perspectiveFilters =
        ImmutableList.of(QLCEViewFilterWrapper.builder()
                             .viewMetadataFilter(QLCEViewMetadataFilter.builder().viewId(PERSPECTIVE_ID).build())
                             .build());

    return buildFilter(perspectiveFilters);
  }

  private static K8sRecommendationFilterDTO createPerspectiveExhaustiveFilter() {
    final List<QLCEViewFilter> conditions =
        ImmutableList.of(createViewFilter("clusterName", QLCEViewFilterOperator.IN, CLUSTER_NAME),
            createViewFilter("namespace", QLCEViewFilterOperator.IN, NAMESPACE),
            createViewFilter("workloadName", QLCEViewFilterOperator.NOT_IN, NAME)

        );

    final List<QLCEViewFilterWrapper> perspectiveFilters =
        ImmutableList.of(QLCEViewFilterWrapper.builder()
                             .viewMetadataFilter(QLCEViewMetadataFilter.builder().viewId(PERSPECTIVE_ID).build())
                             .ruleFilter(QLCEViewRule.builder().conditions(conditions).build())
                             .idFilter(createViewFilter("clusterName", QLCEViewFilterOperator.IN, CLUSTER_NAME))
                             .build());

    return buildFilter(perspectiveFilters);
  }

  private static K8sRecommendationFilterDTO buildFilter(List<QLCEViewFilterWrapper> perspectiveFilters) {
    return K8sRecommendationFilterDTO.builder()
        .perspectiveFilters(perspectiveFilters)
        .minSaving(0D)
        .limit(GraphQLUtils.DEFAULT_LIMIT)
        .offset(GraphQLUtils.DEFAULT_OFFSET)
        .build();
  }

  private static QLCEViewFilter createViewFilter(String fieldId, QLCEViewFilterOperator operator, String... values) {
    return QLCEViewFilter.builder()
        .field(QLCEViewFieldInput.builder()
                   .fieldId(fieldId)
                   .fieldName(fieldId)
                   .identifierName(fieldId)
                   .identifier(ViewFieldIdentifier.CLUSTER)
                   .build())
        .values(values)
        .operator(operator)
        .build();
  }

  private CEView createCEView(String fieldId, ViewIdOperator operator) {
    return CEView.builder()
        .uuid(PERSPECTIVE_ID)
        .viewRules(singletonList(
            ViewRule.builder().viewConditions(ImmutableList.of(createViewCondition(fieldId, operator))).build()))
        .build();
  }

  private static ViewCondition createViewCondition(String fieldId, ViewIdOperator operator) {
    return ViewIdCondition.builder()
        .viewField(ViewField.builder()
                       .fieldId(fieldId)
                       .fieldName(fieldId)
                       .identifierName(fieldId)
                       .identifier(ViewFieldIdentifier.CLUSTER)
                       .build())
        .viewOperator(operator)
        .values(singletonList(NAME))
        .build();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testRecommendationFilterStats() {
    String columnName = "resourceType";
    List<String> columns = singletonList(columnName);
    List<FilterStatsDTO> actualResponse =
        ImmutableList.of(FilterStatsDTO.builder()
                             .key(columnName)
                             .values(ImmutableList.of(ResourceType.WORKLOAD.name(), ResourceType.NODE_POOL.name()))
                             .build());

    when(recommendationService.getFilterStats(eq(ACCOUNT_ID), any(), eq(columns), eq(CE_RECOMMENDATIONS)))
        .thenReturn(actualResponse);

    List<FilterStatsDTO> result =
        overviewQuery.recommendationFilterStats(columns, K8sRecommendationFilterDTO.builder().build(), null);

    verify(recommendationService, times(1)).getFilterStats(any(), conditionCaptor.capture(), any(), any());

    assertThat(result).isNotNull().hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo(columnName);
    assertThat(result.get(0).getValues())
        .containsExactlyInAnyOrder(ResourceType.WORKLOAD.name(), ResourceType.NODE_POOL.name());

    assertCommonCondition(conditionCaptor.getValue());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testRecommendationFilterStatsWithPreselectedFilters() {
    String columnName = "resourceType";
    List<String> columns = singletonList(columnName);
    List<FilterStatsDTO> actualResponse = ImmutableList.of(
        FilterStatsDTO.builder().key(columnName).values(ImmutableList.of(ResourceType.WORKLOAD.name())).build());

    when(recommendationService.getFilterStats(eq(ACCOUNT_ID), any(), eq(columns), eq(CE_RECOMMENDATIONS)))
        .thenReturn(actualResponse);

    K8sRecommendationFilterDTO filter = K8sRecommendationFilterDTO.builder()
                                            .names(singletonList("name0"))
                                            .namespaces(singletonList("namespace0"))
                                            .clusterNames(singletonList("clusterName0"))
                                            .resourceTypes(singletonList(ResourceType.WORKLOAD))
                                            .minCost(200D)
                                            .minSaving(100D)
                                            .build();

    List<FilterStatsDTO> result = overviewQuery.recommendationFilterStats(columns, filter, null);

    verify(recommendationService, times(1)).getFilterStats(any(), conditionCaptor.capture(), any(), any());

    assertThat(result).isNotNull().hasSize(1);
    assertThat(result.get(0).getKey()).isEqualTo(columnName);
    assertThat(result.get(0).getValues()).containsExactly(ResourceType.WORKLOAD.name());

    Condition condition = conditionCaptor.getValue();
    assertCommonCondition(condition);

    assertThat(condition.toString())
        .contains(CE_RECOMMENDATIONS.NAME.getQualifiedName().toString())
        .contains("name0")
        .contains(CE_RECOMMENDATIONS.NAMESPACE.getQualifiedName().toString())
        .contains("namespace0")
        .contains(CE_RECOMMENDATIONS.CLUSTERNAME.getQualifiedName().toString())
        .contains("clusterName0")
        .contains(CE_RECOMMENDATIONS.RESOURCETYPE.getQualifiedName().toString())
        .contains(ResourceType.WORKLOAD.name())
        .contains(CE_RECOMMENDATIONS.MONTHLYCOST.getQualifiedName().toString())
        .contains("100")
        .contains(CE_RECOMMENDATIONS.MONTHLYSAVING.getQualifiedName().toString())
        .contains("200");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testRecommendationStats() {
    when(recommendationService.getStats(eq(ACCOUNT_ID), any()))
        .thenReturn(RecommendationOverviewStats.builder().totalMonthlyCost(100D).totalMonthlySaving(100D).build());

    RecommendationOverviewStats stats =
        overviewQuery.recommendationStats(K8sRecommendationFilterDTO.builder().build(), null);

    verify(recommendationService, times(1)).getStats(any(), conditionCaptor.capture());

    assertThat(stats).isNotNull();
    assertThat(stats.getTotalMonthlyCost()).isCloseTo(100D, offset(0.5D));
    assertThat(stats.getTotalMonthlySaving()).isCloseTo(100D, offset(0.5D));

    assertCommonCondition(conditionCaptor.getValue());
  }

  private void assertCommonCondition(Condition condition) {
    assertThat(condition).isNotNull();
    assertThat(condition.toString())
        .contains(CE_RECOMMENDATIONS.ISVALID.getQualifiedName().toString())
        .contains("true")
        .contains(CE_RECOMMENDATIONS.LASTPROCESSEDAT.getQualifiedName().toString());
  }

  private void assertRecommendationOverviewListResponse(final RecommendationsDTO recommendationsDTO) {
    assertThat(recommendationsDTO).isNotNull();
    assertThat(recommendationsDTO.getLimit()).isEqualTo(GraphQLUtils.DEFAULT_LIMIT);
    assertThat(recommendationsDTO.getOffset()).isEqualTo(GraphQLUtils.DEFAULT_OFFSET);
    assertThat(recommendationsDTO.getItems()).isNotNull();
  }

  private static RecommendationItemDTO createRecommendationItem(String id) {
    return createRecommendationItem(id, ResourceType.WORKLOAD);
  }

  private static RecommendationItemDTO createRecommendationItem(String id, ResourceType resourceType) {
    return RecommendationItemDTO.builder()
        .id(id)
        .clusterName(CLUSTER_NAME)
        .monthlyCost(MONTHLY_COST)
        .monthlySaving(MONTHLY_SAVING)
        .resourceName(NAME)
        .recommendationDetails(null)
        .resourceType(resourceType)
        .build();
  }
}
