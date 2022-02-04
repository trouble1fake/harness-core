/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse.RiskCount;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTarget;
import io.harness.cvng.servicelevelobjective.beans.SLOTarget.SLOTargetKeys;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.ServiceLevelObjectiveKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class ServiceLevelObjectiveServiceImpl implements ServiceLevelObjectiveService {
  @Inject private HPersistence hPersistence;

  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private Clock clock;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private Map<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMap;
  @Inject private SLIRecordService sliRecordService;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;

  @Override
  public ServiceLevelObjectiveResponse create(
      ProjectParams projectParams, ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    validateCreate(serviceLevelObjectiveDTO, projectParams);
    ServiceLevelObjective serviceLevelObjective =
        saveServiceLevelObjectiveEntity(projectParams, serviceLevelObjectiveDTO);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    return getSLOResponse(serviceLevelObjectiveDTO.getIdentifier(), projectParams);
  }

  @Override
  public ServiceLevelObjectiveResponse update(
      ProjectParams projectParams, String identifier, ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    Preconditions.checkArgument(identifier.equals(serviceLevelObjectiveDTO.getIdentifier()),
        String.format("Identifier %s does not match with path identifier %s", serviceLevelObjectiveDTO.getIdentifier(),
            identifier));
    ServiceLevelObjective serviceLevelObjective = getEntity(projectParams, serviceLevelObjectiveDTO.getIdentifier());
    if (serviceLevelObjective == null) {
      throw new InvalidRequestException(String.format(
          "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          serviceLevelObjectiveDTO.getIdentifier(), projectParams.getAccountIdentifier(),
          projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
    }
    validate(serviceLevelObjectiveDTO, projectParams);
    serviceLevelObjective = updateSLOEntity(projectParams, serviceLevelObjective, serviceLevelObjectiveDTO);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    sloErrorBudgetResetService.clearErrorBudgetResets(projectParams, identifier);
    return getSLOResponse(serviceLevelObjectiveDTO.getIdentifier(), projectParams);
  }

  @Override
  public boolean delete(ProjectParams projectParams, String identifier) {
    ServiceLevelObjective serviceLevelObjective = getEntity(projectParams, identifier);
    if (serviceLevelObjective == null) {
      throw new InvalidRequestException(String.format(
          "SLO  with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s  is not present",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    serviceLevelIndicatorService.deleteByIdentifier(projectParams, serviceLevelObjective.getServiceLevelIndicators());
    sloErrorBudgetResetService.clearErrorBudgetResets(projectParams, identifier);
    sloHealthIndicatorService.delete(projectParams, serviceLevelObjective.getIdentifier());
    return hPersistence.delete(serviceLevelObjective);
  }

  @Override
  public PageResponse<ServiceLevelObjectiveResponse> get(ProjectParams projectParams, Integer offset, Integer pageSize,
      ServiceLevelObjectiveFilter serviceLevelObjectiveFilter) {
    return get(projectParams, offset, pageSize,
        Filter.builder()
            .userJourneys(serviceLevelObjectiveFilter.getUserJourneys())
            .identifiers(serviceLevelObjectiveFilter.getIdentifiers())
            .targetTypes(serviceLevelObjectiveFilter.getTargetTypes())
            .sliTypes(serviceLevelObjectiveFilter.getSliTypes())
            .errorBudgetRisks(serviceLevelObjectiveFilter.getErrorBudgetRisks())
            .build());
  }

  @Override
  public List<ServiceLevelObjective> getByMonitoredServiceIdentifier(
      ProjectParams projectParams, String monitoredServiceIdentifier) {
    return get(projectParams, Filter.builder().monitoredServiceIdentifier(monitoredServiceIdentifier).build());
  }

  @Override
  public SLORiskCountResponse getRiskCount(ProjectParams projectParams, SLODashboardApiFilter sloDashboardApiFilter) {
    List<ServiceLevelObjective> serviceLevelObjectiveList = get(projectParams,
        Filter.builder()
            .userJourneys(sloDashboardApiFilter.getUserJourneyIdentifiers())
            .monitoredServiceIdentifier(sloDashboardApiFilter.getMonitoredServiceIdentifier())
            .targetTypes(sloDashboardApiFilter.getTargetTypes())
            .sliTypes(sloDashboardApiFilter.getSliTypes())
            .build());
    List<SLOHealthIndicator> sloHealthIndicators = sloHealthIndicatorService.getBySLOIdentifiers(projectParams,
        serviceLevelObjectiveList.stream()
            .map(serviceLevelObjective -> serviceLevelObjective.getIdentifier())
            .collect(Collectors.toList()));

    Map<ErrorBudgetRisk, Long> riskToCountMap =
        sloHealthIndicators.stream()
            .map(sloHealthIndicator -> sloHealthIndicator.getErrorBudgetRisk())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    return SLORiskCountResponse.builder()
        .totalCount(serviceLevelObjectiveList.size())
        .riskCounts(Arrays.stream(ErrorBudgetRisk.values())
                        .map(risk
                            -> RiskCount.builder()
                                   .errorBudgetRisk(risk)
                                   .count(riskToCountMap.getOrDefault(risk, 0L).intValue())
                                   .build())
                        .collect(Collectors.toList()))
        .build();
  }

  private PageResponse<ServiceLevelObjectiveResponse> get(
      ProjectParams projectParams, Integer offset, Integer pageSize, Filter filter) {
    List<ServiceLevelObjective> serviceLevelObjectiveList = get(projectParams, filter);
    PageResponse<ServiceLevelObjective> sloEntitiesPageResponse =
        PageUtils.offsetAndLimit(serviceLevelObjectiveList, offset, pageSize);
    List<ServiceLevelObjectiveResponse> sloPageResponse =
        sloEntitiesPageResponse.getContent().stream().map(this::sloEntityToSLOResponse).collect(Collectors.toList());

    return PageResponse.<ServiceLevelObjectiveResponse>builder()
        .pageSize(pageSize)
        .pageIndex(offset)
        .totalPages(sloEntitiesPageResponse.getTotalPages())
        .totalItems(sloEntitiesPageResponse.getTotalItems())
        .pageItemCount(sloEntitiesPageResponse.getPageItemCount())
        .content(sloPageResponse)
        .build();
  }

  private List<ServiceLevelObjective> get(ProjectParams projectParams, Filter filter) {
    Query<ServiceLevelObjective> sloQuery =
        hPersistence.createQuery(ServiceLevelObjective.class)
            .disableValidation()
            .filter(ServiceLevelObjectiveKeys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceLevelObjectiveKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceLevelObjectiveKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .order(Sort.descending(ServiceLevelObjectiveKeys.lastUpdatedAt));
    if (isNotEmpty(filter.getUserJourneys())) {
      sloQuery.field(ServiceLevelObjectiveKeys.userJourneyIdentifier).in(filter.getUserJourneys());
    }
    if (isNotEmpty(filter.getIdentifiers())) {
      sloQuery.field(ServiceLevelObjectiveKeys.identifier).in(filter.getIdentifiers());
    }
    if (isNotEmpty(filter.getSliTypes())) {
      sloQuery.field(ServiceLevelObjectiveKeys.type).in(filter.getSliTypes());
    }
    if (isNotEmpty(filter.getTargetTypes())) {
      sloQuery.field(ServiceLevelObjectiveKeys.sloTarget + "." + SLOTargetKeys.type).in(filter.getTargetTypes());
    }
    if (filter.getMonitoredServiceIdentifier() != null) {
      sloQuery.filter(ServiceLevelObjectiveKeys.monitoredServiceIdentifier, filter.monitoredServiceIdentifier);
    }
    List<ServiceLevelObjective> serviceLevelObjectiveList = sloQuery.asList();
    if (isNotEmpty(filter.getErrorBudgetRisks())) {
      List<SLOHealthIndicator> sloHealthIndicators = sloHealthIndicatorService.getBySLOIdentifiers(projectParams,
          serviceLevelObjectiveList.stream()
              .map(serviceLevelObjective -> serviceLevelObjective.getIdentifier())
              .collect(Collectors.toList()));
      Map<String, ErrorBudgetRisk> sloIdToRiskMap =
          sloHealthIndicators.stream().collect(Collectors.toMap(sloHealthIndicator
              -> sloHealthIndicator.getServiceLevelObjectiveIdentifier(),
              sloHealthIndicator -> sloHealthIndicator.getErrorBudgetRisk(), (slo1, slo2) -> slo1));
      serviceLevelObjectiveList =
          serviceLevelObjectiveList.stream()
              .filter(slo -> sloIdToRiskMap.containsKey(slo.getIdentifier()))
              .filter(slo -> filter.getErrorBudgetRisks().contains(sloIdToRiskMap.get(slo.getIdentifier())))
              .collect(Collectors.toList());
    }
    return serviceLevelObjectiveList;
  }

  @Override
  public ServiceLevelObjectiveResponse get(ProjectParams projectParams, String identifier) {
    ServiceLevelObjective serviceLevelObjective = getEntity(projectParams, identifier);
    if (Objects.isNull(serviceLevelObjective)) {
      return null;
    }
    return sloEntityToSLOResponse(serviceLevelObjective);
  }

  @Override
  public PageResponse<ServiceLevelObjectiveResponse> getSLOForDashboard(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams) {
    return get(projectParams, pageParams.getPage(), pageParams.getSize(),
        Filter.builder()
            .monitoredServiceIdentifier(filter.getMonitoredServiceIdentifier())
            .userJourneys(filter.getUserJourneyIdentifiers())
            .sliTypes(filter.getSliTypes())
            .errorBudgetRisks(filter.getErrorBudgetRisks())
            .targetTypes(filter.getTargetTypes())
            .build());
  }

  @Override
  public ServiceLevelObjective getFromSLIIdentifier(
      ProjectParams projectParams, String serviceLevelIndicatorIdentifier) {
    return hPersistence.createQuery(ServiceLevelObjective.class)
        .filter(ServiceLevelObjectiveKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelObjectiveKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelObjectiveKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ServiceLevelObjectiveKeys.serviceLevelIndicators, serviceLevelIndicatorIdentifier)
        .get();
  }

  @Override
  public ServiceLevelObjective getEntity(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(ServiceLevelObjective.class)
        .filter(ServiceLevelObjectiveKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelObjectiveKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelObjectiveKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ServiceLevelObjectiveKeys.identifier, identifier)
        .get();
  }

  @Override
  public Map<ServiceLevelObjective, SLOGraphData> getSLOGraphData(
      List<ServiceLevelObjective> serviceLevelObjectiveList) {
    Map<ServiceLevelObjective, SLOGraphData> serviceLevelObjectiveSLOGraphDataMap = new HashMap<>();
    for (ServiceLevelObjective serviceLevelObjective : serviceLevelObjectiveList) {
      Preconditions.checkState(serviceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      ProjectParams projectParams = ProjectParams.builder()
                                        .accountIdentifier(serviceLevelObjective.getAccountId())
                                        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                        .build();
      ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
          projectParams, serviceLevelObjective.getServiceLevelIndicators().get(0));

      LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
      int totalErrorBudgetMinutes = serviceLevelObjective.getActiveErrorBudgetMinutes(
          CollectionUtils
              .emptyIfNull(
                  sloErrorBudgetResetService.getErrorBudgetResets(projectParams, serviceLevelObjective.getIdentifier()))
              .stream()
              .map(SLOErrorBudgetResetDTO::getErrorBudgetIncrementPercentage)
              .collect(Collectors.toList()),
          currentLocalDate);
      ServiceLevelObjective.TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
      Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());

      SLOGraphData sloGraphData = sliRecordService.getGraphData(serviceLevelIndicator.getUuid(),
          timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()), currentTimeMinute, totalErrorBudgetMinutes,
          serviceLevelIndicator.getSliMissingDataType(), serviceLevelIndicator.getVersion());
      serviceLevelObjectiveSLOGraphDataMap.put(serviceLevelObjective, sloGraphData);
    }
    return serviceLevelObjectiveSLOGraphDataMap;
  }

  @Override
  public List<SLOErrorBudgetResetDTO> getErrorBudgetResetHistory(ProjectParams projectParams, String sloIdentifier) {
    return sloErrorBudgetResetService.getErrorBudgetResets(projectParams, sloIdentifier);
  }

  @Override
  public SLOErrorBudgetResetDTO resetErrorBudget(ProjectParams projectParams, SLOErrorBudgetResetDTO resetDTO) {
    return sloErrorBudgetResetService.resetErrorBudget(projectParams, resetDTO);
  }

  private ServiceLevelObjective updateSLOEntity(ProjectParams projectParams,
      ServiceLevelObjective serviceLevelObjective, ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    prePersistenceCleanup(serviceLevelObjectiveDTO);
    UpdateOperations<ServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(ServiceLevelObjective.class);
    updateOperations.set(ServiceLevelObjectiveKeys.name, serviceLevelObjectiveDTO.getName());
    if (serviceLevelObjectiveDTO.getDescription() != null) {
      updateOperations.set(ServiceLevelObjectiveKeys.desc, serviceLevelObjectiveDTO.getDescription());
    }
    updateOperations.set(ServiceLevelObjectiveKeys.tags, TagMapper.convertToList(serviceLevelObjectiveDTO.getTags()));
    updateOperations.set(ServiceLevelObjectiveKeys.userJourneyIdentifier, serviceLevelObjectiveDTO.getUserJourneyRef());
    updateOperations.set(
        ServiceLevelObjectiveKeys.monitoredServiceIdentifier, serviceLevelObjectiveDTO.getMonitoredServiceRef());
    updateOperations.set(
        ServiceLevelObjectiveKeys.healthSourceIdentifier, serviceLevelObjectiveDTO.getHealthSourceRef());
    TimePeriod timePeriod = sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveDTO.getTarget().getType())
                                .getSLOTarget(serviceLevelObjectiveDTO.getTarget().getSpec())
                                .getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    updateOperations.set(ServiceLevelObjectiveKeys.serviceLevelIndicators,
        serviceLevelIndicatorService.update(projectParams, serviceLevelObjectiveDTO.getServiceLevelIndicators(),
            serviceLevelObjectiveDTO.getIdentifier(), serviceLevelObjective.getServiceLevelIndicators(),
            serviceLevelObjective.getMonitoredServiceIdentifier(), serviceLevelObjective.getHealthSourceIdentifier(),
            timePeriod));
    updateOperations.set(ServiceLevelObjectiveKeys.sloTarget,
        sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveDTO.getTarget().getType())
            .getSLOTarget(serviceLevelObjectiveDTO.getTarget().getSpec()));
    updateOperations.set(
        ServiceLevelObjectiveKeys.sloTargetPercentage, serviceLevelObjectiveDTO.getTarget().getSloTargetPercentage());
    hPersistence.update(serviceLevelObjective, updateOperations);
    return serviceLevelObjective;
  }

  private ServiceLevelObjectiveResponse getSLOResponse(String identifier, ProjectParams projectParams) {
    ServiceLevelObjective serviceLevelObjective = getEntity(projectParams, identifier);

    return sloEntityToSLOResponse(serviceLevelObjective);
  }

  private ServiceLevelObjectiveResponse sloEntityToSLOResponse(ServiceLevelObjective serviceLevelObjective) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(serviceLevelObjective.getAccountId())
                                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                      .build();
    ServiceLevelObjectiveDTO serviceLevelObjectiveDTO =
        ServiceLevelObjectiveDTO.builder()
            .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
            .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
            .identifier(serviceLevelObjective.getIdentifier())
            .name(serviceLevelObjective.getName())
            .description(serviceLevelObjective.getDesc())
            .monitoredServiceRef(serviceLevelObjective.getMonitoredServiceIdentifier())
            .healthSourceRef(serviceLevelObjective.getHealthSourceIdentifier())
            .serviceLevelIndicators(
                serviceLevelIndicatorService.get(projectParams, serviceLevelObjective.getServiceLevelIndicators()))
            .target(SLOTarget.builder()
                        .type(serviceLevelObjective.getSloTarget().getType())
                        .spec(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjective.getSloTarget().getType())
                                  .getSLOTargetSpec(serviceLevelObjective.getSloTarget()))
                        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
                        .build())
            .tags(TagMapper.convertToMap(serviceLevelObjective.getTags()))
            .userJourneyRef(serviceLevelObjective.getUserJourneyIdentifier())
            .build();
    return ServiceLevelObjectiveResponse.builder()
        .serviceLevelObjectiveDTO(serviceLevelObjectiveDTO)
        .createdAt(serviceLevelObjective.getCreatedAt())
        .lastModifiedAt(serviceLevelObjective.getLastUpdatedAt())
        .build();
  }

  private ServiceLevelObjective saveServiceLevelObjectiveEntity(
      ProjectParams projectParams, ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    prePersistenceCleanup(serviceLevelObjectiveDTO);
    ServiceLevelObjective serviceLevelObjective =
        ServiceLevelObjective.builder()
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(projectParams.getOrgIdentifier())
            .projectIdentifier(projectParams.getProjectIdentifier())
            .identifier(serviceLevelObjectiveDTO.getIdentifier())
            .name(serviceLevelObjectiveDTO.getName())
            .desc(serviceLevelObjectiveDTO.getDescription())
            .type(serviceLevelObjectiveDTO.getType())
            .serviceLevelIndicators(serviceLevelIndicatorService.create(projectParams,
                serviceLevelObjectiveDTO.getServiceLevelIndicators(), serviceLevelObjectiveDTO.getIdentifier(),
                serviceLevelObjectiveDTO.getMonitoredServiceRef(), serviceLevelObjectiveDTO.getHealthSourceRef()))
            .monitoredServiceIdentifier(serviceLevelObjectiveDTO.getMonitoredServiceRef())
            .healthSourceIdentifier(serviceLevelObjectiveDTO.getHealthSourceRef())
            .tags(TagMapper.convertToList(serviceLevelObjectiveDTO.getTags()))
            .sloTarget(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveDTO.getTarget().getType())
                           .getSLOTarget(serviceLevelObjectiveDTO.getTarget().getSpec()))
            .sloTargetPercentage(serviceLevelObjectiveDTO.getTarget().getSloTargetPercentage())
            .userJourneyIdentifier(serviceLevelObjectiveDTO.getUserJourneyRef())
            .build();
    hPersistence.save(serviceLevelObjective);
    return serviceLevelObjective;
  }

  private void prePersistenceCleanup(ServiceLevelObjectiveDTO sloCreateDTO) {
    SLOTarget sloTarget = sloCreateDTO.getTarget();
    if (Objects.isNull(sloTarget.getType())) {
      sloTarget.setType(sloTarget.getSpec().getType());
    }
    for (ServiceLevelIndicatorDTO serviceLevelIndicator : sloCreateDTO.getServiceLevelIndicators()) {
      ServiceLevelIndicatorSpec serviceLevelIndicatorSpec = serviceLevelIndicator.getSpec();
      if (Objects.isNull(serviceLevelIndicatorSpec.getType())) {
        serviceLevelIndicatorSpec.setType(serviceLevelIndicatorSpec.getSpec().getType());
      }
    }
  }

  public void validateCreate(ServiceLevelObjectiveDTO sloCreateDTO, ProjectParams projectParams) {
    ServiceLevelObjective serviceLevelObjective = getEntity(projectParams, sloCreateDTO.getIdentifier());
    if (serviceLevelObjective != null) {
      throw new DuplicateFieldException(String.format(
          "serviceLevelObjective with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
          sloCreateDTO.getIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
    }
    validate(sloCreateDTO, projectParams);
  }
  private void validate(ServiceLevelObjectiveDTO sloCreateDTO, ProjectParams projectParams) {
    monitoredServiceService.get(projectParams, sloCreateDTO.getMonitoredServiceRef());
  }

  @Value
  @Builder
  private static class Filter {
    List<String> userJourneys;
    List<String> identifiers;
    List<ServiceLevelIndicatorType> sliTypes;
    List<SLOTargetType> targetTypes;
    List<ErrorBudgetRisk> errorBudgetRisks;
    String monitoredServiceIdentifier;
  }
}
