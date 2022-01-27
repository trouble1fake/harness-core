/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.StepDetailInstance;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.observer.Subject;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.repositories.stepDetail.StepDetailsInstanceRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PmsGraphStepDetailsServiceImpl implements PmsGraphStepDetailsService {
  @Inject StepDetailsInstanceRepository stepDetailsInstanceRepository;
  @Inject @Getter private final Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject = new Subject<>();
  private static final String INPUT_NAME = "pmsStepInputs";

  @Override
  public void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name) {
    StepDetailInstance stepDetailInstance = StepDetailInstance.builder()
                                                .name(name)
                                                .stepDetails(stepDetails)
                                                .planExecutionId(planExecutionId)
                                                .nodeExecutionId(nodeExecutionId)
                                                .build();
    stepDetailsInstanceRepository.save(stepDetailInstance);
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepDetailsUpdate,
        StepDetailsUpdateInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId).build());
  }

  @Override
  public void addStepInputs(String nodeExecutionId, String planExecutionId, PmsStepParameters stepParameters) {
    StepDetailInstance stepDetailInstance = StepDetailInstance.builder()
                                                .name(INPUT_NAME)
                                                .resolvedInputs(stepParameters)
                                                .planExecutionId(planExecutionId)
                                                .nodeExecutionId(nodeExecutionId)
                                                .build();
    stepDetailsInstanceRepository.save(stepDetailInstance);
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
        StepDetailsUpdateInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId).build());
  }

  @Override
  public PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId) {
    Optional<StepDetailInstance> stepDetailInstances =
        stepDetailsInstanceRepository.findByNameAndNodeExecutionId(INPUT_NAME, nodeExecutionId);
    return stepDetailInstances.get().getResolvedInputs();
  }

  @Override
  public Map<String, PmsStepDetails> getStepDetails(String planExecutionId, String nodeExecutionId) {
    List<StepDetailInstance> stepDetailInstances = stepDetailsInstanceRepository.findByNodeExecutionId(nodeExecutionId);
    return stepDetailInstances.stream().collect(
        Collectors.toMap(StepDetailInstance::getName, StepDetailInstance::getStepDetails));
  }

  @Override
  public void copyStepDetailsForRetry(
      String planExecutionId, String originalNodeExecutionId, String newNodeExecutionId) {
    List<StepDetailInstance> originalStepDetailInstances =
        stepDetailsInstanceRepository.findByNodeExecutionId(originalNodeExecutionId);
    List<StepDetailInstance> newStepDetailInstance = new ArrayList<>();
    for (StepDetailInstance instance : originalStepDetailInstances) {
      newStepDetailInstance.add(StepDetailInstance.cloneForRetry(instance, planExecutionId, newNodeExecutionId));
    }
    stepDetailsInstanceRepository.saveAll(newStepDetailInstance);
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
        StepDetailsUpdateInfo.builder().nodeExecutionId(newNodeExecutionId).planExecutionId(planExecutionId).build());
  }
}
