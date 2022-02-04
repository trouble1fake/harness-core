/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.GE;
import static io.harness.beans.SearchFilter.Operator.HAS;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.NO_APPS_ASSIGNED;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Base.CREATED_AT_KEY;
import static software.wings.beans.EntityType.APPLICATION;
import static software.wings.beans.EntityType.ARTIFACT;
import static software.wings.beans.infrastructure.instance.Instance.InstanceKeys;
import static software.wings.features.DeploymentHistoryFeature.FEATURE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.mongodb.morphia.aggregation.Accumulator.accumulator;
import static org.mongodb.morphia.aggregation.Group.first;
import static org.mongodb.morphia.aggregation.Group.grouping;
import static org.mongodb.morphia.aggregation.Group.sum;
import static org.mongodb.morphia.aggregation.Projection.projection;
import static org.mongodb.morphia.query.Sort.ascending;
import static org.mongodb.morphia.query.Sort.descending;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder.OrderType;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NoResultFoundException;
import io.harness.exception.WingsException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.ExceptionLogger;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.time.EpochUtils;

import software.wings.beans.Application;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.SyncStatus;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.instance.dashboard.ArtifactSummary.ArtifactSummaryBuilder;
import software.wings.beans.instance.dashboard.EntitySummary;
import software.wings.beans.instance.dashboard.EntitySummaryStats;
import software.wings.beans.instance.dashboard.EnvironmentSummary;
import software.wings.beans.instance.dashboard.EnvironmentSummary.EnvironmentSummaryBuilder;
import software.wings.beans.instance.dashboard.InstanceStats;
import software.wings.beans.instance.dashboard.InstanceStatsByArtifact;
import software.wings.beans.instance.dashboard.InstanceStatsByEnvironment;
import software.wings.beans.instance.dashboard.InstanceStatsByEnvironment.InstanceStatsByEnvironmentBuilder;
import software.wings.beans.instance.dashboard.InstanceStatsByService;
import software.wings.beans.instance.dashboard.InstanceSummaryStats;
import software.wings.beans.instance.dashboard.InstanceSummaryStatsByService;
import software.wings.beans.instance.dashboard.ServiceSummary;
import software.wings.beans.instance.dashboard.ServiceSummary.ServiceSummaryBuilder;
import software.wings.beans.instance.dashboard.service.CurrentActiveInstances;
import software.wings.beans.instance.dashboard.service.DeploymentHistory;
import software.wings.beans.instance.dashboard.service.ServiceInstanceDashboard;
import software.wings.dl.WingsPersistence;
import software.wings.features.DeploymentHistoryFeature;
import software.wings.features.api.RestrictedFeature;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.instance.CompareEnvironmentAggregationInfo.CompareEnvironmentAggregationInfoKeys;
import software.wings.service.impl.instance.ServiceInfoResponseSummary.ServiceInfoResponseSummaryBuilder;
import software.wings.service.impl.instance.ServiceInfoSummary.ServiceInfoSummaryKeys;
import software.wings.service.impl.instance.ServiceInstanceCount.EnvType;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.instance.DashboardStatisticsService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.sm.PipelineSummary;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.AggregationOptions;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.ReadPreference;
import com.mongodb.TagSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.mongodb.morphia.aggregation.AggregationPipeline;
import org.mongodb.morphia.aggregation.Group;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

/**
 * @author rktummala on 8/13/17
 */
