package io.harness.batch.processing.metrics;

import java.util.HashMap;

public interface CENGTelemetryService {
  HashMap<String, Object> getNextGenConnectorsCountByType(String accountId);
  HashMap<String, Object> getRecommendationMetrics(String accountId);
  HashMap<String, Object> getPerspectivesMetrics(String accountId);
  HashMap<String, Object> getReportMetrics(String accountId);
  HashMap<String, Object> getBudgetMetrics(String accountId);
}
