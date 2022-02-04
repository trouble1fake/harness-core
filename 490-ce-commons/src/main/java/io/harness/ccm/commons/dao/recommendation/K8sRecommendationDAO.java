/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.utils.TimeUtils.offsetDateTimeNow;
import static io.harness.ccm.commons.utils.TimeUtils.toEpocMilli;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import static io.harness.persistence.HQuery.excludeValidate;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;
import static io.harness.timescaledb.Tables.KUBERNETES_UTILIZATION_DATA;
import static io.harness.timescaledb.Tables.NODE_INFO;
import static io.harness.timescaledb.Tables.NODE_POOL_AGGREGATED;
import static io.harness.timescaledb.Tables.POD_INFO;

import static com.google.common.base.MoreObjects.firstNonNull;
import static org.jooq.impl.DSL.concat;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.val;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.retry.RetryOnException;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.beans.recommendation.K8sServiceProvider;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.NodePoolId.NodePoolIdKeys;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.RecommendationTelemetryStats;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.beans.recommendation.TotalResourceUsage;
import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendationResponse;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.batch.InstanceData.InstanceDataKeys;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sNodeRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sNodeRecommendation.K8sNodeRecommendationKeys;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation.K8sWorkloadRecommendationKeys;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram.PartialRecommendationHistogramKeys;
import io.harness.ccm.commons.utils.TimescaleUtils;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.Keys;
import io.harness.timescaledb.Routines;
import io.harness.timescaledb.tables.pojos.CeRecommendations;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectFinalStep;
import org.jooq.SelectSelectStep;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.types.YearToSecond;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
@OwnedBy(CE)
public class K8sRecommendationDAO {
  private static final int RETRY_COUNT = 3;
  private static final int SLEEP_DURATION = 100;

  private static final String MAXCPU = "maxcpu";
  private static final String MAXMEMORY = "maxmemory";
  private static final String SUMCPU = "sumcpu";
  private static final String SUMMEMORY = "summemory";
  private static final String TIME = "time";
  private static final String ACCOUNT_ID = "accountid";

  @Inject private HPersistence hPersistence;
  @Inject private DSLContext dslContext;

  @NonNull
  public Optional<K8sWorkloadRecommendation> fetchK8sWorkloadRecommendationById(
      @NonNull String accountIdentifier, @NonNull String id) {
    return Optional.ofNullable(hPersistence.createQuery(K8sWorkloadRecommendation.class)
                                   .filter(K8sWorkloadRecommendationKeys.accountId, accountIdentifier)
                                   .filter(K8sWorkloadRecommendationKeys.uuid, id)
                                   .get());
  }

