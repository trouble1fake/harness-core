/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.aggregator.ACLUtils.buildACL;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.Principal;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ResourceGroupChangeConsumerImpl implements ChangeConsumer<ResourceGroupDBO> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ResourceGroupRepository resourceGroupRepository;
  private final ExecutorService executorService;
  private final ChangeConsumerService changeConsumerService;

  public ResourceGroupChangeConsumerImpl(ACLRepository aclRepository, RoleAssignmentRepository roleAssignmentRepository,
      ResourceGroupRepository resourceGroupRepository, String executorServiceSuffix,
      ChangeConsumerService changeConsumerService) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.resourceGroupRepository = resourceGroupRepository;
    this.changeConsumerService = changeConsumerService;
    String changeConsumerThreadFactory =
        String.format("%s-resource-group-change-consumer", executorServiceSuffix) + "-%d";
    // Number of threads = Number of Available Cores * (1 + (Wait time / Service time) )
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat(changeConsumerThreadFactory).build());
  }

  @Override
  public void consumeUpdateEvent(String id, ResourceGroupDBO updatedResourceGroup) {
    if (updatedResourceGroup.getResourceSelectors() == null && updatedResourceGroup.getFullScopeSelected() == null) {
      return;
    }

    Optional<ResourceGroupDBO> resourceGroup = resourceGroupRepository.findById(id);
    if (!resourceGroup.isPresent()) {
      return;
    }

    Criteria criteria =
        Criteria.where(RoleAssignmentDBOKeys.resourceGroupIdentifier).is(resourceGroup.get().getIdentifier());
    if (isNotEmpty(resourceGroup.get().getScopeIdentifier())) {
      criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(resourceGroup.get().getScopeIdentifier());
    }

    List<ReProcessRoleAssignmentOnResourceGroupUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.unpaged())
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new ReProcessRoleAssignmentOnResourceGroupUpdateTask(
                         aclRepository, changeConsumerService, roleAssignment, resourceGroup.get()))
            .collect(Collectors.toList());

    long numberOfACLsCreated = 0;
    long numberOfACLsDeleted = 0;

    try {
      for (Future<Result> future : executorService.invokeAll(tasksToExecute)) {
        Result result = future.get();
        numberOfACLsCreated += result.getNumberOfACLsCreated();
        numberOfACLsDeleted += result.getNumberOfACLsDeleted();
      }
    } catch (ExecutionException ex) {
      throw new GeneralException("", ex.getCause());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new GeneralException("", ex);
    }

    log.info("Number of ACLs created: {}", numberOfACLsCreated);
    log.info("Number of ACLs deleted: {}", numberOfACLsDeleted);
  }

  @Override
  public void consumeDeleteEvent(String id) {
    // No need to process separately. Would be processed indirectly when associated role bindings will be deleted
  }

  @Override
  public void consumeCreateEvent(String id, ResourceGroupDBO createdEntity) {
    // we do not consume create event
  }

  private static class ReProcessRoleAssignmentOnResourceGroupUpdateTask implements Callable<Result> {
    private final ACLRepository aclRepository;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final ResourceGroupDBO updatedResourceGroup;
    private final ChangeConsumerService changeConsumerService;

    private ReProcessRoleAssignmentOnResourceGroupUpdateTask(ACLRepository aclRepository,
        ChangeConsumerService changeConsumerService, RoleAssignmentDBO roleAssignment,
        ResourceGroupDBO updatedResourceGroup) {
      this.aclRepository = aclRepository;
      this.changeConsumerService = changeConsumerService;
      this.roleAssignmentDBO = roleAssignment;
      this.updatedResourceGroup = updatedResourceGroup;
    }

    @Override
    public Result call() {
      Set<String> existingResourceSelectors =
          Sets.newHashSet(aclRepository.getDistinctResourceSelectorsInACLs(roleAssignmentDBO.getId()));
      Set<String> newResourceSelectors = new HashSet<>();
      if (updatedResourceGroup.isFullScopeSelected()) {
        newResourceSelectors.add("/*/*");
      } else if (updatedResourceGroup.getResourceSelectors() != null) {
        newResourceSelectors = updatedResourceGroup.getResourceSelectors();
      }

      Set<String> resourceSelectorsRemovedFromResourceGroup =
          Sets.difference(existingResourceSelectors, newResourceSelectors);
      Set<String> resourceSelectorsAddedToResourceGroup =
          Sets.difference(newResourceSelectors, existingResourceSelectors);

      long numberOfACLsDeleted = aclRepository.deleteByRoleAssignmentIdAndResourceSelectors(
          roleAssignmentDBO.getId(), resourceSelectorsRemovedFromResourceGroup);

      Set<String> existingPermissions =
          Sets.newHashSet(aclRepository.getDistinctPermissionsInACLsForRoleAssignment(roleAssignmentDBO.getId()));
      Set<String> existingPrincipals =
          Sets.newHashSet(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(roleAssignmentDBO.getId()));
      PrincipalType principalType =
          USER_GROUP.equals(roleAssignmentDBO.getPrincipalType()) ? USER : roleAssignmentDBO.getPrincipalType();

      long numberOfACLsCreated = 0;
      List<ACL> aclsToCreate = new ArrayList<>();

      if (existingPermissions.isEmpty() || existingPrincipals.isEmpty()) {
        aclsToCreate.addAll(changeConsumerService.getAClsForRoleAssignment(roleAssignmentDBO));
      } else {
        resourceSelectorsAddedToResourceGroup.forEach(resourceSelector
            -> existingPrincipals.forEach(principalIdentifier
                -> existingPermissions.forEach(permissionIdentifier
                    -> aclsToCreate.add(buildACL(permissionIdentifier, Principal.of(principalType, principalIdentifier),
                        roleAssignmentDBO, resourceSelector)))));
      }
      numberOfACLsCreated += aclRepository.insertAllIgnoringDuplicates(aclsToCreate);

      return new Result(numberOfACLsCreated, numberOfACLsDeleted);
    }
  }
}