@Singleton
@Slf4j
@OwnedBy(DX)
public class DashboardStatisticsServiceImpl implements DashboardStatisticsService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private InstanceService instanceService;
  @Inject private AppService appService;
  @Inject private UserService userService;
  @Inject private WorkflowService workflowService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private AccountService accountService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private SettingsService settingsService;
  @Inject @Named(FEATURE_NAME) private RestrictedFeature deploymentHistoryFeature;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private TagSet mongoTagSet;

  @Override
  public InstanceSummaryStats getAppInstanceSummaryStats(
      String accountId, List<String> appIds, List<String> groupByEntityTypes, long timestamp) {
    Query<Instance> query;
    try {
      query = getInstanceQueryAtTime(accountId, appIds, timestamp);
    } catch (NoResultFoundException e) {
      return InstanceSummaryStats.Builder.anInstanceSummaryStats().countMap(null).totalCount(0).build();
    } catch (Exception e) {
      log.error("Error while compiling query for getting app instance summary stats");
      return InstanceSummaryStats.Builder.anInstanceSummaryStats().countMap(null).totalCount(0).build();
    }

    long instanceCount = getInstanceCount(query);

    Map<String, List<EntitySummaryStats>> instanceSummaryMap = new HashMap<>();
    for (String groupByEntityType : groupByEntityTypes) {
      String entityIdColumn;
      String entityNameColumn;
      List<EntitySummaryStats> entitySummaryStatsList;
      if (EntityType.SERVICE.name().equals(groupByEntityType)) {
        entityIdColumn = "serviceId";
        entityNameColumn = "serviceName";
        entitySummaryStatsList = getEntitySummaryStats(entityIdColumn, entityNameColumn, groupByEntityType, query);
      } else if (EntityType.ENVIRONMENT.name().equals(groupByEntityType)) {
        // TODO: Make UI pass ENVIRONMENT_TYPE instead of ENVIRONMENT since that's what are we are really displaying
        entitySummaryStatsList = getEnvironmentTypeSummaryStats(query);
      } else if (SettingCategory.CLOUD_PROVIDER.name().equals(groupByEntityType)) {
        entityIdColumn = "computeProviderId";
        entityNameColumn = "computeProviderName";
        entitySummaryStatsList = getEntitySummaryStats(entityIdColumn, entityNameColumn, groupByEntityType, query);
      } else {
        throw new InvalidArgumentsException("Unsupported groupBy entity type:" + groupByEntityType, USER);
      }

      instanceSummaryMap.put(groupByEntityType, entitySummaryStatsList);
    }

    return InstanceSummaryStats.Builder.anInstanceSummaryStats()
        .countMap(instanceSummaryMap)
        .totalCount(instanceCount)
        .build();
  }

  @Override
  public long getTotalInstancesForAccount(String accountId, List<String> appIds) {
    Query<Instance> query;
    try {
      query = getInstanceQuery(accountId, appIds, false, -1);
      return getInstanceCount(query);
    } catch (Exception e) {
      log.error("Error while getting total instances for accountId:[{}]", accountId, e);
      return -1;
    }
  }

  private List<EntitySummaryStats> getEntitySummaryStats(
      String entityIdColumn, String entityNameColumn, String groupByEntityType, Query<Instance> query) {
    List<EntitySummaryStats> entitySummaryStatsList = new ArrayList<>();
    wingsPersistence.getDatastore(query.getEntityClass())
        .createAggregation(Instance.class)
        .match(query)
        .group(Group.id(grouping(entityIdColumn)), grouping("count", accumulator("$sum", 1)),
            grouping(entityNameColumn, grouping("$first", entityNameColumn)))
        .project(projection("_id").suppress(), projection("entityId", "_id." + entityIdColumn),
            projection("entityName", entityNameColumn), projection("count"))
        .sort(descending("count"))
        .aggregate(FlatEntitySummaryStats.class)
        .forEachRemaining(flatEntitySummaryStats -> {
          EntitySummaryStats entitySummaryStats = getEntitySummaryStats(flatEntitySummaryStats, groupByEntityType);
          entitySummaryStatsList.add(entitySummaryStats);
        });
    return entitySummaryStatsList;
  }

  private long getInstanceCount(Query<Instance> query) {
    AtomicLong totalCount = new AtomicLong();
    wingsPersistence.getDatastore(query.getEntityClass())
        .createAggregation(Instance.class)
        .match(query)
        .group("_id", grouping("count", accumulator("$sum", 1)))
        .aggregate(InstanceCount.class)
        .forEachRemaining(instanceCount -> totalCount.addAndGet(instanceCount.getCount()));
    return totalCount.get();
  }

  private List<EntitySummaryStats> getEnvironmentTypeSummaryStats(Query<Instance> query) {
    List<EntitySummaryStats> entitySummaryStatsList = Lists.newArrayList();
    wingsPersistence.getDatastore(query.getEntityClass())
        .createAggregation(Instance.class)
        .match(query)
        .group(Group.id(grouping("envType")), grouping("count", accumulator("$sum", 1)))
        .project(projection("_id").suppress(), projection("envType", "_id.envType"), projection("count"))
        .sort(ascending("_id.envType"))
        .aggregate(EnvironmentSummaryStats.class)
        .forEachRemaining(environmentSummaryStats -> {
          String envType = environmentSummaryStats.getEnvType();
          EntitySummary entitySummary =
              EntitySummary.builder().name(envType).type(EntityType.ENVIRONMENT.name()).id(envType).build();
          EntitySummaryStats entitySummaryStats = EntitySummaryStats.Builder.anEntitySummaryStats()
                                                      .entitySummary(entitySummary)
                                                      .count(environmentSummaryStats.getCount())
                                                      .build();
          entitySummaryStatsList.add(entitySummaryStats);
        });

    return entitySummaryStatsList;
  }

  private EntitySummaryStats getEntitySummaryStats(FlatEntitySummaryStats flatEntitySummaryStats, String entityType) {
    EntitySummary entitySummary = EntitySummary.builder()
                                      .id(flatEntitySummaryStats.getEntityId())
                                      .name(flatEntitySummaryStats.getEntityName())
                                      .type(entityType)
                                      .build();
    return EntitySummaryStats.Builder.anEntitySummaryStats()
        .count(flatEntitySummaryStats.getCount())
        .entitySummary(entitySummary)
        .build();
  }

  @Override
  public InstanceSummaryStats getServiceInstanceSummaryStats(
      String accountId, String serviceId, List<String> groupByEntityTypes, long timestamp) {
    Query<Instance> query;
    List<String> appIds = null;
    try {
      query = getInstanceQueryAtTime(accountId, appIds, timestamp);
    } catch (NoResultFoundException e) {
      return InstanceSummaryStats.Builder.anInstanceSummaryStats().countMap(null).totalCount(0).build();
    } catch (Exception e) {
      log.error("Error while compiling query for getting app instance summary stats");
      return InstanceSummaryStats.Builder.anInstanceSummaryStats().countMap(null).totalCount(0).build();
    }

    query.filter("serviceId", serviceId);

    long instanceCount = getInstanceCount(query);
    Map<String, List<EntitySummaryStats>> instanceSummaryMap = new HashMap<>();
    for (String groupByEntityType : groupByEntityTypes) {
      String entityIdColumn;
      String entityNameColumn;
      List<EntitySummaryStats> entitySummaryStatsList;
      if (ARTIFACT.name().equals(groupByEntityType)) {
        entityIdColumn = "lastArtifactId";
        entityNameColumn = "lastArtifactBuildNum";
      } else if (EntityType.ENVIRONMENT.name().equals(groupByEntityType)) {
        entityIdColumn = "envId";
        entityNameColumn = "envName";
      } else if ("INFRASTRUCTURE".equals(groupByEntityType)) {
        entityIdColumn = "infraMappingId";
        entityNameColumn = "infraMappingType";
      } else {
        throw new InvalidArgumentsException("Unsupported groupBy entity type:" + groupByEntityType, USER);
      }

      entitySummaryStatsList = getEntitySummaryStats(entityIdColumn, entityNameColumn, groupByEntityType, query);
      instanceSummaryMap.put(groupByEntityType, entitySummaryStatsList);
    }

    return InstanceSummaryStats.Builder.anInstanceSummaryStats()
        .countMap(instanceSummaryMap)
        .totalCount(instanceCount)
        .build();
  }

  private void handleException(Exception exception) {
    if (exception instanceof NoResultFoundException) {
      final NoResultFoundException noResultFoundException = (NoResultFoundException) exception;

      final List<ResponseMessage> responseMessageList =
          ExceptionLogger.getResponseMessageList(noResultFoundException, ReportTarget.LOG_SYSTEM);

      if (isNotEmpty(responseMessageList)) {
        ResponseMessage responseMessage = responseMessageList.get(0);
        if (responseMessage.getCode() != ErrorCode.NO_APPS_ASSIGNED) {
          log.error("Unable to get instance stats", exception);
        }
      }

    } else if (exception instanceof WingsException) {
      ExceptionLogger.logProcessedMessages((WingsException) exception, MANAGER, log);
    } else {
      log.error("Unable to get instance stats", exception);
    }
  }

  @Override
  @Nonnull
  public List<Instance> getAppInstancesForAccount(String accountId, long timestamp) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    return getInstancesForAccount(accountId, timestamp, query);
  }

  private List<Instance> getInstancesForAccount(String accountId, long timestamp, Query<Instance> query) {
    List<Instance> instanceList = new ArrayList<>();
    query.field("accountId").equal(accountId);
    if (timestamp > 0) {
      query.field(Instance.CREATED_AT_KEY).lessThanOrEq(timestamp);
      query.and(
          query.or(query.criteria("isDeleted").equal(false), query.criteria("deletedAt").greaterThanOrEq(timestamp)));
    } else {
      query.filter("isDeleted", false);
    }

    int counter = 0;
    try (HIterator<Instance> iterator = new HIterator<>(query.fetch())) {
      for (Instance instance : iterator) {
        counter++;
        instanceList.add(instance);
      }
    }

    if (isNotEmpty(instanceList)) {
      HashSet<Instance> instanceSet = new HashSet<>(instanceList);
      log.info("Instances reported {}, set count {}", counter, instanceSet.size());
    } else {
      log.info("Instances reported {}", counter);
    }
    return instanceList;
  }

  private long getCreatedTimeOfInstanceAtTimestamp(
      String accountId, long timestamp, Query<Instance> query, boolean oldest) {
    query.field("accountId").equal(accountId);
    query.field(Instance.CREATED_AT_KEY).lessThanOrEq(timestamp);
    query.and(
        query.or(query.criteria("isDeleted").equal(false), query.criteria("deletedAt").greaterThanOrEq(timestamp)));
    if (oldest) {
      query.order(Sort.ascending(CREATED_AT_KEY));
    } else {
      query.order(Sort.descending(CREATED_AT_KEY));
    }

    FindOptions findOptions = new FindOptions();
    findOptions.modifier("$hint", "instance_index7");
    Instance instance = query.get(findOptions);
    if (instance == null) {
      return timestamp;
    }

    return instance.getCreatedAt();
  }

  private PageResponse getEmptyPageResponse() {
    return aPageResponse().withResponse(emptyList()).build();
  }

  @Override
  public List<InstanceStatsByService> getAppInstanceStatsByService(
      String accountId, List<String> appIds, long timestamp) {
    Query<Instance> query;
    try {
      query = getInstanceQueryAtTime(accountId, appIds, timestamp);
    } catch (NoResultFoundException nre) {
      return emptyList();
    } catch (Exception e) {
      log.error("Error while compiling query for instance stats by service");
      return emptyList();
    }

    List<AggregationInfo> instanceInfoList = new ArrayList<>();
    wingsPersistence.getDatastore(query.getEntityClass())
        .createAggregation(Instance.class)
        .match(query)
        .group(Group.id(grouping("serviceId"), grouping("envId"), grouping("lastArtifactId")),
            grouping("count", accumulator("$sum", 1)),
            grouping("appInfo", grouping("$first", projection("id", "appId"), projection("name", "appName"))),
            grouping(
                "serviceInfo", grouping("$first", projection("id", "serviceId"), projection("name", "serviceName"))),
            grouping("envInfo",
                grouping(
                    "$first", projection("id", "envId"), projection("name", "envName"), projection("type", "envType"))),
            grouping("artifactInfo",
                grouping("$first", projection("id", "lastArtifactId"), projection("name", "lastArtifactName"),
                    projection("buildNo", "lastArtifactBuildNum"), projection("streamId", "lastArtifactStreamId"),
                    projection("deployedAt", "lastDeployedAt"), projection("sourceName", "lastArtifactSourceName"))),
            grouping(
                "instanceInfoList", grouping("$addToSet", projection("id", "_id"), projection("name", "hostName"))))
        .sort(ascending("_id.serviceId"), ascending("_id.envId"), descending("count"))
        .aggregate(AggregationInfo.class)
        .forEachRemaining(instanceInfo -> {
          instanceInfoList.add(instanceInfo);
          log.info(instanceInfo.toString());
        });

    return constructInstanceStatsByService(instanceInfoList);
  }

  @Override
  public List<InstanceStatsByEnvironment> getServiceInstances(String accountId, String serviceId, long timestamp) {
    Query<Instance> query;
    try {
      query = getInstanceQueryAtTime(accountId, serviceId, timestamp);
    } catch (Exception e) {
      log.error("Error while compiling query for instance stats by service");
      return emptyList();
    }

    List<ServiceAggregationInfo> serviceAggregationInfoList = new ArrayList<>();
    wingsPersistence.getDatastore(query.getEntityClass())
        .createAggregation(Instance.class)
        .match(query)
        .group(Group.id(grouping("envId"), grouping("lastArtifactId")), grouping("count", accumulator("$sum", 1)),
            grouping("appInfo", grouping("$first", projection("id", "appId"), projection("name", "appName"))),
            grouping("envInfo",
                grouping(
                    "$first", projection("id", "envId"), projection("name", "envName"), projection("type", "envType"))),
            grouping("artifactInfo",
                grouping("$first", projection("id", "lastArtifactId"), projection("name", "lastArtifactName"),
                    projection("buildNo", "lastArtifactBuildNum"), projection("streamId", "lastArtifactStreamId"),
                    projection("deployedAt", "lastDeployedAt"), projection("sourceName", "lastArtifactSourceName"))),
            grouping(
                "instanceInfoList", grouping("$addToSet", projection("id", "_id"), projection("name", "hostName"))))
        .sort(ascending("_id.envId"), descending("count"))
        .aggregate(ServiceAggregationInfo.class)
        .forEachRemaining(serviceAggregationInfoList::add);

    return constructInstanceStatsForService(serviceId, serviceAggregationInfoList);
  }

  @Override
  public PageResponse<InstanceSummaryStatsByService> getAppInstanceSummaryStatsByService(
      String accountId, List<String> appIds, long timestamp, int offset, int limit) {
    Query<Instance> query;
    try {
      query = getInstanceQueryAtTime(accountId, appIds, timestamp);
    } catch (NoResultFoundException nre) {
      return getEmptyPageResponse();
    } catch (Exception e) {
      log.error("Error while compiling query for instance stats by service");
      return getEmptyPageResponse();
    }

    List<ServiceInstanceCount> instanceInfoList = new ArrayList<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(query.getEntityClass())
            .createAggregation(Instance.class)
            .match(query)
            .group(Group.id(grouping("serviceId")), grouping("count", accumulator("$sum", 1)),
                grouping("appInfo", grouping("$first", projection("id", "appId"), projection("name", "appName"))),
                grouping("serviceInfo",
                    grouping("$first", projection("id", "serviceId"), projection("name", "serviceName"))),
                grouping("envTypeList", grouping("$push", projection("type", "envType"))));
    aggregationPipeline.skip(offset);
    aggregationPipeline.limit(limit);

    final Iterator<ServiceInstanceCount> aggregate =
        HPersistence.retry(() -> aggregationPipeline.aggregate(ServiceInstanceCount.class));
    aggregate.forEachRemaining(instanceInfoList::add);
    return constructInstanceSummaryStatsByService(instanceInfoList, offset, limit);
  }

  private PageResponse<InstanceSummaryStatsByService> constructInstanceSummaryStatsByService(
      List<ServiceInstanceCount> serviceInstanceCountList, int offset, int limit) {
    List<InstanceSummaryStatsByService> instanceSummaryStatsByServiceList =
        serviceInstanceCountList.stream().map(this::getInstanceSummaryStatsByService).collect(toList());
    return aPageResponse()
        .withResponse(instanceSummaryStatsByServiceList)
        .withOffset(Integer.toString(offset))
        .withLimit(Integer.toString(limit))
        .build();
  }

  private Query<Instance> getInstanceQueryAtTime(String accountId, String serviceId, long timestamp) {
    Query<Instance> query;
    if (timestamp > 0) {
      query = getInstanceQuery(accountId, serviceId, true);
      query.field(Instance.CREATED_AT_KEY).lessThanOrEq(timestamp);
      query.and(
          query.or(query.criteria("isDeleted").equal(false), query.criteria("deletedAt").greaterThanOrEq(timestamp)));
    } else {
      query = getInstanceQuery(accountId, serviceId, false);
    }
    return query;
  }

  private Query<Instance> getInstanceQueryAtTime(String accountId, List<String> appIds, long timestamp) {
    Query<Instance> query;
    if (timestamp > 0) {
      query = getInstanceQuery(accountId, appIds, true, timestamp);
      query.field(Instance.CREATED_AT_KEY).lessThanOrEq(timestamp);
      query.and(
          query.or(query.criteria("isDeleted").equal(false), query.criteria("deletedAt").greaterThanOrEq(timestamp)));
    } else {
      query = getInstanceQuery(accountId, appIds, false, timestamp);
    }
    return query;
  }

  private List<InstanceStatsByEnvironment> constructInstanceStatsForService(
      String serviceId, List<ServiceAggregationInfo> serviceAggregationInfoList) {
    if (isEmpty(serviceAggregationInfoList)) {
      return Lists.newArrayList();
    }

    String appId = serviceAggregationInfoList.get(0).getAppInfo().getId();

    InstanceStatsByEnvironment currentEnv = null;
    InstanceStatsByArtifact currentArtifact;

    List<InstanceStatsByEnvironment> currentEnvList = Lists.newArrayList();
    List<InstanceStatsByArtifact> currentArtifactList = Lists.newArrayList();

    for (ServiceAggregationInfo serviceAggregationInfo : serviceAggregationInfoList) {
      int size = serviceAggregationInfo.getInstanceInfoList().size();
      List<EntitySummary> instanceList = Lists.newArrayListWithExpectedSize(size);
      for (EntitySummary instanceSummary : serviceAggregationInfo.getInstanceInfoList()) {
        // We have to clone the entity summary because type is not present in database.
        EntitySummary newInstanceSummary = EntitySummary.builder()
                                               .name(instanceSummary.getName())
                                               .id(instanceSummary.getId())
                                               .type(EntityType.INSTANCE.name())
                                               .build();
        instanceList.add(newInstanceSummary);
      }

      InstanceStats instanceStats = InstanceStats.Builder.anInstanceSummaryStats()
                                        .withEntitySummaryList(instanceList)
                                        .withTotalCount(size)
                                        .build();

      if (currentEnv == null || !compareEnvironment(currentEnv, serviceAggregationInfo.getEnvInfo())) {
        currentArtifactList = Lists.newArrayList();
        currentEnv =
            getInstanceStatsByEnvironment(appId, serviceId, serviceAggregationInfo.getEnvInfo(), currentArtifactList);
        currentEnvList.add(currentEnv);
      }

      currentArtifact = getInstanceStatsByArtifact(serviceAggregationInfo.getArtifactInfo(), instanceStats);
      currentArtifactList.add(currentArtifact);
    }

    currentEnvList.sort(
        (lhs,
            rhs) -> ObjectUtils.compare(lhs.getEnvironmentSummary().getName(), rhs.getEnvironmentSummary().getName()));
    return currentEnvList;
  }

  private List<InstanceStatsByService> constructInstanceStatsByService(List<AggregationInfo> aggregationInfoList) {
    List<InstanceStatsByService> instanceStatsByServiceList = Lists.newArrayList();
    InstanceStatsByService currentService = null;
    InstanceStatsByEnvironment currentEnv = null;
    InstanceStatsByArtifact currentArtifact;

    List<InstanceStatsByEnvironment> currentEnvList = Lists.newArrayList();
    List<InstanceStatsByArtifact> currentArtifactList = Lists.newArrayList();
    AtomicLong totalInstanceCountForService = new AtomicLong();
    for (AggregationInfo aggregationInfo : aggregationInfoList) {
      int size = aggregationInfo.getInstanceInfoList().size();
      List<EntitySummary> instanceList = Lists.newArrayListWithExpectedSize(size);
      for (EntitySummary instanceSummary : aggregationInfo.getInstanceInfoList()) {
        // We have to clone the entity summary because type is not present in database.
        EntitySummary newInstanceSummary = EntitySummary.builder()
                                               .name(instanceSummary.getName())
                                               .id(instanceSummary.getId())
                                               .type(EntityType.INSTANCE.name())
                                               .build();
        instanceList.add(newInstanceSummary);
      }

      InstanceStats instanceStats = InstanceStats.Builder.anInstanceSummaryStats()
                                        .withEntitySummaryList(instanceList)
                                        .withTotalCount(size)
                                        .build();

      if (currentService == null) {
        currentService = getInstanceStatsByService(aggregationInfo, totalInstanceCountForService, currentEnvList);
      } else if (!compareService(currentService, aggregationInfo.getServiceInfo())) {
        currentEnvList = Lists.newArrayList();
        currentEnv = null;
        // before moving on to the next service in the result set, set the totalInstanceCount to the current service.
        // To preserve immutability, we clone and set the new count.
        currentService = currentService.clone(totalInstanceCountForService.get());
        instanceStatsByServiceList.add(currentService);
        totalInstanceCountForService = new AtomicLong();

        currentService = getInstanceStatsByService(aggregationInfo, totalInstanceCountForService, currentEnvList);
      }

      if (currentEnv == null || !compareEnvironment(currentEnv, aggregationInfo.getEnvInfo())) {
        currentArtifactList = Lists.newArrayList();
        currentEnv = getInstanceStatsByEnvironment(currentService.getServiceSummary().getAppSummary().getId(),
            currentService.getServiceSummary().getId(), aggregationInfo.getEnvInfo(), currentArtifactList);
        currentEnvList.add(currentEnv);
      }

      currentArtifact = getInstanceStatsByArtifact(aggregationInfo.getArtifactInfo(), instanceStats);
      currentArtifactList.add(currentArtifact);
      totalInstanceCountForService.addAndGet(size);
    }

    // For the last service in the result set, the compareService() logic wouldn't trigger, we need to add the service
    // to the return list
    if (currentService != null) {
      currentService = currentService.clone(totalInstanceCountForService.get());
      instanceStatsByServiceList.add(currentService);
    }

    return instanceStatsByServiceList;
  }

  private InstanceStatsByArtifact getInstanceStatsByArtifact(ArtifactInfo artifactInfo, InstanceStats instanceStats) {
    ArtifactSummaryBuilder builder = ArtifactSummary.builder();
    builder.buildNo(artifactInfo.getBuildNo())
        .artifactSourceName(artifactInfo.getSourceName())
        .name(artifactInfo.getName())
        .type(ARTIFACT.name())
        .id(artifactInfo.getId());
    ArtifactSummary artifactSummary = builder.build();

    InstanceStatsByArtifact.Builder artifactBuilder = InstanceStatsByArtifact.Builder.anInstanceStatsByArtifact();
    artifactBuilder.withEntitySummary(artifactSummary);
    artifactBuilder.withInstanceStats(instanceStats);
    return artifactBuilder.build();
  }

  private InstanceStatsByEnvironment getInstanceStatsByEnvironment(
      String appId, String serviceId, EnvInfo envInfo, List<InstanceStatsByArtifact> currentArtifactList) {
    EnvironmentSummaryBuilder builder = EnvironmentSummary.builder();
    builder.prod("PROD".equals(envInfo.getType()))
        .id(envInfo.getId())
        .type(EntityType.ENVIRONMENT.name())
        .name(envInfo.getName());
    List<SyncStatus> syncStatusList = instanceService.getSyncStatus(appId, serviceId, envInfo.getId());
    InstanceStatsByEnvironmentBuilder instanceStatsByEnvironmentBuilder =
        InstanceStatsByEnvironment.builder()
            .environmentSummary(builder.build())
            .instanceStatsByArtifactList(currentArtifactList);
    if (isNotEmpty(syncStatusList)) {
      boolean hasSyncIssues = hasSyncIssues(syncStatusList);
      instanceStatsByEnvironmentBuilder.infraMappingSyncStatusList(syncStatusList);
      instanceStatsByEnvironmentBuilder.hasSyncIssues(hasSyncIssues);
    }

    return instanceStatsByEnvironmentBuilder.build();
  }

  private boolean hasSyncIssues(List<SyncStatus> syncStatusList) {
    return syncStatusList.stream().anyMatch(
        syncStatus -> syncStatus.getLastSyncedAt() != syncStatus.getLastSuccessfullySyncedAt());
  }

  private InstanceStatsByService getInstanceStatsByService(AggregationInfo instanceInfo,
      AtomicLong totalInstanceCountForService, List<InstanceStatsByEnvironment> currentEnvList) {
    ServiceSummaryBuilder serviceBuilder = ServiceSummary.builder();
    EntitySummary serviceInfo = instanceInfo.getServiceInfo();
    EntitySummary appInfo = instanceInfo.getAppInfo();
    EntitySummary appSummary =
        EntitySummary.builder().name(appInfo.getName()).id(appInfo.getId()).type(APPLICATION.name()).build();

    serviceBuilder.appSummary(appSummary)
        .id(serviceInfo.getId())
        .type(EntityType.SERVICE.name())
        .name(serviceInfo.getName());

    return InstanceStatsByService.builder()
        .serviceSummary(serviceBuilder.build())
        .totalCount(totalInstanceCountForService.get())
        .instanceStatsByEnvList(currentEnvList)
        .build();
  }

  private InstanceSummaryStatsByService getInstanceSummaryStatsByService(ServiceInstanceCount serviceInstanceCount) {
    ServiceSummaryBuilder serviceBuilder = ServiceSummary.builder();
    EntitySummary serviceInfo = serviceInstanceCount.getServiceInfo();
    EntitySummary appInfo = serviceInstanceCount.getAppInfo();
    EntitySummary appSummary =
        EntitySummary.builder().name(appInfo.getName()).id(appInfo.getId()).type(APPLICATION.name()).build();

    serviceBuilder.appSummary(appSummary)
        .id(serviceInfo.getId())
        .type(EntityType.SERVICE.name())
        .name(serviceInfo.getName());

    long prodCount = 0;
    long nonprodCount = 0;

    List<EnvType> envTypeList = serviceInstanceCount.getEnvTypeList();
    for (EnvType envType : envTypeList) {
      if (EnvironmentType.PROD.name().equals(envType.getType())) {
        ++prodCount;
      } else {
        ++nonprodCount;
      }
    }

    return InstanceSummaryStatsByService.builder()
        .serviceSummary(serviceBuilder.build())
        .totalCount(serviceInstanceCount.getCount())
        .prodCount(prodCount)
        .nonprodCount(nonprodCount)
        .build();
  }

  private boolean compareEnvironment(InstanceStatsByEnvironment currentEnv, EnvInfo envInfo) {
    return currentEnv != null && envInfo != null && envInfo.getId().equals(currentEnv.getEnvironmentSummary().getId());
  }

  private boolean compareService(InstanceStatsByService currentService, EntitySummary serviceInfo) {
    return currentService != null && serviceInfo != null
        && serviceInfo.getId().equals(currentService.getServiceSummary().getId());
  }

  @Override
  public ServiceInstanceDashboard getServiceInstanceDashboard(String accountId, String appId, String serviceId) {
    List<CurrentActiveInstances> currentActiveInstances = getCurrentActiveInstances(accountId, appId, serviceId);
    List<DeploymentHistory> deploymentHistoryList = getDeploymentHistory(accountId, appId, serviceId);
    Service service = serviceResourceService.getWithDetails(appId, serviceId);
    notNullCheck("Service not found", service, USER);
    EntitySummary serviceSummary = getEntitySummary(service.getName(), serviceId, EntityType.SERVICE.name());
    return ServiceInstanceDashboard.builder()
        .serviceSummary(serviceSummary)
        .currentActiveInstancesList(currentActiveInstances)
        .deploymentHistoryList(deploymentHistoryList)
        .build();
  }

  @VisibleForTesting
  public List<CurrentActiveInstances> getCurrentActiveInstances(String accountId, String appId, String serviceId) {
    Query<Instance> query;
    try {
      query = getInstanceQuery(accountId, singletonList(appId), false, 0L);
      query.filter("serviceId", serviceId);
    } catch (Exception exception) {
      handleException(exception);
      return emptyList();
    }

    List<AggregationInfo> instanceInfoList = new ArrayList<>();
    wingsPersistence.getDatastore(query.getEntityClass())
        .createAggregation(Instance.class)
        .match(query)
        .group(Group.id(grouping("envId"), grouping("infraMappingId"), grouping("lastArtifactId")),
            grouping("count", accumulator("$sum", 1)),
            grouping("appInfo", grouping("$first", projection("id", "appId"), projection("name", "appName"))),
            grouping("infraMappingInfo",
                grouping("$first", projection("id", "infraMappingId"), projection("name", "infraMappingName"))),
            grouping("envInfo",
                grouping(
                    "$first", projection("id", "envId"), projection("name", "envName"), projection("type", "envType"))),
            grouping("helmChartInfo",
                grouping("$first", projection("name", "instanceInfo.helmChartInfo.name"),
                    projection("version", "instanceInfo.helmChartInfo.version"),
                    projection("repoUrl", "instanceInfo.helmChartInfo.repoUrl"))),
            grouping("artifactInfo",
                grouping("$last", projection("id", "lastArtifactId"), projection("name", "lastArtifactName"),
                    projection("buildNo", "lastArtifactBuildNum"), projection("streamId", "lastArtifactStreamId"),
                    projection("deployedAt", "lastDeployedAt"), projection("sourceName", "lastArtifactSourceName"),
                    projection("lastWorkflowExecutionId", "lastWorkflowExecutionId"))))
        .sort(descending("count"))
        .aggregate(AggregationInfo.class)
        .forEachRemaining(instanceInfoList::add);
    return constructCurrentActiveInstances(instanceInfoList, appId, accountId);
  }

  private List<CurrentActiveInstances> constructCurrentActiveInstances(
      List<AggregationInfo> aggregationInfoList, String appId, String accountId) {
    List<CurrentActiveInstances> currentActiveInstancesList = Lists.newArrayList();
    for (AggregationInfo aggregationInfo : aggregationInfoList) {
      long count = aggregationInfo.getCount();

      EntitySummary infraMappingInfo = aggregationInfo.getInfraMappingInfo();
      notNullCheck("InfraMappingInfo", infraMappingInfo, USER);
      EntitySummary serviceInfraSummary = getEntitySummary(
          infraMappingInfo.getName(), infraMappingInfo.getId(), EntityType.INFRASTRUCTURE_MAPPING.name());

      EnvInfo envInfo = aggregationInfo.getEnvInfo();
      notNullCheck("EnvInfo", envInfo, USER);
      EntitySummary environmentSummary =
          getEntitySummary(envInfo.getName(), envInfo.getId(), EntityType.ENVIRONMENT.name());

      ArtifactInfo artifactInfo = aggregationInfo.getArtifactInfo();
      notNullCheck("QLArtifact", artifactInfo, USER);
      ArtifactSummary artifactSummary = getArtifactSummary(
          artifactInfo.getName(), artifactInfo.getId(), artifactInfo.getBuildNo(), artifactInfo.getSourceName());

      HelmChartInfo helmChartInfo = aggregationInfo.getHelmChartInfo();
      ManifestSummary manifestSummary = null;
      if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId) && helmChartInfo != null) {
        manifestSummary = ManifestSummary.builder()
                              .name(helmChartInfo.getName())
                              .versionNo(helmChartInfo.getVersion())
                              .source(helmChartInfo.getRepoUrl())
                              .build();
      }

      long deployedAt = aggregationInfo.getArtifactInfo().getDeployedAt();

      String lastWorkflowExecutionId = aggregationInfo.getArtifactInfo().getLastWorkflowExecutionId();
      // To fetch last execution
      final WorkflowExecution lastWE = wingsPersistence.createQuery(WorkflowExecution.class)
                                           .filter(WorkflowExecutionKeys.uuid, lastWorkflowExecutionId)
                                           .get();

      CurrentActiveInstances currentActiveInstances;
      if (lastWE == null) {
        log.info("Last workflow execution is null, Execution Id {}", lastWorkflowExecutionId);
        currentActiveInstances = CurrentActiveInstances.builder()
                                     .artifact(artifactSummary)
                                     .manifest(manifestSummary)
                                     .lastWorkflowExecutionDate(new Date(deployedAt))
                                     .deployedAt(new Date(deployedAt))
                                     .environment(environmentSummary)
                                     .instanceCount(count)
                                     .serviceInfra(serviceInfraSummary)
                                     .onDemandRollbackAvailable(false)
                                     .build();
      } else {
        Workflow workflow = workflowService.readWorkflowWithoutOrchestration(appId, lastWE.getWorkflowId());
        String workflowName;
        if (workflow != null) {
          workflowName = workflow.getName();
        } else {
          // if workflow is deleted setting workflowExecution name so UI does not break.
          workflowName = lastWE.getName();
        }
        boolean rollbackAvailable = workflowExecutionService.getOnDemandRollbackAvailable(appId, lastWE);
        EntitySummary workflowExecutionSummary =
            getEntitySummary(workflowName, lastWE.getUuid(), EntityType.WORKFLOW_EXECUTION.name());

        PipelineSummary pipelineSummary = lastWE.getPipelineSummary();
        // This is just precautionary this should never happen hence logging this
        if (lastWE.isOnDemandRollback() && pipelineSummary != null) {
          log.error("Pipeline Summary non null for rollback execution : {}", lastWE.getUuid());
          pipelineSummary = null;
        }
        EntitySummary pipelineEntitySummary = null;
        if (pipelineSummary != null) {
          pipelineEntitySummary = getEntitySummary(
              pipelineSummary.getPipelineName(), lastWE.getPipelineExecutionId(), EntityType.PIPELINE.name());
        }

        currentActiveInstances = CurrentActiveInstances.builder()
                                     .artifact(artifactSummary)
                                     .manifest(manifestSummary)
                                     .lastWorkflowExecutionDate(new Date(lastWE.getStartTs()))
                                     .deployedAt(new Date(deployedAt))
                                     .environment(environmentSummary)
                                     .instanceCount(count)
                                     .serviceInfra(serviceInfraSummary)
                                     .lastWorkflowExecution(workflowExecutionSummary)
                                     .lastPipelineExecution(pipelineEntitySummary)
                                     .onDemandRollbackAvailable(rollbackAvailable)
                                     .build();
      }
      currentActiveInstancesList.add(currentActiveInstances);
    }

    return currentActiveInstancesList;
  }

  @Override
  public Instance getInstanceDetails(String instanceId) {
    return instanceService.get(instanceId, true);
  }

  @Override
  public Set<String> getDeletedAppIds(String accountId, long timestamp) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    query.project("appId", true);
    List<Instance> instancesForAccount = getInstancesForAccount(accountId, timestamp, query);
    Set<String> appIdsFromInstances = instancesForAccount.stream().map(Instance::getAppId).collect(Collectors.toSet());

    List<Application> appsByAccountId = appService.getAppsByAccountId(accountId);
    Set<String> existingApps = appsByAccountId.stream().map(Application::getUuid).collect(Collectors.toSet());
    appIdsFromInstances.removeAll(existingApps);
    return appIdsFromInstances;
  }

  @Override
  public Set<String> getDeletedAppIds(String accountId, long fromTimestamp, long toTimestamp) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    query.project("appId", true);
    query.project(Instance.CREATED_AT_KEY, true);

    // Find the timestamp of oldest instance alive at fromTimestamp
    long lhsCreatedAt = getCreatedTimeOfInstanceAtTimestamp(accountId, fromTimestamp, query, true);
    // Find the timestamp of latest instance alive at toTimestamp
    long rhsCreatedAt = getCreatedTimeOfInstanceAtTimestamp(accountId, toTimestamp, query, false);

    DBCollection dbCollection = wingsPersistence.getCollection(Instance.class);
    BasicDBObject instanceQuery = new BasicDBObject();
    List<BasicDBObject> conditions = new ArrayList<>();
    conditions.add(new BasicDBObject(InstanceKeys.accountId, accountId));
    conditions.add(new BasicDBObject(Instance.CREATED_AT_KEY, new BasicDBObject("$gte", lhsCreatedAt)));
    conditions.add(new BasicDBObject(Instance.CREATED_AT_KEY, new BasicDBObject("$lte", rhsCreatedAt)));
    instanceQuery.put("$and", conditions);
    Set<String> appIdsFromInstances =
        new HashSet<>(dbCollection.distinct(InstanceKeys.appId, instanceQuery, ReadPreference.secondaryPreferred()));

    List<Application> appsByAccountId = appService.getAppsByAccountId(accountId);
    Set<String> existingApps = appsByAccountId.stream().map(Application::getUuid).collect(Collectors.toSet());
    appIdsFromInstances.removeAll(existingApps);
    return appIdsFromInstances;
  }

  @VisibleForTesting
  public List<DeploymentHistory> getDeploymentHistory(String accountId, String appId, String serviceId) {
    List<DeploymentHistory> deploymentExecutionHistoryList = new ArrayList<>();
    Service service = serviceResourceService.getWithDetails(appId, serviceId);
    if (service == null) {
      return deploymentExecutionHistoryList;
    }

    List<String> artifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(service);

    PageRequestBuilder pageRequestBuilder = aPageRequest()
                                                .addFilter("appId", EQ, appId)
                                                .addFilter("workflowType", EQ, ORCHESTRATION)
                                                .addFilter("serviceIds", HAS, serviceId)
                                                .addOrder(WorkflowExecutionKeys.createdAt, OrderType.DESC)
                                                .withLimit("10");

    Optional<Integer> retentionPeriodInDays =
        ((DeploymentHistoryFeature) deploymentHistoryFeature).getRetentionPeriodInDays(accountId);
    retentionPeriodInDays.ifPresent(val
        -> pageRequestBuilder.addFilter(WorkflowExecutionKeys.startTs, GE,
            EpochUtils.calculateEpochMilliOfStartOfDayForXDaysInPastFromNow(val, "UTC")));

    PageRequest<WorkflowExecution> pageRequest = pageRequestBuilder.build();

    List<WorkflowExecution> workflowExecutionList =
        workflowExecutionService.listExecutions(pageRequest, false).getResponse();

    if (isEmpty(workflowExecutionList)) {
      return deploymentExecutionHistoryList;
    }

    for (WorkflowExecution workflowExecution : workflowExecutionList) {
      PipelineSummary pipelineSummary = workflowExecution.getPipelineSummary();
      // This is just precautionary this should never happen hence logging this
      if (workflowExecution.isOnDemandRollback() && pipelineSummary != null) {
        log.error("Pipeline Summary non null for rollback execution : {}", workflowExecution.getUuid());
        pipelineSummary = null;
      }
      EntitySummary pipelineEntitySummary = null;
      if (pipelineSummary != null) {
        pipelineEntitySummary = getEntitySummary(
            pipelineSummary.getPipelineName(), workflowExecution.getPipelineExecutionId(), EntityType.PIPELINE.name());
      }

      EntitySummary workflowExecutionSummary =
          getEntitySummary(workflowExecution.normalizedName(), workflowExecution.getUuid(), EntityType.WORKFLOW.name());
      EmbeddedUser triggeredByUser = workflowExecution.getTriggeredBy();
      EntitySummary triggeredBySummary = null;
      if (triggeredByUser != null) {
        triggeredBySummary =
            getEntitySummary(triggeredByUser.getName(), triggeredByUser.getUuid(), EntityType.USER.name());
      }

      Integer instancesCount = null;
      List<ElementExecutionSummary> serviceExecutionSummaries = workflowExecution.getServiceExecutionSummaries();

      if (isNotEmpty(serviceExecutionSummaries)) {
        // we always have one execution summary per workflow
        ElementExecutionSummary elementExecutionSummary = serviceExecutionSummaries.get(0);
        instancesCount = elementExecutionSummary.getInstancesCount();
      }

      long instanceCount = 0L;
      if (instancesCount != null) {
        instanceCount = instancesCount.longValue();
      }

      ExecutionArgs executionArgs = workflowExecution.getExecutionArgs();
      if (executionArgs == null) {
        if (log.isDebugEnabled()) {
          log.debug("executionArgs is null for workflowExecution:" + workflowExecution.normalizedName());
        }
        continue;
      }

      ManifestSummary manifestSummary = null;
      List<HelmChart> helmCharts = executionArgs.getHelmCharts();
      if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)) {
        if (helmCharts == null) {
          if (log.isDebugEnabled()) {
            log.debug("Helm chart is null for workflowExecution:" + workflowExecution.normalizedName());
          }
        } else {
          manifestSummary = helmCharts.stream()
                                .filter(chart -> serviceId.equals(chart.getServiceId()))
                                .findFirst()
                                .map(this::prepareManifestSummaryFromHelmChart)
                                .orElse(null);
        }
      }

      List<Artifact> artifacts = executionArgs.getArtifacts();
      if (artifacts == null) {
        if (log.isDebugEnabled()) {
          log.debug("artifacts is null for workflowExecution:" + workflowExecution.normalizedName());
        }
      }

      Long startTs = workflowExecution.getStartTs();
      Date startDate = null;
      if (startTs != null) {
        startDate = new Date(startTs.longValue());
      }

      ExecutionStatus status = workflowExecution.getStatus();
      String executionStatus = null;
      if (status != null) {
        executionStatus = status.name();
      }

      if (isNotEmpty(artifacts)) {
        for (Artifact artifact : artifacts) {
          if (artifact == null) {
            continue;
          }

          // The executionArgs contain all the artifacts involved in multiple stages of the pipeline.
          // We need to filter them down to only the ones that are mapped to the current service.
          if (!artifactStreamIds.contains(artifact.getArtifactStreamId())) {
            continue;
          }

          ArtifactSummary artifactSummary = getArtifactSummary(
              artifact.getDisplayName(), artifact.getUuid(), artifact.getBuildNo(), artifact.getArtifactSourceName());

          DeploymentHistory deploymentHistory =
              getDeploymentHistory(appId, serviceId, workflowExecution, pipelineEntitySummary, workflowExecutionSummary,
                  triggeredBySummary, instanceCount, manifestSummary, startDate, executionStatus, artifactSummary);
          deploymentExecutionHistoryList.add(deploymentHistory);
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug("artifacts is null for workflowExecution:" + workflowExecution.normalizedName());
        }
        deploymentExecutionHistoryList.add(
            getDeploymentHistory(appId, serviceId, workflowExecution, pipelineEntitySummary, workflowExecutionSummary,
                triggeredBySummary, instanceCount, manifestSummary, startDate, executionStatus, null));
      }
    }

    return deploymentExecutionHistoryList;
  }

  private DeploymentHistory getDeploymentHistory(String appId, String serviceId, WorkflowExecution workflowExecution,
      EntitySummary pipelineEntitySummary, EntitySummary workflowExecutionSummary, EntitySummary triggeredBySummary,
      long instanceCount, ManifestSummary manifestSummary, Date startDate, String executionStatus,
      ArtifactSummary artifactSummary) {
    List<String> envIdList = workflowExecution.getEnvIds();
    List<EntitySummary> envList = null;
    if (isNotEmpty(envIdList)) {
      PageRequest<Environment> envPageRequest = aPageRequest()
                                                    .addFilter("_id", IN, envIdList.toArray())
                                                    .addFilter("appId", EQ, appId)
                                                    .addFieldsIncluded("_id", "name")
                                                    .build();

      PageResponse<Environment> pageResponse = environmentService.list(envPageRequest, false, null);

      List<Environment> environmentList = pageResponse.getResponse();
      if (isNotEmpty(environmentList)) {
        envList = environmentList.stream()
                      .map(env
                          -> EntitySummary.builder()
                                 .id(env.getUuid())
                                 .name(env.getName())
                                 .type(EntityType.ENVIRONMENT.name())
                                 .build())
                      .collect(toList());
      }
    }

    List<EntitySummary> serviceInfraList = null;
    List<String> infraMappingIdList = workflowExecution.getInfraMappingIds();
    if (isNotEmpty(infraMappingIdList)) {
      PageRequest<InfrastructureMapping> envPageRequest = aPageRequest()
                                                              .addFilter("_id", IN, infraMappingIdList.toArray())
                                                              .addFilter("serviceId", EQ, serviceId)
                                                              .addFilter("appId", EQ, appId)
                                                              .addFieldsIncluded("_id", "name", "displayName")
                                                              .build();

      PageResponse<InfrastructureMapping> pageResponse = infraMappingService.list(envPageRequest);

      List<InfrastructureMapping> infraList = pageResponse.getResponse();
      if (isNotEmpty(infraList)) {
        serviceInfraList = infraList.stream()
                               .map(infraMapping
                                   -> EntitySummary.builder()
                                          .id(infraMapping.getUuid())
                                          .name(infraMapping.getDisplayName())
                                          .type(EntityType.INFRASTRUCTURE_MAPPING.name())
                                          .build())
                               .collect(toList());
      }
    }

    return DeploymentHistory.builder()
        .artifact(artifactSummary)
        .manifest(manifestSummary)
        .envs(envList)
        .inframappings(serviceInfraList)
        .deployedAt(startDate)
        .rolledBack(workflowExecution.isOnDemandRollback())
        .instanceCount(instanceCount)
        .pipeline(pipelineEntitySummary)
        .status(executionStatus)
        .triggeredBy(triggeredBySummary)
        .workflow(workflowExecutionSummary)
        .build();
  }

  private ManifestSummary prepareManifestSummaryFromHelmChart(HelmChart helmChart) {
    helmChart.setMetadata(applicationManifestService.fetchAppManifestProperties(
        helmChart.getAppId(), helmChart.getApplicationManifestId()));
    return ManifestSummary.prepareSummaryFromHelmChart(helmChart);
  }

  private EntitySummary getEntitySummary(String name, String id, String type) {
    return EntitySummary.builder().type(type).id(id).name(name).build();
  }

  private ArtifactSummary getArtifactSummary(String name, String id, String buildNum, String artifactSourceName) {
    ArtifactSummaryBuilder builder = ArtifactSummary.builder().buildNo(buildNum).artifactSourceName(artifactSourceName);
    return builder.type(ARTIFACT.name()).id(id).name(name).build();
  }

  private Query<Instance> getInstanceQuery(
      String accountId, List<String> appIds, boolean includeDeleted, long timestamp) {
    Query query = wingsPersistence.createQuery(Instance.class);
    if (isNotEmpty(appIds)) {
      query.field("appId").in(appIds);
    } else {
      User user = UserThreadLocal.get();
      if (user != null) {
        UserRequestContext userRequestContext = user.getUserRequestContext();
        if (userRequestContext.isAppIdFilterRequired()) {
          Set<String> allowedAppIds = userRequestContext.getAppIds();

          if (includeDeleted && userService.isAccountAdmin(accountId)) {
            Set<String> deletedAppIds = getDeletedAppIds(accountId, timestamp);
            if (isNotEmpty(deletedAppIds)) {
              allowedAppIds = Sets.newHashSet(allowedAppIds);
              allowedAppIds.addAll(deletedAppIds);
            }
          }

          if (isNotEmpty(allowedAppIds)) {
            // This is an optimization. Instead of a large IN() Query, if the user has access to all apps,
            // we could just pull it using accountId. For example, QA has 500 apps in our account.
            if (userRequestContext.getUserPermissionInfo().isHasAllAppAccess()) {
              query.filter("accountId", accountId);
            } else {
              query.field("appId").in(allowedAppIds);
            }
          } else {
            throw NoResultFoundException.newBuilder().code(NO_APPS_ASSIGNED).build();
          }
        }
      } else {
        throw NoResultFoundException.newBuilder().code(NO_APPS_ASSIGNED).build();
      }
    }

    if (!includeDeleted) {
      query.filter("isDeleted", false);
    }

    return query;
  }

  private Query<Instance> getInstanceQuery(String accountId, String serviceId, boolean includeDeleted) {
    Query query = wingsPersistence.createQuery(Instance.class);
    if (!includeDeleted) {
      query.filter("isDeleted", false);
    }
    query.filter("accountId", accountId);
    query.filter("serviceId", serviceId);
    return query;
  }
  @Override
  public PageResponse<CompareEnvironmentAggregationResponseInfo> getCompareServicesByEnvironment(
      String accountId, String appId, String envId1, String envId2, int offset, int limit) {
    ReadPreference readPreference;
    if (Objects.isNull(mongoTagSet)) {
      readPreference = ReadPreference.secondaryPreferred();
    } else {
      readPreference = ReadPreference.secondary(mongoTagSet);
    }
    Query<Instance> query;
    try {
      query = getQueryForCompareServicesByEnvironment(appId, envId1, envId2);
    } catch (NoResultFoundException nre) {
      return getEmptyPageResponse();
    }

    List<CompareEnvironmentAggregationInfo> instanceInfoList = new ArrayList<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(query.getEntityClass())
            .createAggregation(Instance.class)
            .match(query)
            .group(Group.id(grouping(InstanceKeys.serviceId), grouping(InstanceKeys.envId),
                       grouping(InstanceKeys.lastArtifactBuildNum), grouping(InstanceKeys.infraMappingId),
                       grouping(InstanceKeys.lastWorkflowExecutionId)),
                grouping("count", accumulator("$sum", 1)),
                grouping(InstanceKeys.serviceName, first(InstanceKeys.serviceName)),
                grouping(InstanceKeys.lastWorkflowExecutionName, first(InstanceKeys.lastWorkflowExecutionName)),
                grouping(InstanceKeys.infraMappingName, first(InstanceKeys.infraMappingName)))
            .group(Group.id(grouping(InstanceKeys.serviceId, "_id." + InstanceKeys.serviceId)),
                grouping(CompareEnvironmentAggregationInfoKeys.serviceId, first("_id." + InstanceKeys.serviceId)),
                grouping(CompareEnvironmentAggregationInfoKeys.serviceName, first(InstanceKeys.serviceName)),
                grouping(CompareEnvironmentAggregationInfoKeys.count, sum(CompareEnvironmentAggregationInfoKeys.count)),
                grouping(CompareEnvironmentAggregationInfoKeys.serviceInfoSummaries,
                    grouping("$push", projection(ServiceInfoSummaryKeys.serviceName, InstanceKeys.serviceName),
                        projection(ServiceInfoSummaryKeys.envId, "_id." + InstanceKeys.envId),
                        projection(
                            ServiceInfoSummaryKeys.lastArtifactBuildNum, "_id." + InstanceKeys.lastArtifactBuildNum),
                        projection(ServiceInfoSummaryKeys.lastWorkflowExecutionId,
                            "_id." + InstanceKeys.lastWorkflowExecutionId),
                        projection(
                            ServiceInfoSummaryKeys.lastWorkflowExecutionName, InstanceKeys.lastWorkflowExecutionName),
                        projection(ServiceInfoSummaryKeys.infraMappingId, "_id." + InstanceKeys.infraMappingId),
                        projection(ServiceInfoSummaryKeys.infraMappingName, InstanceKeys.infraMappingName))))
            .sort(Sort.ascending(InstanceKeys.serviceName));

    aggregationPipeline.skip(offset);
    aggregationPipeline.limit(limit);

    final Iterator<CompareEnvironmentAggregationInfo> aggregate =
        HPersistence.retry(()
                               -> aggregationPipeline.aggregate(CompareEnvironmentAggregationInfo.class,
                                   AggregationOptions.builder().build(), readPreference));
    aggregate.forEachRemaining(instanceInfoList::add);

    List<CompareEnvironmentAggregationResponseInfo> responseList = new ArrayList<>();
    for (CompareEnvironmentAggregationInfo instanceInfo : instanceInfoList) {
      responseList.add(CompareEnvironmentAggregationResponseInfo.builder()
                           .serviceId(instanceInfo.getServiceId())
                           .serviceName(instanceInfo.getServiceName())
                           .count(instanceInfo.getCount())
                           .envInfo(emptyIfNull(instanceInfo.getServiceInfoSummaries())
                                        .stream()
                                        .collect(Collectors.groupingBy(ServiceInfoSummary::getEnvId,
                                            Collectors.mapping(item -> createServiceSummary(item), toList()))))
                           .build());
    }

    emptyIfNull(responseList)
        .stream()
        .map(item -> {
          item.envInfo.computeIfAbsent(envId1, k -> new ArrayList<>());
          item.envInfo.computeIfAbsent(envId2, k -> new ArrayList<>());
          return item;
        })
        .collect(toList());

    List<String> serviceList = new ArrayList<>();
    AggregationPipeline aggregationPipelineCount = wingsPersistence.getDatastore(query.getEntityClass())
                                                       .createAggregation(Instance.class)
                                                       .match(query)
                                                       .group(Group.id(grouping(InstanceKeys.serviceId)));
    final Iterator<String> aggregateForCount =
        HPersistence.retry(() -> aggregationPipelineCount.aggregate(String.class));
    aggregateForCount.forEachRemaining(serviceList::add);

    return aPageResponse()
        .withResponse(responseList)
        .withOffset(Integer.toString(offset))
        .withLimit(Integer.toString(limit))
        .withTotal(serviceList.size())
        .build();
  }

  private ServiceInfoResponseSummary createServiceSummary(ServiceInfoSummary item) {
    String lastWorkflowExecutionId = item.getLastWorkflowExecutionId();
    // To fetch last execution
    WorkflowExecution lastWorkflowExecution = null;

    if (isNotEmpty(lastWorkflowExecutionId)) {
      lastWorkflowExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                  .filter(WorkflowExecutionKeys.uuid, lastWorkflowExecutionId)
                                  .get();
    }

    ServiceInfoResponseSummaryBuilder serviceInfoResponseSummaryBuilder =
        ServiceInfoResponseSummary.builder()
            .lastArtifactBuildNum(item.getLastArtifactBuildNum())
            .infraMappingName(item.getInfraMappingName())
            .infraMappingId(item.getInfraMappingId());

    if (lastWorkflowExecution != null) {
      serviceInfoResponseSummaryBuilder.lastWorkflowExecutionId(item.getLastWorkflowExecutionId())
          .lastWorkflowExecutionName(item.getLastWorkflowExecutionName());
    }
    return serviceInfoResponseSummaryBuilder.build();
  }

  private Query<Instance> getQueryForCompareServicesByEnvironment(String appId, String envId1, String envId2) {
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    query.filter(InstanceKeys.appId, appId);
    query.filter(InstanceKeys.isDeleted, false);
    query.or(query.criteria(InstanceKeys.envId).equal(envId1), query.criteria(InstanceKeys.envId).equal(envId2));
    return query;
  }
}
