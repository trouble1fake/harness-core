/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.pms.pipeline.PipelineEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Getter
@NoArgsConstructor
public class PipelineDeleteEvent implements Event {
  private String orgIdentifier;
  private String accountIdentifier;
  private String projectIdentifier;
  private PipelineEntity pipeline;
  private Boolean isFromGit;

  public PipelineDeleteEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, PipelineEntity pipeline) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipeline = pipeline;
    this.isFromGit = false;
  }

  public PipelineDeleteEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      PipelineEntity pipeline, boolean isFromGit) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.pipeline = pipeline;
    this.isFromGit = isFromGit;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, pipeline.getName());
    return Resource.builder()
        .identifier(pipeline.getIdentifier())
        .type(ResourceTypeConstants.PIPELINE)
        .labels(labels)
        .build();
  }

  public boolean getIsFromGit() {
    if (isFromGit == null) {
      return false;
    }
    return isFromGit;
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return PipelineOutboxEvents.PIPELINE_DELETED;
  }
}
