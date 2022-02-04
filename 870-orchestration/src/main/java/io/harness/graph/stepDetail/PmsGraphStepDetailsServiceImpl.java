/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionDetailsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.beans.stepDetail.NodeExecutionsInfo.NodeExecutionsInfoKeys;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.observer.Subject;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.repositories.stepDetail.NodeExecutionsInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PmsGraphStepDetailsServiceImpl implements PmsGraphStepDetailsService {
  @Inject NodeExecutionsInfoRepository nodeExecutionsInfoRepository;
  @Inject @Getter private final Subject<StepDetailsUpdateObserver> stepDetailsUpdateObserverSubject = new Subject<>();
  @Inject private MongoTemplate mongoTemplate;

  @Override
  public void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name) {
    Update update = new Update().addToSet(NodeExecutionsInfoKeys.nodeExecutionDetailsInfoList,
        NodeExecutionDetailsInfo.builder().name(name).stepDetails(stepDetails).build());
    Criteria criteria = Criteria.where(NodeExecutionsInfoKeys.nodeExecutionId).is(nodeExecutionId);
    mongoTemplate.findAndModify(new Query(criteria), update, NodeExecutionsInfo.class);
    stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepDetailsUpdate,
        StepDetailsUpdateInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId).build());
  }

  @Override
  public void addStepInputs(String nodeExecutionId, String planExecutionId, PmsStepParameters stepParameters) {
    NodeExecutionsInfo nodeExecutionsInfo = NodeExecutionsInfo.builder()
                                                .nodeExecutionId(nodeExecutionId)
                                                .planExecutionId(planExecutionId)
                                                .resolvedInputs(stepParameters)
                                                .build();
    if (!nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId).isPresent()) {
      nodeExecutionsInfoRepository.save(nodeExecutionsInfo);
      stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
          StepDetailsUpdateInfo.builder().nodeExecutionId(nodeExecutionId).planExecutionId(planExecutionId).build());
    }
  }

  @Override
  public PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId) {
    Optional<NodeExecutionsInfo> nodeExecutionsInfo =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    if (nodeExecutionsInfo.isPresent()) {
      return nodeExecutionsInfo.get().getResolvedInputs();
    } else {
      log.warn("Could not find nodeExecutionsInfo with the given nodeExecutionId: " + nodeExecutionId);
      return new PmsStepParameters(new HashMap<>());
    }
  }

  @Override
  public Map<String, PmsStepDetails> getStepDetails(String planExecutionId, String nodeExecutionId) {
    Optional<NodeExecutionsInfo> nodeExecutionsInfo =
        nodeExecutionsInfoRepository.findByNodeExecutionId(nodeExecutionId);
    return nodeExecutionsInfo
        .map(executionsInfo
            -> executionsInfo.getNodeExecutionDetailsInfoList().stream().collect(
                Collectors.toMap(NodeExecutionDetailsInfo::getName, NodeExecutionDetailsInfo::getStepDetails)))
        .orElseGet(HashMap::new);
  }

  @Override
  public void copyStepDetailsForRetry(
      String planExecutionId, String originalNodeExecutionId, String newNodeExecutionId) {
    Optional<NodeExecutionsInfo> originalStepDetailInstances =
        nodeExecutionsInfoRepository.findByNodeExecutionId(originalNodeExecutionId);
    if (originalStepDetailInstances.isPresent()) {
      NodeExecutionsInfo originalExecutionInfo = originalStepDetailInstances.get();
      NodeExecutionsInfo newNodeExecutionsInfo =
          NodeExecutionsInfo.builder()
              .nodeExecutionDetailsInfoList(originalExecutionInfo.getNodeExecutionDetailsInfoList())
              .nodeExecutionId(newNodeExecutionId)
              .planExecutionId(planExecutionId)
              .resolvedInputs(originalExecutionInfo.getResolvedInputs())
              .build();
      nodeExecutionsInfoRepository.save(newNodeExecutionsInfo);
      stepDetailsUpdateObserverSubject.fireInform(StepDetailsUpdateObserver::onStepInputsAdd,
          StepDetailsUpdateInfo.builder().nodeExecutionId(newNodeExecutionId).planExecutionId(planExecutionId).build());
    }
  }
}
