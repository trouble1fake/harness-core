/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.compliance;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.event.model.EventType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.CustomEnvFilter;
import io.harness.governance.DeploymentFreezeInfo;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.GovernanceFreezeConfig;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.governance.TimeRangeBasedFreezeConfig;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.validation.Validator;

import software.wings.beans.Event.Type;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.governance.GovernanceConfig.GovernanceConfigKeys;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.features.GovernanceFeature;
import software.wings.features.api.AccountId;
import software.wings.features.api.RestrictedApi;
import software.wings.resources.stats.model.TimeRange;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.segment.analytics.messages.TrackMessage;
import com.segment.analytics.messages.TrackMessage.Builder;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * @author rktummala on 02/04/19
 */
@Slf4j
@ValidateOnExecution
@Singleton
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(HarnessTeam.CDC)
public class GovernanceConfigServiceImpl implements GovernanceConfigService {
  private static final long MIN_FREEZE_WINDOW_TIME = 1800000L;
  private static final long MAX_FREEZE_WINDOW_TIME = 31536000000L;

  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private SegmentHelper segmentHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AppService appService;
  @Inject private EnvironmentService environmentService;
  @Inject private DeploymentFreezeActivationHandler freezeActivationHandler;
  @Inject private DeploymentFreezeDeactivationHandler freezeDeactivationHandler;
  @Inject private YamlPushService yamlPushService;
  @Inject private UserGroupService userGroupService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private DeploymentFreezeUtils deploymentFreezeUtils;
  @Inject private ExecutorService executorService;

