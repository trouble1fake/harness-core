/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskData.TaskDataKeys;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.HDelegateTask;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

@Data
@Builder
@EqualsAndHashCode(exclude = {"uuid", "createdAt", "lastUpdatedAt", "validUntil"})
@Entity(value = "delegateTasks", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "DelegateTaskKeys")
@TargetModule(HarnessModule._920_DELEGATE_SERVICE_BEANS)
public class DelegateTask
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, HDelegateTask {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("index")
                 .field(DelegateTaskKeys.status)
                 .field(DelegateTaskKeys.expiry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("rebroadcast")
                 .field(DelegateTaskKeys.version)
                 .field(DelegateTaskKeys.status)
                 .field(DelegateTaskKeys.delegateId)
                 .field(DelegateTaskKeys.nextBroadcast)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("pulling")
                 .field(DelegateTaskKeys.accountId)
                 .field(DelegateTaskKeys.status)
                 .field(DelegateTaskKeys.data_async)
                 .field(DelegateTaskKeys.expiry)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("rebroadcast1")
                 .field(DelegateTaskKeys.status)
                 .field(DelegateTaskKeys.delegateId)
                 .field(DelegateTaskKeys.nextBroadcast)
                 .build())
        .build();
  }

  private static final Long DEFAULT_FORCE_EXECUTE_TIMEOUT = Duration.ofSeconds(5).toMillis();
  public static final Long DELEGATE_QUEUE_TIMEOUT = Duration.ofSeconds(6).toMillis();

  @NotNull private TaskData data;
  private List<ExecutionCapability> executionCapabilities;

  @Id private String uuid;
  @NotEmpty private String accountId;
  private String driverId;

  private DelegateTaskRank rank;

  private String description;
  private boolean selectionLogsTrackingEnabled;

  private String workflowExecutionId;

  @Singular private Map<String, String> setupAbstractions;

  /**
   * This field is intended to be used by Task owners to prepare key-value pairs which should represent the baseLogKey
   * to be used for log streaming. If any other sub-step, like command unit, exists and has to be logged in a dedicated
   * log stream, command unit identifier will be appended to the base key, by the logger implementation based on the
   * command unit identifier passed by the task that is being executed. SortedMap is used, so that the same order is
   * guarantied every time when the key is being built, on manager or delegate side.
   *
   * Convention for key generation will be the following:
   *
   *  [mapKey]:[mapvalue]-[mapKey]:[mapvalue]-...
   *
   *  In case there is a command unit, it should be appended to the end of the base key:
   *
   *  [mapKey]:[mapvalue]-[mapKey]:[mapvalue]-commandUnit:[commandUnitIdentifier]
   *
   * Example:
   *     key: pipelineId, value: 1111
   *     key: stageId, value: 2222
   *     key: stepId, value: 3333
   *
   * Value of the key would be: pipelineId:1111-stageId:2222-stepId:3333
   *
   * In case there is a command unit that requires a dedicated log stream, manager(while reading logs) and logger
   * implementation(while writing logs) should concatenate the commandUnit part to the end:
   *
   * Value of the key would be: pipelineId:1111-stageId:2222-stepId:3333-commandUnit:XYZ
   *
   */
  private LinkedHashMap<String, String> logStreamingAbstractions;

  private String version;

  // Please use SelectorCapability instead.
  @Deprecated private List<String> tags;

  // This extra field is pointless, we should use the task uuid.
  @Deprecated private String waitId;

  private long createdAt;
  private long lastUpdatedAt;

  private Status status;

  private Long validationStartedAt;
  private Set<String> validatingDelegateIds;
  private Set<String> validationCompleteDelegateIds;

  private String delegateId;
  private String delegateInstanceId;
  private String preAssignedDelegateId;
  private Set<String> alreadyTriedDelegates;

  private Long lastBroadcastAt;
  private int broadcastCount;
  private long nextBroadcast;
  private boolean forceExecute;
  private int broadcastRound;

  private long expiry;

  private LinkedList<String> eligibleToExecuteDelegateIds;
  @Transient private List<String> broadcastToDelegateIds;

  @Transient private List<String> taskActivityLogs;

  @FdTtlIndex @Default private Date validUntil = Date.from(OffsetDateTime.now().plusDays(2).toInstant());

  public Long fetchExtraTimeoutForForceExecution() {
    if (forceExecute) {
      return DEFAULT_FORCE_EXECUTE_TIMEOUT;
    }
    return 0L;
  }

  // Following getters, setters have been added temporarily because of backward compatibility

  public String calcDescription() {
    if (isEmpty(description)) {
      return data.getTaskType();
    }
    return description;
  }

  public enum Status {
    QUEUED,
    STARTED,
    ERROR,
    ABORTED,
    PARKED;

    private static Set<Status> finalStatuses = EnumSet.of(ERROR, ABORTED);
    private static Set<Status> runningStatuses = EnumSet.of(QUEUED, STARTED);

    public static Set<Status> finalStatuses() {
      return finalStatuses;
    }

    public static boolean isFinalStatus(Status status) {
      return status != null && finalStatuses.contains(status);
    }

    public static Set<Status> runningStatuses() {
      return runningStatuses;
    }

    public static boolean isRunningStatus(Status status) {
      return status != null && runningStatuses.contains(status);
    }
  }

  @UtilityClass
  public static final class DelegateTaskKeys {
    public static final String data_parameters = data + "." + TaskDataKeys.parameters;
    public static final String data_taskType = data + "." + TaskDataKeys.taskType;
    public static final String data_timeout = data + "." + TaskDataKeys.timeout;
    public static final String data_async = data + "." + TaskDataKeys.async;
  }
}
