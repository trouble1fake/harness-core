/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.data.structure.CollectionUtils;
import io.harness.ng.beans.PageResponse;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class SLODashboardServiceImpl implements SLODashboardService {
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SLIRecordService sliRecordService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private Clock clock;
  @Inject private NextGenService nextGenService;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;

  @Override
  public PageResponse<SLODashboardWidget> getSloDashboardWidgets(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams) {
    PageResponse<ServiceLevelObjectiveResponse> sloPageResponse =
        serviceLevelObjectiveService.getSLOForDashboard(projectParams, filter, pageParams);
    Set<String> monitoredServiceIdentifiers =
        sloPageResponse.getContent()
            .stream()
            .map(slo -> slo.getServiceLevelObjectiveDTO().getMonitoredServiceRef())
            .collect(Collectors.toSet());
    Map<String, MonitoredServiceDTO> identifierToMonitoredServiceMap =
        getIdentifierToMonitoredServiceDTOMap(projectParams, monitoredServiceIdentifiers);
    Map<String, List<SLOErrorBudgetResetDTO>> errorBudgetResetDTOMap =
        sloErrorBudgetResetService.getErrorBudgetResets(projectParams,
            sloPageResponse.getContent()
                .stream()
                .map(slo -> slo.getServiceLevelObjectiveDTO().getIdentifier())
                .collect(Collectors.toSet()));
    List<SLODashboardWidget> sloDashboardWidgets =
        sloPageResponse.getContent()
            .stream()
            .map(sloResponse
                -> getSloDashboardWidget(projectParams, identifierToMonitoredServiceMap, sloResponse,
                    errorBudgetResetDTOMap.get(sloResponse.getServiceLevelObjectiveDTO().getIdentifier())))
            .collect(Collectors.toList());
    return PageResponse.<SLODashboardWidget>builder()
        .pageSize(sloPageResponse.getPageSize())
        .pageIndex(sloPageResponse.getPageIndex())
        .totalPages(sloPageResponse.getTotalPages())
        .totalItems(sloPageResponse.getTotalItems())
        .pageItemCount(sloPageResponse.getPageItemCount())
        .content(sloDashboardWidgets)
        .build();
  }

  @Override
  public SLORiskCountResponse getRiskCount(
      ProjectParams projectParams, SLODashboardApiFilter serviceLevelObjectiveFilter) {
    return serviceLevelObjectiveService.getRiskCount(projectParams, serviceLevelObjectiveFilter);
  }

  private SLODashboardWidget getSloDashboardWidget(ProjectParams projectParams,
      Map<String, MonitoredServiceDTO> identifierToMonitoredServiceMap, ServiceLevelObjectiveResponse sloResponse,
      List<SLOErrorBudgetResetDTO> errorBudgetResetDTOS) {
    Preconditions.checkState(sloResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().size() == 1,
        "Only one service level indicator is supported");
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        sloResponse.getServiceLevelObjectiveDTO().getServiceLevelIndicators().get(0);
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());

    ServiceLevelObjectiveDTO slo = sloResponse.getServiceLevelObjectiveDTO();
    ServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveService.getEntity(projectParams, slo.getIdentifier());
    MonitoredServiceDTO monitoredService = identifierToMonitoredServiceMap.get(slo.getMonitoredServiceRef());
    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
    TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
    Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    int totalErrorBudgetMinutes = serviceLevelObjective.getActiveErrorBudgetMinutes(
        CollectionUtils.emptyIfNull(errorBudgetResetDTOS)
            .stream()
            .sorted((dto1, dto2) -> dto1.getCreatedAt().compareTo(dto2.getCreatedAt()))
            .map(dto -> dto.getErrorBudgetIncrementPercentage())
            .collect(Collectors.toList()),
        currentLocalDate);
    SLODashboardWidget.SLOGraphData sloGraphData = sliRecordService.getGraphData(serviceLevelIndicator.getUuid(),
        timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()), currentTimeMinute, totalErrorBudgetMinutes,
        serviceLevelIndicator.getSliMissingDataType(), serviceLevelIndicator.getVersion());
    return SLODashboardWidget.withGraphData(sloGraphData)
        .sloIdentifier(slo.getIdentifier())
        .title(slo.getName())
        .sloTargetType(slo.getTarget().getType())
        .currentPeriodLengthDays(timePeriod.getTotalDays())
        .currentPeriodStartTime(timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .currentPeriodEndTime(timePeriod.getEndTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .monitoredServiceIdentifier(slo.getMonitoredServiceRef())
        .monitoredServiceName(monitoredService.getName())
        .environmentIdentifier(monitoredService.getEnvironmentRef())
        .environmentName(
            nextGenService
                .getEnvironment(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                    serviceLevelObjective.getProjectIdentifier(), monitoredService.getEnvironmentRef())
                .getName())
        .serviceName(nextGenService
                         .getService(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                             serviceLevelObjective.getProjectIdentifier(), monitoredService.getServiceRef())
                         .getName())
        .serviceIdentifier(monitoredService.getServiceRef())
        .healthSourceIdentifier(slo.getHealthSourceRef())
        .healthSourceName(getHealthSourceName(monitoredService, slo.getHealthSourceRef()))
        .tags(slo.getTags())
        .type(slo.getServiceLevelIndicators().get(0).getType())
        .totalErrorBudget(totalErrorBudgetMinutes)
        .timeRemainingDays(timePeriod.getRemainingDays(currentLocalDate))
        .burnRate(SLODashboardWidget.BurnRate.builder()
                      .currentRatePercentage(sloGraphData.dailyBurnRate(serviceLevelObjective.getZoneOffset()))
                      .build())
        .build();
  }

  @NotNull
  private Map<String, MonitoredServiceDTO> getIdentifierToMonitoredServiceDTOMap(
      ProjectParams projectParams, Set<String> monitoredServiceIdentifiers) {
    List<MonitoredServiceDTO> monitoredServiceDTOS =
        monitoredServiceService.get(projectParams, monitoredServiceIdentifiers)
            .stream()
            .map(monitoredServiceResponse -> monitoredServiceResponse.getMonitoredServiceDTO())
            .collect(Collectors.toList());
    return monitoredServiceDTOS.stream().collect(
        Collectors.toMap(MonitoredServiceDTO::getIdentifier, Function.identity()));
  }

  private String getHealthSourceName(MonitoredServiceDTO monitoredServiceDTO, String healthSourceRef) {
    return monitoredServiceDTO.getSources()
        .getHealthSources()
        .stream()
        .filter(healthSource -> healthSource.getIdentifier().equals(healthSourceRef))
        .findFirst()
        .orElseThrow(()
                         -> new IllegalStateException(
                             "Health source identifier" + healthSourceRef + " not found in monitored service"))
        .getName();
  }
}