  @Override
  public GovernanceConfig get(String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Getting Deployment Freeze window");
      GovernanceConfig governanceConfig =
          wingsPersistence.createQuery(GovernanceConfig.class).filter(GovernanceConfigKeys.accountId, accountId).get();
      if (governanceConfig == null) {
        return getDefaultGovernanceConfig(accountId);
      }
      toggleExpiredWindows(governanceConfig, accountId);
      populateWindowsStatus(governanceConfig, accountId);
      return governanceConfig;
    }
  }

  private void populateWindowsStatus(GovernanceConfig governanceConfig, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)
        && isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      governanceConfig.getTimeRangeBasedFreezeConfigs().forEach(
          TimeRangeBasedFreezeConfig::recalculateFreezeWindowState);
    }
  }

  private void toggleExpiredWindows(GovernanceConfig governanceConfig, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)
        && isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      governanceConfig.getTimeRangeBasedFreezeConfigs().forEach(TimeRangeBasedFreezeConfig::toggleExpiredWindowsOff);
    }
  }

  private GovernanceConfig getDefaultGovernanceConfig(String accountId) {
    return GovernanceConfig.builder().accountId(accountId).deploymentFreeze(false).build();
  }

  @Override
  @RestrictedApi(GovernanceFeature.class)
  public GovernanceConfig upsert(@AccountId String accountId, @Nonnull GovernanceConfig governanceConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      boolean newDeploymentFreezeEnabled = featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId);

      log.info("Updating Deployment Freeze window");
      GovernanceConfig oldSetting = get(accountId);

      if (newDeploymentFreezeEnabled) {
        validateDeploymentFreezeInput(governanceConfig.getTimeRangeBasedFreezeConfigs(), accountId, oldSetting);
        resetReadOnlyProperties(governanceConfig.getTimeRangeBasedFreezeConfigs(), accountId, oldSetting);
        checkForWindowActivationAndSendNotification(
            governanceConfig.getTimeRangeBasedFreezeConfigs(), oldSetting.getTimeRangeBasedFreezeConfigs(), accountId);
      }

      Query<GovernanceConfig> query =
          wingsPersistence.createQuery(GovernanceConfig.class).filter(GovernanceConfigKeys.accountId, accountId);

      UpdateOperations<GovernanceConfig> updateOperations =
          wingsPersistence.createUpdateOperations(GovernanceConfig.class)
              .set(GovernanceConfigKeys.deploymentFreeze, governanceConfig.isDeploymentFreeze())
              .set(GovernanceConfigKeys.timeRangeBasedFreezeConfigs, governanceConfig.getTimeRangeBasedFreezeConfigs())
              .set(GovernanceConfigKeys.weeklyFreezeConfigs, governanceConfig.getWeeklyFreezeConfigs());

      User user = UserThreadLocal.get();
      if (null != user) {
        EmbeddedUser embeddedUser = new EmbeddedUser(user.getUuid(), user.getName(), user.getEmail());
        updateOperations.set(GovernanceConfigKeys.lastUpdatedBy, embeddedUser);
      } else {
        log.error("ThreadLocal User is null when trying to update governance config. accountId={}", accountId);
      }

      if (newDeploymentFreezeEnabled) {
        governanceConfig.recalculateNextIterations(GovernanceConfigKeys.nextIterations, true, 0);
        governanceConfig.recalculateNextIterations(GovernanceConfigKeys.nextCloseIterations, true, 0);
        updateOperations.set(GovernanceConfigKeys.nextIterations, governanceConfig.getNextIterations());
        updateOperations.set(GovernanceConfigKeys.nextCloseIterations, governanceConfig.getNextCloseIterations());
      }

      GovernanceConfig updatedSetting =
          wingsPersistence.findAndModify(query, updateOperations, WingsPersistence.upsertReturnNewOptions);

      // push service also adds audit trail, in case of no yaml we add the entry explicitly
      if (newDeploymentFreezeEnabled) {
        yamlPushService.pushYamlChangeSet(
            accountId, oldSetting, updatedSetting, Type.UPDATE, governanceConfig.isSyncFromGit(), false);
      } else {
        auditDeploymentFreeze(accountId, oldSetting, updatedSetting);
      }

      if (newDeploymentFreezeEnabled) {
        freezeDeactivationHandler.wakeup();
        freezeActivationHandler.wakeup();
      }

      if (!ListUtils.isEqualList(
              oldSetting.getTimeRangeBasedFreezeConfigs(), governanceConfig.getTimeRangeBasedFreezeConfigs())) {
        publishToSegment(accountId, user, EventType.BLACKOUT_WINDOW_UPDATED);
      }

      if (!ListUtils.isEqualList(oldSetting.getWeeklyFreezeConfigs(), governanceConfig.getWeeklyFreezeConfigs())) {
        publishToSegment(accountId, user, EventType.BLACKOUT_WINDOW_UPDATED);
      }
      toggleExpiredWindows(updatedSetting, accountId);
      populateWindowsStatus(updatedSetting, accountId);
      return updatedSetting;
    }
  }

  private void checkForWindowActivationAndSendNotification(
      List<TimeRangeBasedFreezeConfig> newWindows, List<TimeRangeBasedFreezeConfig> oldWindows, String accountId) {
    if (isEmpty(newWindows)) {
      return;
    }
    for (TimeRangeBasedFreezeConfig freezeWindow : newWindows) {
      Optional<TimeRangeBasedFreezeConfig> oldMatchingWindow =
          oldWindows.stream().filter(window -> window.getUuid().equals(freezeWindow.getUuid())).findFirst();

      if (!oldMatchingWindow.isPresent()) {
        if (freezeWindow.checkIfActive()) {
          executorService.submit(() -> deploymentFreezeUtils.handleActivationEvent(freezeWindow, accountId));
        }
      } else {
        if (freezeWindow.checkIfActive() && !oldMatchingWindow.get().checkIfActive()) {
          executorService.submit(() -> deploymentFreezeUtils.handleActivationEvent(freezeWindow, accountId));
        } else if (!freezeWindow.checkIfActive() && oldMatchingWindow.get().checkIfActive()) {
          executorService.submit(() -> deploymentFreezeUtils.handleDeActivationEvent(freezeWindow, accountId));
        }
      }
    }
  }

  @Override
  public Map<String, Set<String>> getFrozenEnvIdsForApp(
      String accountId, String appId, GovernanceConfig governanceConfig) {
    if (governanceConfig == null) {
      governanceConfig = get(accountId);
    }
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)) {
      if (isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
        Map<String, Set<String>> envIdsByWindow = new HashMap<>();
        for (TimeRangeBasedFreezeConfig freezeConfig : governanceConfig.getTimeRangeBasedFreezeConfigs()) {
          if (isNotEmpty(freezeConfig.getAppSelections()) && freezeConfig.checkIfActive()) {
            freezeConfig.getAppSelections().forEach(appSelection -> {
              if ((appSelection.getFilterType() == BlackoutWindowFilterType.ALL
                      || (appSelection.getFilterType() == BlackoutWindowFilterType.CUSTOM
                          && ((CustomAppFilter) appSelection).getApps().contains(appId)))
                  && areAllServicesFrozen(appSelection)) {
                envIdsByWindow.merge(freezeConfig.getUuid(),
                    new HashSet<>(getEnvIdsFromAppSelection(appId, appSelection)), (prevEnvSet, newEnvSet) -> {
                      prevEnvSet.addAll(newEnvSet);
                      return prevEnvSet;
                    });
              }
            });
          }
        }
        return envIdsByWindow;
      }
    }
    return Collections.emptyMap();
  }

  private boolean areAllServicesFrozen(ApplicationFilter appSelection) {
    return appSelection.getServiceSelection() == null
        || appSelection.getServiceSelection().getFilterType() == ServiceFilterType.ALL;
  }

  @Override
  public List<GovernanceFreezeConfig> getGovernanceFreezeConfigs(String accountId, List<String> deploymentFreezeIds) {
    GovernanceConfig governanceConfig = get(accountId);
    if (governanceConfig != null && EmptyPredicate.isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
      return governanceConfig.getTimeRangeBasedFreezeConfigs()
          .stream()
          .filter(freeze -> deploymentFreezeIds.contains(freeze.getUuid()))
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  @Override
  public List<String> getEnvIdsFromAppSelection(String appId, ApplicationFilter appSelection) {
    switch (appSelection.getEnvSelection().getFilterType()) {
      case ALL:
        return environmentService.getEnvIdsByApp(appId);
      case ALL_NON_PROD:
        return environmentService.getEnvIdsByAppsAndType(
            Collections.singletonList(appId), EnvironmentType.NON_PROD.name());
      case ALL_PROD:
        return environmentService.getEnvIdsByAppsAndType(Collections.singletonList(appId), EnvironmentType.PROD.name());
      case CUSTOM:
        return ((CustomEnvFilter) appSelection.getEnvSelection()).getEnvironments();
      default:
    }
    return new ArrayList<>();
  }

  @Override
  public List<String> getServiceIdsFromAppSelection(String appId, ApplicationFilter appSelection) {
    switch (appSelection.getServiceSelection().getFilterType()) {
      case ALL:
        return serviceResourceService.findServicesByAppInternal(appId)
            .stream()
            .map(Service::getUuid)
            .collect(Collectors.toList());
      case CUSTOM:
        if (isEmpty(appSelection.getServiceSelection().getServices())) {
          return Collections.emptyList();
        } else {
          return appSelection.getServiceSelection().getServices();
        }
      default:
    }
    return Collections.emptyList();
  }

  @Override
  public DeploymentFreezeInfo getDeploymentFreezeInfo(String accountId) {
    GovernanceConfig governanceConfig = get(accountId);
    if (featureFlagService.isEnabled(FeatureName.NEW_DEPLOYMENT_FREEZE, accountId)) {
      Set<String> allEnvFrozenApps = new HashSet<>();
      Map<String, Set<String>> appEnvs = new HashMap<>();
      // What services are frozen for a environment
      Map<String, Set<String>> envServices = new HashMap<>();

      if (isNotEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
        for (TimeRangeBasedFreezeConfig freezeConfig : governanceConfig.getTimeRangeBasedFreezeConfigs()) {
          if (isNotEmpty(freezeConfig.getAppSelections()) && isActive(freezeConfig)) {
            freezeConfig.getAppSelections().forEach(appSelection -> {
              if (appSelection.getServiceSelection() == null
                  || appSelection.getServiceSelection().getFilterType() == ServiceFilterType.ALL) {
                Map<String, Set<String>> appEnvMap = getAppEnvMapForAppSelection(accountId, appSelection);
                if (isNotEmpty(appEnvMap)) {
                  appEnvMap.forEach((app, envSet) -> appEnvs.merge(app, envSet, (prevEnvSet, newEnvSet) -> {
                    prevEnvSet.addAll(newEnvSet);
                    return prevEnvSet;
                  }));
                }
                checkIfAllEnvAllServiceFrozenAndAdd(appSelection, allEnvFrozenApps, accountId);
              }
            });
          }
        }
      }
      return DeploymentFreezeInfo.builder()
          .freezeAll(governanceConfig.isDeploymentFreeze())
          .allEnvFrozenApps(allEnvFrozenApps)
          .appEnvs(appEnvs)
          .build();
    }
    return DeploymentFreezeInfo.builder()
        .freezeAll(false)
        .allEnvFrozenApps(Collections.emptySet())
        .appEnvs(Collections.emptyMap())
        .build();
  }

  private void checkIfAllEnvAllServiceFrozenAndAdd(
      ApplicationFilter appSelection, Set<String> allEnvFrozenApps, String accountId) {
    // Whole app is frozen when both Env Filters and Service filters are ALL frozen.
    // This also applies to previously created windows which did not have option to add service selection
    if (appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.ALL
        && (appSelection.getServiceSelection() == null
            || appSelection.getServiceSelection().getFilterType() == ServiceFilterType.ALL)) {
      if (appSelection.getFilterType() == BlackoutWindowFilterType.CUSTOM) {
        allEnvFrozenApps.addAll(((CustomAppFilter) appSelection).getApps());
      } else {
        allEnvFrozenApps.addAll(CollectionUtils.emptyIfNull(appService.getAppIdsByAccountId(accountId)));
      }
    }
  }

  // Given an app selection row in a freeze window, this returns a map of frozen environments in each application as
  // specified by it
  private Map<String, Set<String>> getAppEnvMapForAppSelection(String accountId, ApplicationFilter appSelection) {
    if (appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.ALL) {
      return new HashMap<>();
    }
    List<String> appIds = appSelection.getFilterType() == BlackoutWindowFilterType.ALL
        ? appService.getAppIdsByAccountId(accountId)
        : ((CustomAppFilter) appSelection).getApps();
    Map<String, Set<String>> appEnvMap = new HashMap<>();
    switch (appSelection.getEnvSelection().getFilterType()) {
      case ALL:
        break;
      case ALL_NON_PROD:
        appEnvMap = environmentService.getAppIdEnvIdMapByType(new HashSet<>(appIds), EnvironmentType.NON_PROD);
        break;
      case ALL_PROD:
        appEnvMap = environmentService.getAppIdEnvIdMapByType(new HashSet<>(appIds), EnvironmentType.PROD);
        break;
      case CUSTOM:
        CustomEnvFilter customEnvFilter = (CustomEnvFilter) appSelection.getEnvSelection();
        appEnvMap.put(appIds.get(0), new HashSet<>(customEnvFilter.getEnvironments()));
        break;
      default:
        throw new InvalidRequestException("Invalid app selection");
    }
    if (EmptyPredicate.isEmpty(appEnvMap)) {
      log.info("No applications and environments matching the given app selection: {}, environment selection type: {}",
          appSelection.getFilterType(), appSelection.getEnvSelection().getFilterType());
    }
    return appEnvMap;
  }

  // Function to check if freeze window is turned on and is effective for the current time
  private boolean isActive(TimeRangeBasedFreezeConfig freezeConfig) {
    if (!freezeConfig.isApplicable()) {
      return false;
    }
    long currentTime = System.currentTimeMillis();
    log.info("Window id: {}, Current time: {}, from: {}, to: {}", freezeConfig.getUuid(), currentTime,
        freezeConfig.getTimeRange().getFrom(), freezeConfig.getTimeRange().getTo());
    return currentTime <= freezeConfig.getTimeRange().getTo() && currentTime >= freezeConfig.getTimeRange().getFrom();
  }

  private void validateDeploymentFreezeInput(List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigs,
      String accountId, GovernanceConfig oldGovernanceConfig) {
    if (EmptyPredicate.isEmpty(timeRangeBasedFreezeConfigs)) {
      return;
    }

    Set<String> freezeNameSet = new HashSet<>();
    timeRangeBasedFreezeConfigs.stream().map(GovernanceFreezeConfig::getName).filter(Objects::nonNull).forEach(name -> {
      if (freezeNameSet.contains(name)) {
        throw new InvalidRequestException(format("Duplicate name %s", name), WingsException.USER);
      }
      freezeNameSet.add(name);
    });

    timeRangeBasedFreezeConfigs.stream()
        .filter(freeze -> EmptyPredicate.isNotEmpty(freeze.getAppSelections()))
        .forEach(deploymentFreeze -> {
          validateName(deploymentFreeze.getName());
          validateAppEnvFilter(deploymentFreeze);
          validateTimeRange(deploymentFreeze.getTimeRange(), deploymentFreeze.getName());
        });
  }

  private void validateUserGroups(List<String> userGroups, String accountId) {
    if (isEmpty(userGroups)) {
      throw new InvalidRequestException("User Groups cannot be empty");
    }
    for (String userGroupId : userGroups) {
      UserGroup userGroup = userGroupService.get(accountId, userGroupId);
      if (userGroup == null) {
        throw new InvalidRequestException(format("Invalid User Group Id: %s", userGroupId));
      }
    }
  }

  /**
   * We need to set uuid for individual windows
   * @param timeRangeBasedFreezeConfigs
   * @param oldGovernanceConfig
   */
  private void resetReadOnlyProperties(List<TimeRangeBasedFreezeConfig> timeRangeBasedFreezeConfigs, String accountId,
      GovernanceConfig oldGovernanceConfig) {
    List<TimeRangeBasedFreezeConfig> oldTimeRangeBasedFreezeConfigs =
        oldGovernanceConfig.getTimeRangeBasedFreezeConfigs();

    Map<String, TimeRangeBasedFreezeConfig> configMap = oldTimeRangeBasedFreezeConfigs.stream().collect(
        Collectors.toMap(TimeRangeBasedFreezeConfig::getName, Function.identity()));

    for (TimeRangeBasedFreezeConfig entry : timeRangeBasedFreezeConfigs) {
      if (configMap.get(entry.getName()) != null) {
        TimeRangeBasedFreezeConfig oldWindow = configMap.get(entry.getName());
        // update scenario, restore uuid and timezone
        entry.setUuid(oldWindow.getUuid());

        if (isEmpty(entry.getDescription())) {
          entry.setDescription(null);
        }

        // if any updates to an active window
        if (!entry.equals(oldWindow)) {
          if (oldWindow.checkIfActive()) {
            throw new InvalidRequestException("Cannot update active freeze window");
          }
        }
        validateUserGroups(entry.getUserGroups(), accountId);
      }
    }
  }

  private void validateName(String name) {
    if (name == null) {
      throw new InvalidRequestException("Name cannot be empty for the freeze window");
    }
  }

  private void validateTimeRange(TimeRange timeRange, String name) {
    Validator.notNullCheck("Time zone cannot be empty", timeRange.getTimeZone());
    if (!ZoneId.getAvailableZoneIds().contains(timeRange.getTimeZone())) {
      throw new InvalidRequestException(
          "Please select a valid time zone. Eg. Asia/Calcutta for freeze window: " + name);
    }
    if (timeRange.getFrom() > timeRange.getTo()) {
      throw new InvalidRequestException("Window Start time is less than Window end Time");
    }
    if (timeRange.getTo() - timeRange.getFrom() < MIN_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be at least 30 minutes");
    }
    if (timeRange.getTo() - timeRange.getFrom() > MAX_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be less than 365 days");
    }
  }

  private void validateAppEnvFilter(TimeRangeBasedFreezeConfig deploymentFreeze) {
    if (deploymentFreeze.getAppSelections().stream().anyMatch(appSelection
            -> appSelection.getFilterType() != BlackoutWindowFilterType.CUSTOM
                && appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.CUSTOM)) {
      throw new InvalidRequestException(
          "Environment filter type can be CUSTOM only when Application Filter type is CUSTOM");
    }
    if (deploymentFreeze.getAppSelections()
            .stream()
            .filter(selection -> selection.getFilterType() == BlackoutWindowFilterType.CUSTOM)
            .anyMatch(appSelection
                -> appSelection.getEnvSelection().getFilterType() == EnvironmentFilterType.CUSTOM
                    && ((CustomAppFilter) appSelection).getApps().size() != 1)) {
      throw new InvalidRequestException(
          "Application filter should have exactly one app when environment filter type is CUSTOM");
    }
  }

  private void auditDeploymentFreeze(String accountId, GovernanceConfig oldConfig, GovernanceConfig updatedConfig) {
    if (deploymentFreezeBeingEnabled(oldConfig, updatedConfig)) {
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, oldConfig, updatedConfig, Type.ENABLE);
    } else if (deploymentFreezeBeingDisabled(oldConfig, updatedConfig)) {
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, oldConfig, updatedConfig, Type.DISABLE);
    } else {
      auditServiceHelper.reportForAuditingUsingAccountId(accountId, oldConfig, updatedConfig, Type.UPDATE);
    }
  }

  private boolean deploymentFreezeBeingEnabled(GovernanceConfig oldConfig, GovernanceConfig updatedConfig) {
    return updatedConfig.isDeploymentFreeze() && (oldConfig == null || !oldConfig.isDeploymentFreeze());
  }

  private boolean deploymentFreezeBeingDisabled(GovernanceConfig oldConfig, GovernanceConfig updatedConfig) {
    return !updatedConfig.isDeploymentFreeze() && (oldConfig == null || oldConfig.isDeploymentFreeze());
  }

  private void publishToSegment(String accountId, User user, EventType eventType) {
    if (null == user) {
      log.error("User is null when trying to publish to segment. Event will be skipped. Event Type: {}, accountId={}",
          eventType, accountId);
      return;
    }

    // repeating=false until repeating blackout windows are implemented
    Builder messageBuilder = TrackMessage.builder(eventType.toString())
                                 .userId(user.getUuid())
                                 .properties(new ImmutableMap.Builder<String, Object>()
                                                 .put("product", "Security")
                                                 .put("groupId", accountId)
                                                 .put("module", "Governance")
                                                 .put("repeating", false)
                                                 .build());

    segmentHelper.enqueue(messageBuilder);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting Deployment Freeze window(s)");
      Query<GovernanceConfig> query =
          wingsPersistence.createQuery(GovernanceConfig.class).filter(GovernanceConfigKeys.accountId, accountId);
      GovernanceConfig config = query.get();
      if (wingsPersistence.delete(query)) {
        auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, config);
      }
    }
  }
}
