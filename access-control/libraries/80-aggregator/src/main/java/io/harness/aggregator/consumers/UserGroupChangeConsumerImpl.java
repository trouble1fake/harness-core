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

import io.harness.accesscontrol.Principal;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
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
public class UserGroupChangeConsumerImpl implements ChangeConsumer<UserGroupDBO> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final UserGroupRepository userGroupRepository;
  private final ExecutorService executorService;
  private final ChangeConsumerService changeConsumerService;
  private final UserGroupCRUDEventHandler userGroupCRUDEventHandler;

  public UserGroupChangeConsumerImpl(ACLRepository aclRepository, RoleAssignmentRepository roleAssignmentRepository,
      UserGroupRepository userGroupRepository, String executorServiceSuffix,
      ChangeConsumerService changeConsumerService, UserGroupCRUDEventHandler userGroupCRUDEventHandler) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.userGroupRepository = userGroupRepository;
    this.userGroupCRUDEventHandler = userGroupCRUDEventHandler;
    String changeConsumerThreadFactory = String.format("%s-user-group-change-consumer", executorServiceSuffix) + "-%d";
    // Number of threads = Number of Available Cores * (1 + (Wait time / Service time) )
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat(changeConsumerThreadFactory).build());
    this.changeConsumerService = changeConsumerService;
  }

  @Override
  public void consumeUpdateEvent(String id, UserGroupDBO updatedUserGroup) {
    if (updatedUserGroup.getUsers() == null) {
      return;
    }

    Optional<UserGroupDBO> userGroup = userGroupRepository.findById(id);
    if (!userGroup.isPresent()) {
      return;
    }

    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.principalType)
                            .is(USER_GROUP)
                            .and(RoleAssignmentDBOKeys.principalIdentifier)
                            .is(userGroup.get().getIdentifier())
                            .and(RoleAssignmentDBOKeys.scopeIdentifier)
                            .is(userGroup.get().getScopeIdentifier());
    List<ReProcessRoleAssignmentOnUserGroupUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.unpaged())
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new ReProcessRoleAssignmentOnUserGroupUpdateTask(
                         aclRepository, changeConsumerService, roleAssignment, userGroup.get()))
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

    userGroupCRUDEventHandler.handleUserGroupUpdate(userGroup.get());

    log.info("Number of ACLs created: {}", numberOfACLsCreated);
    log.info("Number of ACLs deleted: {}", numberOfACLsDeleted);
  }

  @Override
  public void consumeDeleteEvent(String id) {
    // No need to process separately. Would be processed indirectly when associated role bindings will be deleted
  }

  @Override
  public void consumeCreateEvent(String id, UserGroupDBO createdEntity) {
    // we do not consume create event
  }

  private static class ReProcessRoleAssignmentOnUserGroupUpdateTask implements Callable<Result> {
    private final ACLRepository aclRepository;
    private final ChangeConsumerService changeConsumerService;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final UserGroupDBO updatedUserGroup;

    private ReProcessRoleAssignmentOnUserGroupUpdateTask(ACLRepository aclRepository,
        ChangeConsumerService changeConsumerService, RoleAssignmentDBO roleAssignment, UserGroupDBO updatedUserGroup) {
      this.aclRepository = aclRepository;
      this.changeConsumerService = changeConsumerService;
      this.roleAssignmentDBO = roleAssignment;
      this.updatedUserGroup = updatedUserGroup;
    }

    @Override
    public Result call() {
      Set<String> existingPrincipals = Sets.newHashSet(
          Sets.newHashSet(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(roleAssignmentDBO.getId())));
      Set<String> principalsAddedToUserGroup =
          Sets.difference(updatedUserGroup.getUsers() == null ? Collections.emptySet() : updatedUserGroup.getUsers(),
              existingPrincipals);
      Set<String> principalRemovedFromUserGroup = Sets.difference(existingPrincipals,
          updatedUserGroup.getUsers() == null ? Collections.emptySet() : updatedUserGroup.getUsers());

      long numberOfACLsDeleted =
          aclRepository.deleteByRoleAssignmentIdAndPrincipals(roleAssignmentDBO.getId(), principalRemovedFromUserGroup);

      Set<String> existingResourceSelectors =
          Sets.newHashSet(aclRepository.getDistinctResourceSelectorsInACLs(roleAssignmentDBO.getId()));
      Set<String> existingPermissions =
          Sets.newHashSet(aclRepository.getDistinctPermissionsInACLsForRoleAssignment(roleAssignmentDBO.getId()));

      long numberOfACLsCreated = 0;
      List<ACL> aclsToCreate = new ArrayList<>();

      if (existingResourceSelectors.isEmpty() || existingPermissions.isEmpty()) {
        aclsToCreate.addAll(changeConsumerService.getAClsForRoleAssignment(roleAssignmentDBO));
      } else {
        existingPermissions.forEach(permissionIdentifier
            -> principalsAddedToUserGroup.forEach(principalIdentifier
                -> existingResourceSelectors.forEach(resourceSelector
                    -> aclsToCreate.add(buildACL(permissionIdentifier, Principal.of(USER, principalIdentifier),
                        roleAssignmentDBO, resourceSelector)))));
      }
      numberOfACLsCreated += aclRepository.insertAllIgnoringDuplicates(aclsToCreate);

      return new Result(numberOfACLsCreated, numberOfACLsDeleted);
    }
  }
}
