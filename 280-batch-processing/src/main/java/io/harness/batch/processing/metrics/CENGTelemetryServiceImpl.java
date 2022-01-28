package io.harness.batch.processing.metrics;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.RestCallToNGManagerClientUtils.execute;

import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.beans.recommendation.RecommendationTelemetryStats;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.views.dao.CEReportScheduleDao;
import io.harness.ccm.views.dao.CEViewDao;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewType;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.filter.FilterType;
import io.harness.ng.beans.PageResponse;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class CENGTelemetryServiceImpl implements CENGTelemetryService {
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private K8sRecommendationDAO k8sRecommendationDAO;
  @Autowired private CEViewDao ceViewDao;
  @Autowired private BudgetDao budgetDao;
  @Autowired private BudgetUtils budgetUtils;
  @Autowired private CEReportScheduleDao ceReportScheduleDao;

  public HashMap<String, Object> getReportMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    Set<String> uniquePerspectiveIds = new HashSet<>();
    List<CEReportSchedule> ceReportSchedules = ceReportScheduleDao.getAllByAccount(accountId);
    for (CEReportSchedule reportSchedule : ceReportSchedules) {
      uniquePerspectiveIds.addAll(Arrays.asList(reportSchedule.getViewsId()));
    }
    properties.put("ccm_total_report_schedules_count", ceReportSchedules.size());
    properties.put("ccm_perspective_count_with_report_enabled", uniquePerspectiveIds.size());
    return properties;
  }

  public HashMap<String, Object> getBudgetMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    List<Budget> budgets = budgetDao.list(accountId);
    Set<String> uniquePerspectiveIds = new HashSet<>();

    for (Budget budget : budgets) {
      String perspectiveIdForBudget = budgetUtils.getPerspectiveIdForBudget(budget);
      if (perspectiveIdForBudget != budgetUtils.UNDEFINED_PERSPECTIVE) {
        uniquePerspectiveIds.add(perspectiveIdForBudget);
      }
    }

    properties.put("ccm_total_budget_count", budgets.size());
    properties.put("ccm_perspective_count_with_budget_enabled", uniquePerspectiveIds.size());

    return properties;
  }

  public HashMap<String, Object> getPerspectivesMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    List<CEView> viewList = ceViewDao.findByAccountId(accountId);
    viewList = viewList.stream()
                   .filter(view -> ImmutableSet.of(ViewType.SAMPLE, ViewType.CUSTOMER).contains(view.getViewType()))
                   .filter(view -> ImmutableSet.of(ViewState.COMPLETED).contains(view.getViewState()))
                   .collect(Collectors.toList());
    int awsPerspectives = 0, gcpPerspectives = 0, azurePerspectives = 0, clusterPerspectives = 0;
    for (CEView view : viewList) {
      for (ViewFieldIdentifier datasource : view.getDataSources()) {
        if (datasource == ViewFieldIdentifier.AWS) {
          awsPerspectives += 1;
        } else if (datasource == ViewFieldIdentifier.GCP) {
          gcpPerspectives += 1;
        } else if (datasource == ViewFieldIdentifier.AZURE) {
          azurePerspectives += 1;
        } else if (datasource == ViewFieldIdentifier.CLUSTER) {
          clusterPerspectives += 1;
        }
      }
    }

    properties.put("ccm_total_perspective_count", viewList.size());
    properties.put("ccm_aws_perspective_count", awsPerspectives);
    properties.put("ccm_gcp_perspective_count", gcpPerspectives);
    properties.put("ccm_azure_perspective_count", azurePerspectives);
    properties.put("ccm_cluster_perspective_count", clusterPerspectives);
    return properties;
  }

  public HashMap<String, Object> getRecommendationMetrics(String accountId) {
    HashMap<String, Object> properties = new HashMap<>();
    List<RecommendationTelemetryStats> recommendationTelemetryStatsList =
        k8sRecommendationDAO.fetchRecommendationsTelemetry(accountId);
    for (RecommendationTelemetryStats telemetryStat : recommendationTelemetryStatsList) {
      String recommendationType = telemetryStat.getType().toLowerCase();
      properties.put("ccm" + recommendationType + "_recommendation_count", telemetryStat.getCount());
      properties.put("ccm" + recommendationType + "_recommendation_spend", telemetryStat.getTotalMonthlyCost());
      properties.put("ccm" + recommendationType + "_recommendation_saving", telemetryStat.getTotalMonthlySaving());
    }
    return properties;
  }

  public HashMap<String, Object> getNextGenConnectorsCountByType(String accountId) {
    List<ConnectorResponseDTO> nextGenConnectors = new ArrayList<>();
    PageResponse<ConnectorResponseDTO> response = null;
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .types(Arrays.asList(ConnectorType.CE_KUBERNETES_CLUSTER, ConnectorType.CE_AWS, ConnectorType.CE_AZURE,
                ConnectorType.GCP_CLOUD_COST, ConnectorType.KUBERNETES_CLUSTER))
            .build();
    connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);
    int page = 0;
    int size = 100;
    do {
      response = getConnectors(accountId, page, size, connectorFilterPropertiesDTO);
      if (response != null && isNotEmpty(response.getContent())) {
        nextGenConnectors.addAll(response.getContent());
      }
      page++;
    } while (response != null && isNotEmpty(response.getContent()));

    HashMap<String, Object> properties = new HashMap<>();

    // Total Count
    properties.put("ccm_aws_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.CE_AWS));
    properties.put("ccm_azure_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.CE_AZURE));
    properties.put("ccm_gcp_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.GCP_CLOUD_COST));
    properties.put(
        "ccm_base_k8s_connector_count", getConnectorCount(nextGenConnectors, ConnectorType.KUBERNETES_CLUSTER));
    properties.put("ccm_cloud_cost_k8s_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_KUBERNETES_CLUSTER));

    // Failed Connector Count
    properties.put("ccm_aws_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_AWS, ConnectivityStatus.FAILURE));
    properties.put("ccm_azure_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_AZURE, ConnectivityStatus.FAILURE));
    properties.put("ccm_gcp_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.GCP_CLOUD_COST, ConnectivityStatus.FAILURE));
    properties.put("ccm_base_k8s_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.KUBERNETES_CLUSTER, ConnectivityStatus.FAILURE));
    properties.put("ccm_cloud_cost_k8s_failed_connector_count",
        getConnectorCount(nextGenConnectors, ConnectorType.CE_KUBERNETES_CLUSTER, ConnectivityStatus.FAILURE));
    return properties;
  }

  private long getConnectorCount(
      List<ConnectorResponseDTO> nextGenConnectors, ConnectorType connectorType, ConnectivityStatus status) {
    return nextGenConnectors.stream()
        .filter(connector
            -> connector.getConnector().getConnectorType() == connectorType
                && connector.getStatus().getStatus() == status)
        .count();
  }

  private long getConnectorCount(List<ConnectorResponseDTO> nextGenConnectors, ConnectorType connectorType) {
    return nextGenConnectors.stream()
        .filter(connector -> connector.getConnector().getConnectorType() == connectorType)
        .count();
  }

  private PageResponse getConnectors(
      String accountId, int page, int size, ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO) {
    return execute(
        connectorResourceClient.listConnectors(accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
  }
}