  public List<PartialRecommendationHistogram> fetchPartialRecommendationHistograms(
      @NonNull String accountId, @NonNull ResourceId resourceId, @NonNull Instant startTime, @NonNull Instant endTime) {
    return hPersistence.createQuery(PartialRecommendationHistogram.class)
        .filter(PartialRecommendationHistogramKeys.accountId, accountId)
        .filter(PartialRecommendationHistogramKeys.clusterId, resourceId.getClusterId())
        .filter(PartialRecommendationHistogramKeys.namespace, resourceId.getNamespace())
        .filter(PartialRecommendationHistogramKeys.workloadName, resourceId.getName())
        .filter(PartialRecommendationHistogramKeys.workloadType, resourceId.getKind())
        .field(PartialRecommendationHistogramKeys.date)
        .greaterThanOrEq(startTime)
        .field(PartialRecommendationHistogramKeys.date)
        .lessThanOrEq(endTime)
        .asList();
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public List<CeRecommendations> fetchRecommendationsOverview(
      @NonNull String accountId, @Nullable Condition condition, @NonNull Long offset, @NonNull Long limit) {
    return dslContext.selectFrom(CE_RECOMMENDATIONS)
        .where(CE_RECOMMENDATIONS.ACCOUNTID.eq(accountId).and(firstNonNull(condition, DSL.noCondition())))
        .orderBy(CE_RECOMMENDATIONS.MONTHLYSAVING.desc().nullsLast())
        .offset(offset)
        .limit(limit)
        .fetchInto(CeRecommendations.class);
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public RecommendationOverviewStats fetchRecommendationsOverviewStats(
      @NonNull String accountId, @Nullable Condition condition) {
    return dslContext
        .select(sum(CE_RECOMMENDATIONS.MONTHLYCOST).as("totalMonthlyCost"),
            sum(CE_RECOMMENDATIONS.MONTHLYSAVING).as("totalMonthlySaving"))
        .from(CE_RECOMMENDATIONS)
        .where(CE_RECOMMENDATIONS.ACCOUNTID.eq(accountId).and(firstNonNull(condition, DSL.noCondition())))
        .fetchOneInto(RecommendationOverviewStats.class);
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public List<RecommendationTelemetryStats> fetchRecommendationsTelemetry(@NonNull String accountId) {
    return dslContext
        .select(DSL.count().as("count"), sum(CE_RECOMMENDATIONS.MONTHLYCOST).as("totalMonthlyCost"),
            sum(CE_RECOMMENDATIONS.MONTHLYSAVING).as("totalMonthlySaving"), CE_RECOMMENDATIONS.RESOURCETYPE.as("type"))
        .from(CE_RECOMMENDATIONS)
        .where(CE_RECOMMENDATIONS.ACCOUNTID.eq(accountId))
        .groupBy(CE_RECOMMENDATIONS.RESOURCETYPE)
        .fetchInto(RecommendationTelemetryStats.class);
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public List<NodePoolId> getUniqueNodePools(@NonNull String accountId) {
    // TODO(UTSAV): We want to fetch only node pools which has at least one node running in jobStartTime and jobEndTime
    // window. will enforce this once the recommendation algo is stable.
    return dslContext.select(NODE_INFO.NODEPOOLNAME, NODE_INFO.CLUSTERID)
        .from(NODE_INFO)
        .where(NODE_INFO.ACCOUNTID.eq(accountId))
        .groupBy(NODE_INFO.CLUSTERID, NODE_INFO.NODEPOOLNAME)
        .fetchInto(NodePoolId.class);
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public TotalResourceUsage maxResourceOfAllTimeBucketsForANodePool(
      @NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId) {
    Table<? extends Record> t2 = sumResourceForEachTimeBucket(jobConstants, nodePoolId);

    // T3
    SelectSelectStep<? extends Record> selectStepT3 =
        select(max(t2.field(SUMCPU)).as(SUMCPU), max(t2.field(SUMMEMORY)).as(SUMMEMORY),
            max(t2.field(MAXCPU)).as(MAXCPU), max(t2.field(MAXMEMORY)).as(MAXMEMORY));

    SelectFinalStep<? extends Record> finalStepT3 = dslContext.select(selectStepT3.getSelect()).from(t2);
    log.debug("maxResourceOfAllTimeBucketsForANodePool, final query\n{}", finalStepT3.toString());

    return finalStepT3.fetchOneInto(TotalResourceUsage.class);
  }

  private Table<? extends Record> sumResourceForEachTimeBucket(JobConstants jobConstants, NodePoolId nodePoolId) {
    Table<? extends Record> t1 = groupByInstanceIdForEachTimeBucket(jobConstants, nodePoolId);

    SelectSelectStep<? extends Record> selectStep = select(sum(t1.field(KUBERNETES_UTILIZATION_DATA.CPU)).as(SUMCPU),
        sum(t1.field(KUBERNETES_UTILIZATION_DATA.MEMORY)).as(SUMMEMORY),
        max(t1.field(KUBERNETES_UTILIZATION_DATA.MAXCPU)).as(MAXCPU),
        max(t1.field(KUBERNETES_UTILIZATION_DATA.MAXMEMORY)).as(MAXMEMORY));

    return dslContext.select(selectStep.getSelect()).from(t1).groupBy(t1.field(TIME)).asTable();
  }

  private Table<? extends Record> groupByInstanceIdForEachTimeBucket(JobConstants jobConstants, NodePoolId nodePoolId) {
    Table<? extends Record> t0 = getPodRequest(jobConstants, nodePoolId);

    Field<OffsetDateTime> timeBucket =
        Routines.timeBucket2(val(YearToSecond.valueOf(Duration.ofMinutes(20))), KUBERNETES_UTILIZATION_DATA.STARTTIME)
            .as(TIME);

    Condition kubernetesUtilizationData_with_t0 =
        KUBERNETES_UTILIZATION_DATA.ACTUALINSTANCEID.eq(t0.field(KUBERNETES_UTILIZATION_DATA.ACTUALINSTANCEID))
            .and(KUBERNETES_UTILIZATION_DATA.ACCOUNTID.eq(jobConstants.getAccountId())
                     .and(KUBERNETES_UTILIZATION_DATA.CLUSTERID.eq(nodePoolId.getClusterid())
                              .and(TimescaleUtils.isAlive(KUBERNETES_UTILIZATION_DATA.STARTTIME,
                                  KUBERNETES_UTILIZATION_DATA.ENDTIME, jobConstants.getJobStartTime(),
                                  jobConstants.getJobEndTime()))));

    SelectSelectStep<? extends Record> selectStepT1 = select(timeBucket,
        greatest(max(KUBERNETES_UTILIZATION_DATA.CPU), max(t0.field(POD_INFO.CPUREQUEST)))
            .as(KUBERNETES_UTILIZATION_DATA.CPU),
        greatest(max(KUBERNETES_UTILIZATION_DATA.MEMORY), max(t0.field(POD_INFO.MEMORYREQUEST)))
            .as(KUBERNETES_UTILIZATION_DATA.MEMORY),
        greatest(max(KUBERNETES_UTILIZATION_DATA.MAXCPU), max(t0.field(POD_INFO.CPUREQUEST)))
            .as(KUBERNETES_UTILIZATION_DATA.MAXCPU),
        greatest(max(KUBERNETES_UTILIZATION_DATA.MAXMEMORY), max(t0.field(POD_INFO.MEMORYREQUEST)))
            .as(KUBERNETES_UTILIZATION_DATA.MAXMEMORY));

    return dslContext.select(selectStepT1.getSelect())
        .from(KUBERNETES_UTILIZATION_DATA.innerJoin(t0).on(kubernetesUtilizationData_with_t0))
        .groupBy(timeBucket, KUBERNETES_UTILIZATION_DATA.ACTUALINSTANCEID)
        .asTable();
  }

  private Table<? extends Record> getPodRequest(JobConstants jobConstants, NodePoolId nodePoolId) {
    final String kube_system_namespace = "kube-system";
    final String namespace_name_separator = "/";

    return dslContext
        .select(concat(POD_INFO.NAMESPACE, val(namespace_name_separator), POD_INFO.NAME)
                    .as(KUBERNETES_UTILIZATION_DATA.ACTUALINSTANCEID),
            POD_INFO.CPUREQUEST, POD_INFO.MEMORYREQUEST)
        .from(POD_INFO.innerJoin(NODE_INFO).on(NODE_INFO.INSTANCEID.eq(POD_INFO.PARENTNODEID),
            NODE_INFO.ACCOUNTID.eq(jobConstants.getAccountId()), NODE_INFO.CLUSTERID.eq(nodePoolId.getClusterid()),
            POD_INFO.ACCOUNTID.eq(jobConstants.getAccountId()), POD_INFO.CLUSTERID.eq(nodePoolId.getClusterid()),
            NODE_INFO.NODEPOOLNAME.eq(nodePoolId.getNodepoolname()),
            TimescaleUtils.isAlive(
                NODE_INFO.STARTTIME, NODE_INFO.STOPTIME, jobConstants.getJobStartTime(), jobConstants.getJobEndTime()),
            POD_INFO.NAMESPACE.notEqual(kube_system_namespace),
            TimescaleUtils.isAlive(
                POD_INFO.STARTTIME, POD_INFO.STOPTIME, jobConstants.getJobStartTime(), jobConstants.getJobEndTime())))
        .asTable();
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void insertNodePoolAggregated(@NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId,
      @NonNull TotalResourceUsage totalResourceUsage) {
    dslContext.insertInto(NODE_POOL_AGGREGATED)
        .set(NODE_POOL_AGGREGATED.ACCOUNTID, jobConstants.getAccountId())
        .set(NODE_POOL_AGGREGATED.CLUSTERID, nodePoolId.getClusterid())
        .set(NODE_POOL_AGGREGATED.NAME, nodePoolId.getNodepoolname())
        .set(NODE_POOL_AGGREGATED.STARTTIME, toOffsetDateTime(jobConstants.getJobStartTime()))
        .set(NODE_POOL_AGGREGATED.ENDTIME, toOffsetDateTime(jobConstants.getJobEndTime()))
        .set(NODE_POOL_AGGREGATED.SUMCPU, totalResourceUsage.getSumcpu())
        .set(NODE_POOL_AGGREGATED.SUMMEMORY, totalResourceUsage.getSummemory())
        .set(NODE_POOL_AGGREGATED.MAXCPU, totalResourceUsage.getMaxcpu())
        .set(NODE_POOL_AGGREGATED.MAXMEMORY, totalResourceUsage.getMaxmemory())
        .onConflictOnConstraint(Keys.NODE_POOL_AGGREGATED_UNIQUE_RECORD_INDEX)
        .doUpdate()
        .set(NODE_POOL_AGGREGATED.SUMCPU, totalResourceUsage.getSumcpu())
        .set(NODE_POOL_AGGREGATED.SUMMEMORY, totalResourceUsage.getSummemory())
        .set(NODE_POOL_AGGREGATED.MAXCPU, totalResourceUsage.getMaxcpu())
        .set(NODE_POOL_AGGREGATED.MAXMEMORY, totalResourceUsage.getMaxmemory())
        .set(NODE_POOL_AGGREGATED.UPDATEDAT, offsetDateTimeNow())
        .execute();
  }

  @RetryOnException(
      retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION, retryOn = {DataAccessException.class})
  public List<String>
  getDistinctStringValues(String accountId, Condition condition, Field<?> tableField, Table<?> jooqTable) {
    condition = ((TableField<? extends Record, String>) jooqTable.field(ACCOUNT_ID)).eq(accountId).and(condition);

    return dslContext.selectDistinct(tableField).from(jooqTable).where(condition).fetchInto(String.class);
  }

  @NonNull
  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public TotalResourceUsage aggregateTotalResourceRequirement(
      String accountId, NodePoolId nodePoolId, OffsetDateTime startTime, OffsetDateTime endTime) {
    return dslContext
        .select(max(NODE_POOL_AGGREGATED.SUMCPU).as(SUMCPU), max(NODE_POOL_AGGREGATED.SUMMEMORY).as(SUMMEMORY),
            max(NODE_POOL_AGGREGATED.MAXCPU).as(MAXCPU), max(NODE_POOL_AGGREGATED.MAXMEMORY).as(MAXMEMORY))
        .from(NODE_POOL_AGGREGATED)
        .where(NODE_POOL_AGGREGATED.ACCOUNTID.eq(accountId),
            NODE_POOL_AGGREGATED.CLUSTERID.eq(nodePoolId.getClusterid()),
            NODE_POOL_AGGREGATED.NAME.eq(nodePoolId.getNodepoolname()),
            TimescaleUtils.isAlive(NODE_POOL_AGGREGATED.STARTTIME, NODE_POOL_AGGREGATED.ENDTIME, toEpocMilli(startTime),
                toEpocMilli(endTime)))
        .fetchOneInto(TotalResourceUsage.class);
  }

  @NonNull
  public K8sServiceProvider getServiceProvider(JobConstants jobConstants, NodePoolId nodePoolId) {
    InstanceData instanceData = hPersistence.createQuery(InstanceData.class)
                                    .filter(InstanceDataKeys.accountId, jobConstants.getAccountId())
                                    .filter(InstanceDataKeys.clusterId, nodePoolId.getClusterid())
                                    .filter(InstanceDataKeys.instanceType, InstanceType.K8S_NODE)
                                    // currently we are only computing recommendation for non-null node_pool_name
                                    .filter(InstanceDataKeys.metaData + "." + InstanceMetaDataConstants.NODE_POOL_NAME,
                                        nodePoolId.getNodepoolname())
                                    .get();

    try {
      Map<String, String> metaData = instanceData.getMetaData();
      String region = metaData.get(InstanceMetaDataConstants.REGION);
      String instanceFamily = metaData.get(InstanceMetaDataConstants.INSTANCE_FAMILY);
      InstanceCategory instanceCategory =
          InstanceCategory.valueOf(metaData.get(InstanceMetaDataConstants.INSTANCE_CATEGORY));
      CloudProvider cloudProvider =
          CloudProvider.fromCloudProviderName(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER));

      if (!ImmutableList.of(CloudProvider.AZURE, CloudProvider.AWS, CloudProvider.GCP).contains(cloudProvider)) {
        return defaultK8sServiceProvider();
      }

      int nodeCount = getNodeCount(jobConstants, nodePoolId);

      return K8sServiceProvider.builder()
          .region(region)
          .instanceFamily(instanceFamily)
          .nodeCount(nodeCount)
          .cloudProvider(cloudProvider)
          .instanceCategory(instanceCategory)
          .build();

    } catch (Exception ex) {
      log.error("Error reading instanceData metadata, returning default", ex);
      return defaultK8sServiceProvider();
    }
  }

  private static K8sServiceProvider defaultK8sServiceProvider() {
    return K8sServiceProvider.builder()
        .region("us-west1")
        .cloudProvider(CloudProvider.GCP)
        .instanceCategory(InstanceCategory.ON_DEMAND)
        // with null instanceFamily we can't calculate monthlyCost but we can still recommend NodePool
        .instanceFamily(null)
        .build();
  }

  public String insertNodeRecommendationResponse(JobConstants jobConstants, NodePoolId nodePoolId,
      RecommendClusterRequest recommendClusterRequest, K8sServiceProvider serviceProvider,
      RecommendationResponse recommendation) {
    Query<K8sNodeRecommendation> query =
        hPersistence.createQuery(K8sNodeRecommendation.class)
            .filter(K8sNodeRecommendationKeys.accountId, jobConstants.getAccountId())
            .filter(K8sNodeRecommendationKeys.nodePoolId + "." + NodePoolIdKeys.clusterid, nodePoolId.getClusterid())
            .filter(
                K8sNodeRecommendationKeys.nodePoolId + "." + NodePoolIdKeys.nodepoolname, nodePoolId.getNodepoolname());

    UpdateOperations<K8sNodeRecommendation> updateOperations =
        hPersistence.createUpdateOperations(K8sNodeRecommendation.class)
            .set(K8sNodeRecommendationKeys.accountId, jobConstants.getAccountId())
            .set(K8sNodeRecommendationKeys.nodePoolId, nodePoolId)
            .set(K8sNodeRecommendationKeys.recommendation, recommendation)
            .set(K8sNodeRecommendationKeys.recommendClusterRequest, recommendClusterRequest)
            .set(K8sNodeRecommendationKeys.currentServiceProvider, serviceProvider);

    return hPersistence.upsert(query, updateOperations, HPersistence.upsertReturnNewOptions).getUuid();
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void upsertCeRecommendation(@NonNull String uuid, @NonNull ResourceId workloadId, String clusterName,
      @Nullable Double monthlyCost, @Nullable Double monthlySaving, boolean shouldShowRecommendation,
      @NonNull Instant lastReceivedUntilAt) {
    dslContext.insertInto(CE_RECOMMENDATIONS)
        .set(CE_RECOMMENDATIONS.ACCOUNTID, workloadId.getAccountId())
        .set(CE_RECOMMENDATIONS.ID, uuid)
        .set(CE_RECOMMENDATIONS.CLUSTERNAME, clusterName)
        .set(CE_RECOMMENDATIONS.NAME, workloadId.getName())
        .set(CE_RECOMMENDATIONS.NAMESPACE, workloadId.getNamespace())
        .set(CE_RECOMMENDATIONS.RESOURCETYPE, ResourceType.WORKLOAD.name())
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, monthlyCost)
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, monthlySaving)
        .set(CE_RECOMMENDATIONS.ISVALID, shouldShowRecommendation)
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT, toOffsetDateTime(lastReceivedUntilAt))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .onConflictOnConstraint(CE_RECOMMENDATIONS.getPrimaryKey())
        .doUpdate()
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, monthlyCost)
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, monthlySaving)
        .set(CE_RECOMMENDATIONS.ISVALID, shouldShowRecommendation)
        .set(CE_RECOMMENDATIONS.CLUSTERNAME, clusterName) // for updating older rows having clusterId instead
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT, toOffsetDateTime(lastReceivedUntilAt))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .execute();
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void upsertCeRecommendation(String entityUuid, @NonNull JobConstants jobConstants,
      @NonNull NodePoolId nodePoolId, String clusterName, @NonNull RecommendationOverviewStats stats,
      Instant lastReceivedUntilAt) {
    dslContext.insertInto(CE_RECOMMENDATIONS)
        .set(CE_RECOMMENDATIONS.ACCOUNTID, jobConstants.getAccountId())
        .set(CE_RECOMMENDATIONS.ID, entityUuid)
        .set(CE_RECOMMENDATIONS.CLUSTERNAME, clusterName)
        .set(CE_RECOMMENDATIONS.NAME, nodePoolId.getNodepoolname())
        .set(CE_RECOMMENDATIONS.RESOURCETYPE, ResourceType.NODE_POOL.name())
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, stats.getTotalMonthlyCost())
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, stats.getTotalMonthlySaving())
        .set(CE_RECOMMENDATIONS.ISVALID, true)
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT, toOffsetDateTime(lastReceivedUntilAt))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .onConflictOnConstraint(CE_RECOMMENDATIONS.getPrimaryKey())
        .doUpdate()
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, stats.getTotalMonthlyCost())
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, stats.getTotalMonthlySaving())
        .set(CE_RECOMMENDATIONS.ISVALID, true)
        .set(CE_RECOMMENDATIONS.CLUSTERNAME, clusterName) // for updating older rows having clusterId instead
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT, toOffsetDateTime(lastReceivedUntilAt))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .execute();
  }

  /**
   * maximum overlapping problem with given start and end time, but in pure PSQL
   * https://stackoverflow.com/questions/66416245/postgresql-count-max-number-of-concurrent-user-sessions-per-hour
   */
  private int getNodeCount(@NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId) {
    final Condition condition = TimescaleUtils.isAlive(DSL.field(NODE_INFO.STARTTIME.getName(), OffsetDateTime.class),
        DSL.field(NODE_INFO.STOPTIME.getName(), OffsetDateTime.class), jobConstants.getJobStartTime(),
        jobConstants.getJobEndTime());

    final String query =
        "SELECT max(q.num_overlaps) from( SELECT v.ts, sum(sum(v.inc)) OVER (order by v.ts) AS num_overlaps FROM "
        + NODE_INFO.getName() + " n CROSS JOIN lateral (values (" + NODE_INFO.STARTTIME.getName() + ", 1), ("
        + NODE_INFO.STOPTIME.getName() + ", -1)) v(ts, inc) WHERE " + NODE_INFO.ACCOUNTID.getName() + " = '"
        + jobConstants.getAccountId() + "' AND " + NODE_INFO.CLUSTERID.getName() + " = '" + nodePoolId.getClusterid()
        + "' AND " + NODE_INFO.NODEPOOLNAME.getName() + " = '" + nodePoolId.getNodepoolname() + "' AND "
        + condition.toString() + " GROUP BY v.ts ) q;";

    return TimescaleUtils.retryRun(() -> dslContext.fetchOne(query).into(int.class));
  }

  public Optional<K8sNodeRecommendation> fetchNodeRecommendationById(
      @NonNull String accountIdentifier, @NonNull String uuid) {
    return Optional.ofNullable(hPersistence.createQuery(K8sNodeRecommendation.class, excludeValidate)
                                   .filter(K8sNodeRecommendationKeys.accountId, accountIdentifier)
                                   .filter(K8sNodeRecommendationKeys.uuid, new ObjectId(uuid))
                                   .get());
  }

  public int fetchRecommendationsCount(@NonNull String accountId, Condition condition) {
    Condition nonNullCondition = firstNonNull(condition, DSL.noCondition());
    return TimescaleUtils.retryRun(()
                                       -> dslContext.fetchCount(CE_RECOMMENDATIONS,
                                           CE_RECOMMENDATIONS.ACCOUNTID.eq(accountId).and(nonNullCondition)));
  }
}
