/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.anomaly;

import static io.harness.timescaledb.Tables.ANOMALIES;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.annotations.retry.RetryOnException;
import io.harness.timescaledb.tables.pojos.Anomalies;
import io.harness.timescaledb.tables.records.AnomaliesRecord;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sun.istack.internal.Nullable;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.SelectFinalStep;
import org.jooq.impl.DSL;

@Slf4j
@Singleton
public class AnomalyDao {
  @Inject private DSLContext dslContext;

  private static final int RETRY_COUNT = 3;
  private static final int SLEEP_DURATION = 100;

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public List<Anomalies> fetchAnomalies(@NonNull String accountId, @Nullable Condition condition,
      @NonNull List<OrderField<?>> orderFields, @NonNull Integer offset, @NonNull Integer limit) {
    SelectFinalStep<AnomaliesRecord> finalStep =
        dslContext.selectFrom(ANOMALIES)
            .where(ANOMALIES.ACCOUNTID.eq(accountId).and(firstNonNull(condition, DSL.noCondition())))
            .orderBy(orderFields)
            .offset(offset)
            .limit(limit);
    log.info("Anomaly Query: {}", finalStep.getQuery().toString());
    return finalStep.fetchInto(Anomalies.class);
  }
}
