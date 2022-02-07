/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;

@Data
@Builder(buildMethodName = "unsafeBuild")
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "LogAnalysisClusterKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(value = "logAnalysisClusters", noClassnameStored = true)
@HarnessEntity(exportable = false)
@StoreIn(DbAliases.CVNG)
public final class LogAnalysisCluster implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(LogAnalysisClusterKeys.verificationTaskId)
                 .field(LogAnalysisClusterKeys.isEvicted)
                 .build())
        .build();
  }

  public static class LogAnalysisClusterBuilder {
    public LogAnalysisCluster build() {
      LogAnalysisCluster logAnalysisCluster = unsafeBuild();
      logAnalysisCluster.setCompressedText(logAnalysisCluster.getCompressedText());
      logAnalysisCluster.setText(null);
      return logAnalysisCluster;
    }
  }

  @Id private String uuid;
  @FdIndex private long createdAt;
  @FdIndex private long lastUpdatedAt;
  private String verificationTaskId;
  private Instant analysisStartTime;
  private Instant analysisEndTime;
  @FdIndex private String accountId;
  private long analysisMinute;
  private long label;
  private List<Frequency> frequencyTrend;
  private String text;
  private byte[] compressedText;
  private boolean isEvicted;
  private long firstSeenTime;
  private double x;
  private double y;

  public void compressText() {
    if (isNotEmpty(text)) {
      try {
        setCompressedText(compressString(JsonUtils.asJson(text)));
        setText(null);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public void deCompressText() {
    if (isNotEmpty(compressedText)) {
      try {
        String decompressedText = deCompressString(compressedText);
        setText(JsonUtils.asObject(decompressedText, new TypeReference<String>() {}));
        setCompressedText(null);
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  public String getText() {
    if (Objects.isNull(text) && isNotEmpty(compressedText)) {
      try {
        String decompressedText = deCompressString(compressedText);
        return JsonUtils.asObject(decompressedText, new TypeReference<String>() {});
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
    }
    return text;
  }

  public byte[] getCompressedText() {
    if (isEmpty(compressedText) && isNotEmpty(text)) {
      try {
        return compressString(JsonUtils.asJson(text));
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    return compressedText;
  }

  public List<Frequency> getFrequencyTrend() {
    if (frequencyTrend == null) {
      return new ArrayList<>();
    }
    return frequencyTrend;
  }

  @JsonIgnore @SchemaIgnore @FdTtlIndex private Date validUntil;

  @Data
  @Builder
  public static class Frequency {
    Integer count;
    Long timestamp;
    Double riskScore;
  }

  @PrePersist
  public void updateValidUntil() {
    if (isEvicted) {
      validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());
    }
  }
}
