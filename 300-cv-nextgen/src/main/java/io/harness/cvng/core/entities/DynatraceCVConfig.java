package io.harness.cvng.core.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.DynatraceHealthSourceSpec;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.utils.analysisinfo.DevelopmentVerificationTransformer;
import io.harness.cvng.core.utils.analysisinfo.LiveMonitoringTransformer;
import io.harness.cvng.core.utils.analysisinfo.SLIMetricTransformer;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("DYNATRACE")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "DynatraceCVConfigKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DynatraceCVConfig extends MetricCVConfig {
  private String dynatraceServiceName;
  private String dynatraceServiceId;
  private String groupName;
  private List<DynatraceMetricInfo> metricInfos;
  private HealthSourceQueryType queryType;
  private List<String> serviceMethodIds;

  @Override
  protected void validateParams() {
    checkNotNull(dynatraceServiceName, generateErrorMessageFromParam(DynatraceCVConfigKeys.dynatraceServiceName));
    checkNotNull(dynatraceServiceId, generateErrorMessageFromParam(DynatraceCVConfigKeys.dynatraceServiceId));

    if (isEmpty(metricInfos)) {
      // if there are no custom metrics, serviceMethodIds are required
      checkNotNull(serviceMethodIds, generateErrorMessageFromParam(DynatraceCVConfigKeys.serviceMethodIds));
    }
  }

  @Override
  public DataSourceType getType() {
    return DataSourceType.DYNATRACE;
  }

  @Override
  public String getDataCollectionDsl() {
    return getMetricPack().getDataCollectionDsl();
  }

  public void populateFromMetricDefinitions(
      List<DynatraceHealthSourceSpec.DynatraceMetricDefinition> metricDefinitions, CVMonitoringCategory category) {
    MetricPack metricPack = MetricPack.builder()
                                .category(category)
                                .accountId(getAccountId())
                                .dataSourceType(DataSourceType.DYNATRACE)
                                .projectIdentifier(getProjectIdentifier())
                                .orgIdentifier(getOrgIdentifier())
                                .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                .category(category)
                                .build();
    if (this.metricInfos == null) {
      this.metricInfos = new ArrayList<>();
    }

    metricDefinitions.forEach(md -> {
      DynatraceMetricInfo metricInfo =
          DynatraceMetricInfo.builder()
              .identifier(md.getIdentifier())
              .metricName(md.getMetricName())
              .metricSelector(md.getMetricSelector())
              .isManualQuery(md.isManualQuery())
              .sli(SLIMetricTransformer.transformDTOtoEntity(md.getSli()))
              .liveMonitoring(LiveMonitoringTransformer.transformDTOtoEntity(md.getAnalysis()))
              .deploymentVerification(DevelopmentVerificationTransformer.transformDTOtoEntity(md.getAnalysis()))
              .metricType(md.getRiskProfile().getMetricType())
              .build();
      this.metricInfos.add(metricInfo);
      Set<TimeSeriesThreshold> thresholds = getThresholdsToCreateOnSaveForCustomProviders(
          metricInfo.getMetricName(), metricInfo.getMetricType(), md.getRiskProfile().getThresholdTypes());

      metricPack.addToMetrics(MetricPack.MetricDefinition.builder()
                                  .thresholds(new ArrayList<>(thresholds))
                                  .type(metricInfo.getMetricType())
                                  .name(metricInfo.getMetricName())
                                  .included(true)
                                  .build());
    });
    this.setMetricPack(metricPack);
  }

  public static class DynatraceCVConfigUpdatableEntity
      extends MetricCVConfigUpdatableEntity<DynatraceCVConfig, DynatraceCVConfig> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<DynatraceCVConfig> updateOperations, DynatraceCVConfig dynatraceCVConfig) {
      setCommonOperations(updateOperations, dynatraceCVConfig);
      updateOperations.set(DynatraceCVConfigKeys.dynatraceServiceName, dynatraceCVConfig.getDynatraceServiceName())
          .set(DynatraceCVConfigKeys.dynatraceServiceId, dynatraceCVConfig.getDynatraceServiceId());
    }
  }

  @Value
  @SuperBuilder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @EqualsAndHashCode(callSuper = true)
  public static class DynatraceMetricInfo extends AnalysisInfo {
    String metricName;
    TimeSeriesMetricType metricType;
    String metricSelector;
    boolean isManualQuery;
  }
}
