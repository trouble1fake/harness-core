/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.AUTO_ACCEPT_SAML_ACCOUNT_INVITES;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.ACCOUNT_DOES_NOT_EXIST;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.KubernetesConvention.getAccountIdentifier;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.persistence.HQuery.excludeAuthorityCount;
import static io.harness.utils.Misc.generateSecretKey;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.AppContainer.Builder.anAppContainer;
import static software.wings.beans.Base.ID_KEY2;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.NotificationGroup.NotificationGroupBuilder.aNotificationGroup;
import static software.wings.beans.Role.Builder.aRole;
import static software.wings.beans.RoleType.ACCOUNT_ADMIN;
import static software.wings.beans.RoleType.APPLICATION_ADMIN;
import static software.wings.beans.RoleType.NON_PROD_SUPPORT;
import static software.wings.beans.RoleType.PROD_SUPPORT;
import static software.wings.beans.SystemCatalog.CatalogType.APPSTACK;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.account.ProvisionStep;
import io.harness.account.ProvisionStep.ProvisionStepKeys;
import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.AuthenticationInfo;
import io.harness.authenticationservice.beans.AuthenticationInfo.AuthenticationInfoBuilder;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.cache.HarnessCacheManager;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.cvng.beans.ServiceGuardLimitDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.datahandler.models.AccountDetails;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.dataretention.AccountDataRetentionService;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.utils.DelegateRingConstants;
import io.harness.eraro.Level;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.event.handler.impl.segment.SegmentGroupEventJobService;
import io.harness.event.model.Event;
import io.harness.event.model.EventData;
import io.harness.event.model.EventType;
import io.harness.event.publisher.EventPublisher;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.managerclient.HttpsCertRequirement.CertRequirement;
import io.harness.network.Http;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.scheduler.PersistentScheduler;
import io.harness.seeddata.SampleDataProviderService;
import io.harness.validation.SuppressValidation;
import io.harness.version.VersionInfoManager;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.AccountEvent;
import software.wings.beans.AccountPreferences;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.AppContainer;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.LicenseInfo;
import software.wings.beans.NotificationGroup;
import software.wings.beans.Role;
import software.wings.beans.RoleType;
import software.wings.beans.Service;
import software.wings.beans.SubdomainUrl;
import software.wings.beans.SystemCatalog;
import software.wings.beans.TechStack;
import software.wings.beans.UrlInfo;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.loginSettings.LoginSettingsService;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettings.LdapSettingsKeys;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.dl.GenericDbCache;
import software.wings.dl.WingsPersistence;
import software.wings.exception.AccountNotFoundException;
import software.wings.features.GovernanceFeature;
import software.wings.helpers.ext.account.DeleteAccountHelper;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.licensing.LicenseService;
import software.wings.scheduler.AlertCheckJob;
import software.wings.scheduler.InstanceStatsCollectorJob;
import software.wings.scheduler.LdapGroupSyncJob;
import software.wings.scheduler.LdapGroupSyncJobHelper;
import software.wings.scheduler.LimitVicinityCheckerJob;
import software.wings.scheduler.ScheduledTriggerJob;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserThreadLocal;
import software.wings.security.authentication.AccountSettingsResponse;
import software.wings.security.saml.SSORequest;
import software.wings.security.saml.SamlClientService;
import software.wings.service.impl.analysis.CVEnabledService;
import software.wings.service.impl.event.AccountEntityEvent;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertNotificationRuleService;
import software.wings.service.intfc.AppContainerService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.SystemCatalogService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.account.AccountCrudObserver;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.verification.CVConfiguration;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.DuplicateKeyException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;

/**
 * Created by peeyushaggarwal on 10/11/16.
 */
@OwnedBy(PL)
@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(_955_ACCOUNT_MGMT)
@BreakDependencyOn("io.harness.delegate.beans.Delegate")
@BreakDependencyOn("software.wings.service.impl.DelegateConnectionDao")
public class AccountServiceImpl implements AccountService {
  private static final SecureRandom random = new SecureRandom();
  private static final int SIZE_PER_SERVICES_REQUEST = 25;
  private static final int NUM_OF_RETRIES_TO_GENERATE_UNIQUE_ACCOUNT_NAME = 20;
  private static final String UNLIMITED_PAGE_SIZE = "UNLIMITED";
  private static final String ILLEGAL_ACCOUNT_NAME_CHARACTERS = "[~!@#$%^*\\[\\]{}<>'\"/:;\\\\]";
  private static final int MAX_ACCOUNT_NAME_LENGTH = 50;
  private static final String WELCOME_EMAIL_TEMPLATE_NAME = "welcome_email";
  private static final String GENERATE_SAMPLE_DELEGATE_CURL_COMMAND_FORMAT_STRING =
      "curl -s -X POST -H 'content-type: application/json' "
      + "--url https://app.harness.io/gateway/gratis/api/webhooks/cmnhGRyXyBP5RJzz8Ae9QP7mqUATVotr7v2knjOf "
      + "-d '{\"application\":\"4qPkwP5dQI2JduECqGZpcg\","
      + "\"parameters\":{\"Environment\":\"%s\",\"delegate\":\"delegate\","
      + "\"account_id\":\"%s\",\"account_id_short\":\"%s\",\"account_secret\":\"%s\"}}'";
  private static final String SAMPLE_DELEGATE_STATUS_ENDPOINT_FORMAT_STRING = "http://%s/account-%s.txt";
  private static final String DELIMITER = "####";
  private static final String DEFAULT_EXPERIENCE = "defaultExperience";

  @Inject protected AuthService authService;
  @Inject protected HarnessCacheManager harnessCacheManager;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private RoleService roleService;
  @Inject private AuthHandler authHandler;
  @Inject private LicenseService licenseService;
  @Inject private NotificationSetupService notificationSetupService;
  @Inject private SettingsService settingsService;
  @Inject private ExecutorService executorService;
  @Inject private AppService appService;
  @Inject private AppContainerService appContainerService;
  @Inject private SystemCatalogService systemCatalogService;
  @Inject private TemplateGalleryService templateGalleryService;
  @Inject private GenericDbCache dbCache;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private AlertNotificationRuleService notificationRuleService;
  @Inject private SampleDataProviderService sampleDataProviderService;
  @Inject private GovernanceConfigService governanceConfigService;
  @Inject private SSOSettingServiceImpl ssoSettingService;
  @Inject private SamlClientService samlClientService;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private UserService userService;
  @Inject private LoginSettingsService loginSettingsService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private EmailNotificationService emailNotificationService;
  @Inject private HarnessUserGroupService harnessUserGroupService;
  @Inject private EventPublisher eventPublisher;
  @Inject private SegmentGroupEventJobService segmentGroupEventJobService;
  @Inject private Morphia morphia;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private DeleteAccountHelper deleteAccountHelper;
  @Inject private AccountDao accountDao;
  @Inject private AccountDataRetentionService accountDataRetentionService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private LdapGroupSyncJobHelper ldapGroupSyncJobHelper;
  @Inject private DelegateService delegateService;
  @Inject @Named(EventsFrameworkConstants.ENTITY_CRUD) private Producer eventProducer;
  @Inject private RemoteObserverInformer remoteObserverInformer;
  @Inject private HPersistence persistence;

  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Inject private GovernanceFeature governanceFeature;
  private Map<String, UrlInfo> techStackDocLinks;

  @Getter private Subject<AccountCrudObserver> accountCrudSubject = new Subject<>();

  @Override
  public Account save(@Valid Account account, boolean fromDataGen) {
    return save(account, fromDataGen, true);
  }

  @Override
  public Account save(@Valid Account account, boolean fromDataGen, boolean shouldCreateSampleApp) {
    // Validate if account/company name is valid.
    validateAccount(account);

    account.setCompanyName(account.getCompanyName().trim());
    account.setAccountName(account.getAccountName().trim());
    if (isEmpty(account.getRingName())) {
      account.setRingName(DelegateRingConstants.DEFAULT_RING_NAME);
    }
    if (isEmpty(account.getUuid())) {
      log.info("Creating a new account '{}'.", account.getAccountName());
      account.setUuid(UUIDGenerator.generateUuid());
    } else {
      log.info("Creating a new account '{}' with specified id '{}'.", account.getAccountName(), account.getUuid());
    }

    account.setAppId(GLOBAL_APP_ID);
    account.setAccountKey(generateSecretKey());
    LicenseUtils.addLicenseInfo(account);

    accountDao.save(account);

    try (AutoLogContext logContext = new AccountLogContext(account.getUuid(), OVERRIDE_ERROR)) {
      // Both subject and remote Observer are needed since in few places DMS might not be present
      accountCrudSubject.fireInform(AccountCrudObserver::onAccountCreated, account);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(AccountCrudObserver.class, "onAccountCreated", Account.class),
          AccountServiceImpl.class, account);

      // When an account is just created for import, no need to create default account entities.
      // As the import process will do all these instead.
      if (account.isForImport()) {
        log.info("Creating the account for import only, no default account entities will be created");
      } else {
        createDefaultAccountEntities(account, shouldCreateSampleApp, fromDataGen);
        // Schedule default account level jobs.
        scheduleAccountLevelJobs(account.getUuid());
      }

      publishAccountChangeEvent(account);
      // TODO {karan} uncomment this when NG is enabled globally for new accounts
      // publishAccountChangeEventViaEventFramework(account.getUuid(), EventsFrameworkMetadataConstants.CREATE_ACTION);

      log.info("Successfully created account.");
    }
    return account;
  }

  private void publishAccountChangeEventViaEventFramework(String accountId, String action) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of(EventsFrameworkMetadataConstants.ENTITY_TYPE,
                  EventsFrameworkMetadataConstants.ACCOUNT_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(AccountEntityChangeDTO.newBuilder().setAccountId(accountId).build().toByteString())
              .build());
    } catch (Exception ex) {
      log.error(
          String.format("Failed to publish account %s event for accountId %s via event framework.", action, accountId));
    }
  }

  private void publishAccountChangeEvent(Account account) {
    EventData eventData = EventData.builder().eventInfo(new AccountEntityEvent(account)).build();
    eventPublisher.publishEvent(
        Event.builder().eventData(eventData).eventType(EventType.ACCOUNT_ENTITY_CHANGE).build());
  }

  public void validateAccount(Account account) {
    String companyName = account.getCompanyName();
    String accountName = account.getAccountName();
    if (isBlank(companyName)) {
      throw new InvalidRequestException("Company Name can't be empty.", USER);
    } else if (companyName.length() > MAX_ACCOUNT_NAME_LENGTH) {
      throw new InvalidRequestException(
          "Company Name exceeds " + MAX_ACCOUNT_NAME_LENGTH + " max-allowed characters.", USER);
    } else {
      String[] parts = companyName.split(ILLEGAL_ACCOUNT_NAME_CHARACTERS, 2);
      if (parts.length > 1) {
        throw new InvalidRequestException("Company Name '" + companyName + "' contains illegal characters.", USER);
      }
    }

    if (isBlank(accountName)) {
      throw new InvalidRequestException("Account Name can't be empty.", USER);
    } else if (accountName.length() > MAX_ACCOUNT_NAME_LENGTH) {
      throw new InvalidRequestException(
          "Account Name exceeds " + MAX_ACCOUNT_NAME_LENGTH + " max-allowed characters.", USER);
    } else {
      String[] parts = accountName.split(ILLEGAL_ACCOUNT_NAME_CHARACTERS, 2);
      if (parts.length > 1) {
        throw new InvalidRequestException("Account Name '" + accountName + "' contains illegal characters", USER);
      }
    }
    String suggestedAccountName = suggestAccountName(accountName);
    account.setAccountName(suggestedAccountName);
  }

  @Override
  public boolean isCertValidationRequired(String accountId) {
    if (isEmpty(accountId)) {
      return false;
    }
    Account account = wingsPersistence.get(Account.class, accountId);
    if (account == null) {
      return false;
    }
    return featureFlagService.isEnabled(FeatureName.ENABLE_CERT_VALIDATION, accountId);
  }

  @Override
  public boolean updatePovFlag(String accountId, boolean isPov) {
    Account account = getFromCache(accountId);
    if (account == null) {
      log.warn("accountId={} doesn't exist", accountId);
      return false;
    }

    if (account.getLicenseInfo() == null || !AccountType.TRIAL.equals(account.getLicenseInfo().getAccountType())) {
      log.info("accountId={} does not have license or is not a TRIAL account", accountId);
      return false;
    }

    UpdateOperations<Account> updateOperation = wingsPersistence.createUpdateOperations(Account.class);
    updateOperation.set(AccountKeys.isPovAccount, isPov);
    UpdateResults updateResults = wingsPersistence.update(account, updateOperation);

    if (updateResults != null && updateResults.getUpdatedCount() > 0) {
      log.info("Successfully set isPovAccount to {} for accountId = {} ", isPov, accountId);
      return true;
    }

    log.info("Failed to set isPovAccount to {} for accountId = {} ", isPov, accountId);
    return false;
  }

  @Override
  public boolean updateAccountStatus(String accountId, String accountStatus) {
    Account account = getFromCacheWithFallback(accountId);
    LicenseInfo licenseInfo = account.getLicenseInfo();
    licenseInfo.setAccountStatus(accountStatus);
    return licenseService.updateAccountLicense(accountId, licenseInfo);
  }

  private void createDefaultAccountEntities(Account account, boolean shouldCreateSampleApp, boolean fromDataGen) {
    createDefaultRoles(account)
        .stream()
        .filter(role -> RoleType.ACCOUNT_ADMIN == role.getRoleType())
        .forEach(role -> createDefaultNotificationGroup(account, role));
    createSystemAppContainers(account);
    authHandler.createDefaultUserGroups(account);
    loginSettingsService.createDefaultLoginSettings(account);
    notificationRuleService.createDefaultRule(account.getUuid());

    executorService.submit(() -> {
      try {
        templateGalleryService.copyHarnessTemplatesToAccountV2(account.getUuid(), account.getAccountName());
      } catch (Exception e) {
        log.error("Failed to load default templates", e);
      }
      try {
        templateGalleryService.saveHarnessCommandLibraryGalleryToAccount(account.getUuid(), account.getAccountName());
      } catch (Exception e) {
        log.error("Failed to load harness gallery", e);
      }
    });

    enableFeatureFlags(account, fromDataGen);
    if (shouldCreateSampleApp) {
      if (account.isCreatedFromNG()) {
        // Asynchronous creates the sample app in NG
        executorService.submit(() -> sampleDataProviderService.createK8sV2SampleApp(account));
      } else {
        sampleDataProviderService.createK8sV2SampleApp(account);
      }
    }
  }

  private void enableFeatureFlags(@NotNull Account account, boolean fromDataGen) {
    if (fromDataGen) {
      updateNextGenEnabled(account.getUuid(), true);
      featureFlagService.enableAccount(FeatureName.CDNG_ENABLED, account.getUuid());
      featureFlagService.enableAccount(FeatureName.CENG_ENABLED, account.getUuid());
      featureFlagService.enableAccount(FeatureName.CFNG_ENABLED, account.getUuid());
      featureFlagService.enableAccount(FeatureName.CING_ENABLED, account.getUuid());
      featureFlagService.enableAccount(FeatureName.CVNG_ENABLED, account.getUuid());
    } else if (account.isCreatedFromNG()) {
      updateNextGenEnabled(account.getUuid(), true);
    }
  }

  List<Role> createDefaultRoles(Account account) {
    return Lists.newArrayList(roleService.save(aRole()
                                                   .withAppId(GLOBAL_APP_ID)
                                                   .withAccountId(account.getUuid())
                                                   .withName(ACCOUNT_ADMIN.getDisplayName())
                                                   .withRoleType(ACCOUNT_ADMIN)
                                                   .build()),

        roleService.save(aRole()
                             .withAppId(GLOBAL_APP_ID)
                             .withAccountId(account.getUuid())
                             .withName(APPLICATION_ADMIN.getDisplayName())
                             .withRoleType(APPLICATION_ADMIN)
                             .withAllApps(true)
                             .build()),
        roleService.save(aRole()
                             .withAppId(GLOBAL_APP_ID)
                             .withAccountId(account.getUuid())
                             .withName(PROD_SUPPORT.getDisplayName())
                             .withRoleType(PROD_SUPPORT)
                             .withAllApps(true)
                             .build()),
        roleService.save(aRole()
                             .withAppId(GLOBAL_APP_ID)
                             .withAccountId(account.getUuid())
                             .withName(NON_PROD_SUPPORT.getDisplayName())
                             .withRoleType(NON_PROD_SUPPORT)
                             .withAllApps(true)
                             .build()));
  }

  @Override
  public Account get(String accountId) {
    Account account = wingsPersistence.get(Account.class, accountId);
    if (account == null) {
      throw new AccountNotFoundException(
          "Account is not found for the given id:" + accountId, null, ACCOUNT_DOES_NOT_EXIST, Level.ERROR, USER, null);
    }
    LicenseUtils.decryptLicenseInfo(account, false);
    return account;
  }

  @Override
  public boolean isNextGenEnabled(String accountId) {
    Account account = getFromCacheWithFallback(accountId);
    return account != null && account.isNextGenEnabled();
  }

  @Override
  public Boolean updateNextGenEnabled(String accountId, boolean enabled) {
    Account account = get(accountId);
    account.setNextGenEnabled(enabled);
    update(account);
    publishAccountChangeEventViaEventFramework(accountId, UPDATE_ACTION);
    return true;
  }

  @Override
  public AccountDetails getAccountDetails(String accountId) {
    Account account = wingsPersistence.get(Account.class, accountId);
    if (account == null) {
      throw new AccountNotFoundException(
          "Account is not found for the given id:" + accountId, null, ACCOUNT_DOES_NOT_EXIST, Level.ERROR, USER, null);
    }
    LicenseUtils.decryptLicenseInfo(account, false);
    AccountDetails accountDetails = new AccountDetails();
    accountDetails.setAccountId(accountId);
    accountDetails.setAccountName(account.getAccountName());
    accountDetails.setCompanyName(account.getCompanyName());
    accountDetails.setCluster(mainConfiguration.getDeploymentClusterName());
    accountDetails.setLicenseInfo(account.getLicenseInfo());
    accountDetails.setCeLicenseInfo(account.getCeLicenseInfo());
    accountDetails.setDefaultExperience(account.getDefaultExperience());
    accountDetails.setCreatedFromNG(account.isCreatedFromNG());
    accountDetails.setActiveServiceCount(workflowExecutionService.getActiveServiceCount(accountId));
    return accountDetails;
  }

  @Override
  public List<Account> getAccounts(List<String> identifiers) {
    return wingsPersistence.createQuery(Account.class).field("uuid").in(identifiers).asList();
  }

  @Override
  public Account getFromCache(String accountId) {
    return dbCache.get(Account.class, accountId);
  }

  @Override
  public String getAccountStatus(String accountId) {
    Account account = getFromCacheWithFallback(accountId);
    LicenseInfo licenseInfo = account.getLicenseInfo();
    return licenseInfo == null ? AccountStatus.ACTIVE : licenseInfo.getAccountStatus();
  }

  private void decryptLicenseInfo(List<Account> accounts) {
    if (isEmpty(accounts)) {
      return;
    }

    accounts.forEach(account -> LicenseUtils.decryptLicenseInfo(account, false));
  }

  @Override
  public Account getFromCacheWithFallback(String accountId) {
    Account account = dbCache.get(Account.class, accountId);
    if (account == null) {
      // Some false nulls have been observed. Verify by querying directly from db.
      account = get(accountId);
    }
    return account;
  }

  @Override
  public boolean delete(String accountId) {
    boolean success = accountId != null && deleteAccountHelper.deleteAccount(accountId);
    if (success) {
      publishAccountChangeEventViaEventFramework(accountId, DELETE_ACTION);
    }
    return success;
  }

  @Override
  public void handleNonExistentAccount(String accountId) {
    if (accountId != null) {
      deleteAccountHelper.upsertDeletedEntity(accountId, 0);
    }
  }

  @Override
  public boolean deleteExportableAccountData(String accountId) {
    log.info("Deleting exportable data for account {}", accountId);
    if (get(accountId) == null) {
      throw new InvalidRequestException("The account to be deleted doesn't exist");
    }

    return deleteAccountHelper.deleteExportableAccountData(accountId);
  }

  @Override
  public boolean getTwoFactorEnforceInfo(String accountId) {
    Query<Account> getQuery = wingsPersistence.createQuery(Account.class).filter(ID_KEY2, accountId);
    return getQuery.get().isTwoFactorAdminEnforced();
  }

  @Override
  public void updateTwoFactorEnforceInfo(String accountId, boolean enabled) {
    Account account = get(accountId);
    account.setTwoFactorAdminEnforced(enabled);
    update(account);
  }

  /**
   * Takes a valid account name and checks database for duplicates, if duplicate exists appends
   * "-x" (where x is a random number between 1000 and 9999) to the name and repeats the process until it generates a
   * unique account name
   *
   * @param accountName user input account name
   * @return uniqueAccountName
   */
  @Override
  public String suggestAccountName(@NotNull String accountName) {
    if (!exists(accountName)) {
      return accountName;
    }
    log.debug("Account name '{}' already in use, generating new unique account name", accountName);
    int count = 0;
    while (count < NUM_OF_RETRIES_TO_GENERATE_UNIQUE_ACCOUNT_NAME) {
      String newAccountName = accountName + "-" + (1000 + random.nextInt(9000));
      if (!exists(newAccountName)) {
        return newAccountName;
      }
      count++;
    }
    throw new GeneralException(
        String.format("Failed to generate unique Account Name for initial accountName=%s", accountName));
  }

  /**
   * Takes an account name and performs a case-insensitive check on all account names in database
   *
   * @param accountName account name
   * @return Returns true if duplicate is found else false
   */
  private Boolean isDuplicateAccountName(@NotNull String accountName) {
    return wingsPersistence.createQuery(Account.class, excludeAuthority)
               .field(AccountKeys.accountName)
               .equalIgnoreCase(accountName)
               .get()
        != null;
  }

  @Override
  public boolean updateTechStacks(String accountId, Set<TechStack> techStacks) {
    Account accountInDB = get(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, accountInDB, USER);

    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
    if (isEmpty(techStacks)) {
      updateOperations.unset(AccountKeys.techStacks);
    } else {
      updateOperations.set(AccountKeys.techStacks, techStacks);
    }
    wingsPersistence.update(accountInDB, updateOperations);
    dbCache.invalidate(Account.class, accountId);

    final List<User> usersOfAccount = userService.getUsersOfAccount(accountId);
    final User currentUser = UserThreadLocal.get();
    if (isNotEmpty(usersOfAccount) && usersOfAccount.contains(currentUser)) {
      executorService.submit(() -> sendWelcomeEmail(currentUser, techStacks));
    }
    eventPublishHelper.publishTechStackEvent(accountId, techStacks);
    return true;
  }

  @Override
  public void updateAccountEvents(String accountId, AccountEvent accountEvent) {
    Account accountInDB = get(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, accountInDB, USER);
    Set<AccountEvent> accountEvents = Sets.newHashSet(accountEvent);
    Set<AccountEvent> existingEvents = accountInDB.getAccountEvents();
    if (isNotEmpty(existingEvents)) {
      accountEvents.addAll(existingEvents);
    }

    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
    if (isEmpty(accountEvents)) {
      updateOperations.unset("accountEvents");
    } else {
      updateOperations.set("accountEvents", accountEvents);
    }
    wingsPersistence.update(accountInDB, updateOperations);
    dbCache.invalidate(Account.class, accountId);
  }

  private UrlInfo getDocLink(TechStack techStack) {
    String category = techStack.getCategory();
    String technology = techStack.getTechnology();
    if (isEmpty(category)) {
      return null;
    }

    if (isEmpty(technology)) {
      return null;
    }

    String key =
        new StringBuilder(category.substring(0, category.indexOf(' '))).append("-").append(technology).toString();
    return techStackDocLinks.get(key);
  }

  private void sendWelcomeEmail(User user, Set<TechStack> techStackSet) {
    if (techStackDocLinks == null) {
      techStackDocLinks = mainConfiguration.getTechStackLinks();
    }

    try {
      List<String> deployPlatforms = new ArrayList<>();
      List<String> artifacts = new ArrayList<>();
      List<String> monitoringTools = new ArrayList<>();
      if (isNotEmpty(techStackSet)) {
        techStackSet.forEach(techStack -> {
          UrlInfo docLink = getDocLink(techStack);
          if (docLink != null) {
            switch (techStack.getCategory()) {
              case "Deployment Platforms":
                deployPlatforms.add(String.join(DELIMITER, docLink.getTitle(), docLink.getUrl()));
                break;
              case "Artifact Repositories":
                artifacts.add(String.join(DELIMITER, docLink.getTitle(), docLink.getUrl()));
                break;
              case "Monitoring And Logging":
                monitoringTools.add(String.join(DELIMITER, docLink.getTitle(), docLink.getUrl()));
                break;
              default:
                throw new WingsException("Unknown category " + techStack.getCategory());
            }
          }
        });
      }

      if (isEmpty(deployPlatforms)) {
        UrlInfo docLink =
            getDocLink(TechStack.builder().category("Deployment Platforms").technology("General").build());
        if (docLink != null) {
          deployPlatforms.add(String.join(DELIMITER, docLink.getTitle(), docLink.getUrl()));
        }
      }

      if (isEmpty(artifacts)) {
        UrlInfo docLink =
            getDocLink(TechStack.builder().category("Artifact Repositories").technology("General").build());
        if (docLink != null) {
          artifacts.add(String.join(DELIMITER, docLink.getTitle(), docLink.getUrl()));
        }
      }

      if (isEmpty(monitoringTools)) {
        UrlInfo docLink =
            getDocLink(TechStack.builder().category("Monitoring And Logging").technology("General").build());
        if (docLink != null) {
          monitoringTools.add(String.join(DELIMITER, docLink.getTitle(), docLink.getUrl()));
        }
      }

      Map<String, Object> model = new HashMap<>();
      model.put("name", user.getName());
      model.put("deploymentPlatforms", deployPlatforms);
      model.put("artifacts", artifacts);
      model.put("monitoringAndLoggingTools", monitoringTools);
      sendEmail(user.getEmail(), WELCOME_EMAIL_TEMPLATE_NAME, model);
    } catch (Exception e) {
      log.error("Failed to send welcome email", e);
    }
  }

  private boolean sendEmail(String toEmail, String templateName, Map<String, Object> templateModel) {
    List<String> toList = new ArrayList<>();
    toList.add(toEmail);
    EmailData emailData =
        EmailData.builder().to(toList).templateName(templateName).templateModel(templateModel).build();
    emailData.setRetries(2);
    return emailNotificationService.send(emailData);
  }

  @Override
  public boolean exists(String accountName) {
    return wingsPersistence.createQuery(Account.class, excludeAuthority)
               .field(AccountKeys.accountName)
               .equalIgnoreCase(accountName)
               .getKey()
        != null;
  }

  @Override
  public Optional<String> getAccountType(String accountId) {
    Account account = getFromCache(accountId);
    if (account == null) {
      log.warn("accountId={} doesn't exist", accountId);
      return Optional.empty();
    }

    LicenseInfo licenseInfo = account.getLicenseInfo();
    if (null == licenseInfo) {
      log.warn("License info not present for account. accountId={}", accountId);
      return Optional.empty();
    }

    String accountType = licenseInfo.getAccountType();
    if (!AccountType.isValid(accountType)) {
      log.warn("Invalid account type. accountType={}, accountId={}", accountType, accountId);
      return Optional.empty();
    }

    return Optional.of(accountType);
  }

  @Override
  public Boolean updateCloudCostEnabled(String accountId, boolean cloudCostEnabled) {
    Account account = get(accountId);
    account.setCloudCostEnabled(cloudCostEnabled);
    update(account);
    return true;
  }

  @Override
  public boolean updateCeAutoCollectK8sEvents(String accountId, boolean ceK8sEventCollectionEnabled) {
    Account account = get(accountId);
    account.setCeAutoCollectK8sEvents(ceK8sEventCollectionEnabled);
    update(account);
    return true;
  }

  @Override
  public Account update(@Valid Account account) {
    LicenseUtils.decryptLicenseInfo(account, false);

    UpdateOperations<Account> updateOperations =
        wingsPersistence.createUpdateOperations(Account.class)
            .set("companyName", account.getCompanyName())
            .set("twoFactorAdminEnforced", account.isTwoFactorAdminEnforced())
            .set(AccountKeys.oauthEnabled, account.isOauthEnabled())
            .set(AccountKeys.cloudCostEnabled, account.isCloudCostEnabled())
            .set(AccountKeys.nextGenEnabled, account.isNextGenEnabled())
            .set(AccountKeys.ceAutoCollectK8sEvents, account.isCeAutoCollectK8sEvents())
            .set("whitelistedDomains", account.getWhitelistedDomains());

    if (null != account.getLicenseInfo()) {
      updateOperations.set(AccountKeys.licenseInfo, account.getLicenseInfo());
    }

    if (account.getAuthenticationMechanism() != null) {
      updateOperations.set("authenticationMechanism", account.getAuthenticationMechanism());
    }

    if (account.getServiceGuardLimit() != null) {
      updateOperations.set(AccountKeys.serviceGuardLimit, account.getServiceGuardLimit());
    }

    if (account.getDefaultExperience() != null) {
      updateOperations.set(AccountKeys.defaultExperience, account.getDefaultExperience());
    }

    wingsPersistence.update(account, updateOperations);
    dbCache.invalidate(Account.class, account.getUuid());
    authService.evictUserPermissionCacheForAccount(account.getUuid(), true);
    Account updatedAccount = wingsPersistence.get(Account.class, account.getUuid());
    LicenseUtils.decryptLicenseInfo(updatedAccount, false);

    publishAccountChangeEvent(updatedAccount);
    try (AutoLogContext logContext = new AccountLogContext(account.getUuid(), OVERRIDE_ERROR)) {
      accountCrudSubject.fireInform(AccountCrudObserver::onAccountUpdated, updatedAccount);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(AccountCrudObserver.class, "onAccountUpdated", Account.class),
          AccountServiceImpl.class, updatedAccount);
    }
    return updatedAccount;
  }

  @Override
  public boolean isPaidAccount(String accountId) {
    Optional<String> accountType = getAccountType(accountId);
    return accountType.isPresent() && AccountType.PAID.equals(accountType.get());
  }

  @Override
  public Optional<Account> getOnPremAccount() {
    List<Account> accounts = listAccounts(Sets.newHashSet(GLOBAL_ACCOUNT_ID));
    return isNotEmpty(accounts) ? Optional.of(accounts.get(0)) : Optional.empty();
  }

  @Override
  public Account getByName(String companyName) {
    return wingsPersistence.createQuery(Account.class).filter(AccountKeys.companyName, companyName).get();
  }

  @Override
  public List<Account> list(PageRequest<Account> pageRequest) {
    List<Account> accountList = wingsPersistence.query(Account.class, pageRequest, excludeAuthority).getResponse();
    decryptLicenseInfo(accountList);
    return accountList;
  }

  @Override
  public List<Account> listAccounts(Set<String> excludedAccountIds) {
    Query<Account> query = wingsPersistence.createQuery(Account.class, excludeAuthority);
    if (isNotEmpty(excludedAccountIds)) {
      query.field("_id").notIn(excludedAccountIds);
    }

    List<Account> accountList = new ArrayList<>();
    try (HIterator<Account> iterator = new HIterator<>(query.fetch())) {
      for (Account account : iterator) {
        LicenseUtils.decryptLicenseInfo(account, false);
        accountList.add(account);
      }
    }
    return accountList;
  }

  @Override
  public DelegateConfiguration getDelegateConfiguration(String accountId) {
    if (licenseService.isAccountDeleted(accountId)) {
      throw new InvalidRequestException("Deleted AccountId: " + accountId);
    }

    Account account = wingsPersistence.createQuery(Account.class, excludeAuthorityCount)
                          .filter(AccountKeys.uuid, accountId)
                          .project("delegateConfiguration", true)
                          .get();

    if (account.getDelegateConfiguration() == null) {
      account = wingsPersistence.createQuery(Account.class, excludeAuthorityCount)
                    .filter(AccountKeys.uuid, GLOBAL_ACCOUNT_ID)
                    .project("delegateConfiguration", true)
                    .get();
      return account.getDelegateConfiguration();
    }
    return DelegateConfiguration.builder()
        .accountVersion(true)
        .delegateVersions(account.getDelegateConfiguration().getDelegateVersions())
        .build();
  }

  @Override
  public String getAccountPrimaryDelegateVersion(String accountId) {
    if (licenseService.isAccountDeleted(accountId)) {
      throw new InvalidRequestException("Deleted AccountId: " + accountId);
    }
    Account account = wingsPersistence.createQuery(Account.class, excludeAuthorityCount)
                          .filter(AccountKeys.uuid, accountId)
                          .project("delegateConfiguration", true)
                          .get();
    if (account.getDelegateConfiguration() == null) {
      return null;
    }
    return account.getDelegateConfiguration()
        .getDelegateVersions()
        .stream()
        .reduce((first, last) -> last)
        .orElse(EMPTY);
  }

  @Override
  public boolean updateAccountPreference(String accountId, String preferenceKey, Object value) {
    Account account = get(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, USER);

    AccountPreferences accountPreferences = account.getAccountPreferences();
    if (accountPreferences == null) {
      accountPreferences = AccountPreferences.builder().build();
    }

    try {
      Field field = ReflectionUtils.getFieldByName(accountPreferences.getClass(), preferenceKey);
      if (field == null) {
        log.warn("The provided preferenceKey is not valid");
        return false;
      }
      if (field != null) {
        field.setAccessible(true);
        Class clazz = field.getType();
        if (clazz.getSuperclass().isAssignableFrom(Integer.class)) {
          field.set(accountPreferences, value);
          wingsPersistence.update(account,
              wingsPersistence.createUpdateOperations(Account.class)
                  .set(AccountKeys.accountPreferences, accountPreferences));
          return true;
        }
      }
    } catch (IllegalAccessException exception) {
      log.warn("Exception encountered while updating account preference for accountId: {} ", accountId, exception);
      return false;
    }
    return false;
  }

  @Override
  @SuppressValidation
  public void updateFeatureFlagsForOnPremAccount() {
    Optional<Account> onPremAccount = getOnPremAccount();
    if (!onPremAccount.isPresent()) {
      return;
    }
    String featureNames = mainConfiguration.getFeatureNames();
    List<String> enabled = isBlank(featureNames)
        ? emptyList()
        : Splitter.on(',').omitEmptyStrings().trimResults().splitToList(featureNames);
    for (String name : Arrays.stream(FeatureName.values()).map(FeatureName::name).collect(toSet())) {
      if (enabled.contains(name)) {
        featureFlagService.enableAccount(FeatureName.valueOf(name), onPremAccount.get().getUuid());
      }
    }
    if (enabled.contains("NEXT_GEN_ENABLED")) {
      updateNextGenEnabled(onPremAccount.get().getUuid(), true);
    }
  }

  @Override
  public List<Account> listAllAccounts() {
    List<Account> accountList = wingsPersistence.createQuery(Account.class, excludeAuthorityCount)
                                    .filter(ApplicationKeys.appId, GLOBAL_APP_ID)
                                    .asList();
    decryptLicenseInfo(accountList);
    return accountList;
  }

  @Override
  public List<Account> listAllAccountsWithoutTheGlobalAccount() {
    Query<Account> query =
        wingsPersistence.createQuery(Account.class, excludeAuthorityCount).filter(ApplicationKeys.appId, GLOBAL_APP_ID);

    query.and(query.criteria(ApplicationKeys.uuid).notEqual(GLOBAL_ACCOUNT_ID));

    return query.asList();
  }

  @Override
  public List<Account> listAllActiveAccounts() {
    List<Account> accountList = wingsPersistence.createQuery(Account.class, excludeAuthorityCount)
                                    .filter(ApplicationKeys.appId, GLOBAL_APP_ID)
                                    .asList();
    return accountList.stream()
        .filter(account -> !licenseService.isAccountExpired(account.getUuid()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Account> listAllAccountWithDefaultsWithoutLicenseInfo() {
    return wingsPersistence.createQuery(Account.class, excludeAuthorityCount)
        .project(ID_KEY2, true)
        .project(AccountKeys.accountName, true)
        .project(AccountKeys.companyName, true)
        .filter(ApplicationKeys.appId, GLOBAL_APP_ID)
        .asList();
  }

  public Set<String> getAccountsWithDisabledHarnessUserGroupAccess() {
    return wingsPersistence.createQuery(Account.class, excludeAuthority)
        .project(ID_KEY2, true)
        .filter(AccountKeys.isHarnessSupportAccessAllowed, Boolean.FALSE)
        .asList()
        .stream()
        .map(Account::getUuid)
        .collect(Collectors.toSet());
  }

  @Override
  public void updateBackgroundJobsDisabled(String accountId, boolean isDisabled) {
    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
    updateOperations.set(AccountKeys.backgroundJobsDisabled, isDisabled);

    wingsPersistence.update(
        wingsPersistence.createQuery(Account.class).filter(Mapper.ID_KEY, accountId), updateOperations);
  }

  @Override
  public PageResponse<Account> getAccounts(PageRequest pageRequest) {
    PageResponse<Account> responses = wingsPersistence.query(Account.class, pageRequest, excludeAuthority);
    List<Account> accounts = responses.getResponse();
    decryptLicenseInfo(accounts);
    return responses;
  }

  @Override
  public Account getByAccountName(String accountName) {
    return wingsPersistence.createQuery(Account.class).filter(AccountKeys.accountName, accountName).get();
  }

  @Override
  public Account getAccountWithDefaults(String accountId) {
    Account account = wingsPersistence.createQuery(Account.class)
                          .project(AccountKeys.accountName, true)
                          .project(AccountKeys.companyName, true)
                          .filter(ID_KEY2, accountId)
                          .get();
    if (account != null) {
      account.setDefaults(settingsService.listAccountDefaults(accountId));
    }
    return account;
  }

  @Override
  public Collection<FeatureFlag> getFeatureFlags(String accountId) {
    return Arrays.stream(FeatureName.values())
        .map(featureName
            -> FeatureFlag.builder()
                   .name(featureName.toString())
                   .enabled(featureFlagService.isEnabled(featureName, accountId))
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public boolean disableAccount(String accountId, String migratedToClusterUrl) {
    Account account = get(accountId);
    updateMigratedToClusterUrl(account, migratedToClusterUrl);
    // Also need to prevent all existing users in the migration account from logging in after completion of migration.
    setUserStatusInAccount(accountId, false);
    return setAccountStatusInternal(account, AccountStatus.INACTIVE);
  }

  @Override
  public boolean enableAccount(String accountId) {
    Account account = get(accountId);
    setUserStatusInAccount(accountId, true);
    return setAccountStatusInternal(account, AccountStatus.ACTIVE);
  }

  private void updateMigratedToClusterUrl(Account account, String migratedToClusterUrl) {
    if (isNotEmpty(migratedToClusterUrl)) {
      wingsPersistence.update(account,
          wingsPersistence.createUpdateOperations(Account.class)
              .set(AccountKeys.migratedToClusterUrl, migratedToClusterUrl));
    }
  }

  private void setUserStatusInAccount(String accountId, boolean enable) {
    Query<User> query = wingsPersistence.createQuery(User.class, excludeAuthority).filter(UserKeys.accounts, accountId);
    int count = 0;
    try (HIterator<User> records = new HIterator<>(query.fetch())) {
      for (User user : records) {
        if (userService.canEnableOrDisable(user)) {
          user.setDisabled(!enable);
          wingsPersistence.save(user);
          userService.evictUserFromCache(user.getUuid());
          log.info("User {} has been set to status disabled: {}", user.getEmail(), !enable);
          count++;
        }
      }
    }
    log.info("{} users in account {} has been set to status disabled: {}", count, accountId, !enable);
  }

  @Override
  public boolean isAccountMigrated(String accountId) {
    Account account = getFromCacheWithFallback(accountId);
    if (account != null && account.getLicenseInfo() != null) {
      // Old account have empty 'licenseInfo' field in account. Need special handling of those account.
      return AccountStatus.INACTIVE.equals(account.getLicenseInfo().getAccountStatus())
          && isNotEmpty(account.getMigratedToClusterUrl());
    } else {
      return false;
    }
  }

  @Override
  public boolean isCommunityAccount(String accountId) {
    return getAccountType(accountId).map(AccountType::isCommunity).orElse(false);
  }

  @Override
  public String generateSampleDelegate(String accountId) {
    assertTrialAccount(accountId);
    if (isBlank(mainConfiguration.getSampleTargetEnv())) {
      String err = "Sample target env not configured";
      log.warn(err);
      throw new UnexpectedException(err);
    }

    String script =
        String.format(GENERATE_SAMPLE_DELEGATE_CURL_COMMAND_FORMAT_STRING, mainConfiguration.getSampleTargetEnv(),
            accountId, getAccountIdentifier(accountId), getFromCache(accountId).getAccountKey());
    Logger scriptLogger = LoggerFactory.getLogger("generate-delegate-" + accountId);
    try {
      ProcessExecutor processExecutor = new ProcessExecutor()
                                            .timeout(10, TimeUnit.MINUTES)
                                            .command("/bin/bash", "-c", script)
                                            .readOutput(true)
                                            .redirectOutput(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                scriptLogger.info(line);
                                              }
                                            })
                                            .redirectError(new LogOutputStream() {
                                              @Override
                                              protected void processLine(String line) {
                                                scriptLogger.error(line);
                                              }
                                            });
      int exitCode = processExecutor.execute().getExitValue();
      if (exitCode == 0) {
        return "SUCCESS";
      }
      log.error("Curl script to generate delegate returned non-zero exit code: {}", exitCode);
    } catch (IOException e) {
      log.error("Error executing generate delegate curl command", e);
    } catch (InterruptedException e) {
      log.info("Interrupted", e);
    } catch (TimeoutException e) {
      log.info("Timed out", e);
    }

    String err = "Failed to provision";
    log.warn(err);
    throw new UnexpectedException(err);
  }

  @Override
  public boolean sampleDelegateExists(String accountId) {
    assertTrialAccount(accountId);
    return delegateService.sampleDelegateExists(accountId);
  }

  @Override
  public List<ProvisionStep> sampleDelegateProgress(String accountId) {
    assertTrialAccount(accountId);

    if (isBlank(mainConfiguration.getSampleTargetStatusHost())) {
      String err = "Sample target status host not configured";
      log.warn(err);
      throw new UnexpectedException(err);
    }

    try {
      String url = String.format(SAMPLE_DELEGATE_STATUS_ENDPOINT_FORMAT_STRING,
          mainConfiguration.getSampleTargetStatusHost(), getAccountIdentifier(accountId));
      log.info("Fetching delegate provisioning progress for account {} from {}", accountId, url);
      String result = Http.getResponseStringFromUrl(url, 30, 10).trim();
      if (isNotEmpty(result)) {
        log.info("Provisioning progress for account {}: {}", accountId, result);
        if (result.contains("<title>404 Not Found</title>")) {
          return singletonList(ProvisionStep.builder().step("Provisioning Started").done(false).build());
        }
        List<ProvisionStep> steps = new ArrayList<>();
        for (JsonElement element : new JsonParser().parse(result).getAsJsonArray()) {
          JsonObject jsonObject = element.getAsJsonObject();
          steps.add(ProvisionStep.builder()
                        .step(jsonObject.get(ProvisionStepKeys.step).getAsString())
                        .done(jsonObject.get(ProvisionStepKeys.done).getAsBoolean())
                        .build());
        }
        return steps;
      }
      throw new UnexpectedException(String.format("Empty provisioning result for account %s", accountId));
    } catch (SocketTimeoutException e) {
      // Timed out for some reason. Return empty list to indicate unknown progress. UI can ignore and try again.
      log.info("Timed out getting progress. Returning empty list.");
      return new ArrayList<>();
    } catch (IOException e) {
      throw new UnexpectedException(
          String.format("Exception in fetching delegate provisioning progress for account %s", accountId), e);
    }
  }

  private void assertTrialAccount(String accountId) {
    Account account = getFromCache(accountId);

    if (!AccountType.TRIAL.equals(account.getLicenseInfo().getAccountType())) {
      String err = "Not a trial account";
      log.warn(err);
      throw new InvalidRequestException(err);
    }
  }

  private boolean setAccountStatusInternal(Account account, String accountStatus) {
    String accountId = account.getUuid();
    if (!AccountStatus.isValid(accountStatus)) {
      throw new InvalidArgumentsException("Invalid account status: " + accountStatus, USER);
    }

    if (AccountStatus.INACTIVE.equals(accountStatus)) {
      updateDeploymentFreeze(accountId, true);
      deleteQuartzJobs(accountId);
    } else if (AccountStatus.ACTIVE.equals(accountStatus)) {
      updateDeploymentFreeze(accountId, false);
      scheduleQuartzJobs(accountId);
    }

    LicenseInfo newLicenseInfo = account.getLicenseInfo();
    newLicenseInfo.setAccountStatus(accountStatus);
    licenseService.updateAccountLicense(accountId, newLicenseInfo);

    log.info("Updated status for account {}, new status is {}", accountId, accountStatus);
    return true;
  }

  private void updateDeploymentFreeze(String accountId, boolean deploymentFreezeStatus) {
    if (governanceFeature.isAvailableForAccount(accountId)) {
      setDeploymentFreeze(accountId, deploymentFreezeStatus);
    }
  }

  @Override
  public boolean setAuthenticationMechanism(String accountId, AuthenticationMechanism authenticationMechanism) {
    Account account = get(accountId);
    wingsPersistence.update(account,
        wingsPersistence.createUpdateOperations(Account.class)
            .set(AccountKeys.authenticationMechanism, authenticationMechanism));

    return true;
  }

  private void setDeploymentFreeze(String accountId, boolean freeze) {
    GovernanceConfig governanceConfig = governanceConfigService.get(accountId);
    if (governanceConfig == null) {
      governanceConfig = GovernanceConfig.builder().accountId(accountId).deploymentFreeze(freeze).build();
      wingsPersistence.save(governanceConfig);
    } else {
      governanceConfig.setDeploymentFreeze(freeze);
      governanceConfigService.upsert(accountId, governanceConfig);
    }
    log.info("Set deployment freeze for account {} to: {}", accountId, freeze);
  }

  private void scheduleAccountLevelJobs(String accountId) {
    // Schedule default account level jobs.
    AlertCheckJob.add(jobScheduler, accountId);
    InstanceStatsCollectorJob.add(jobScheduler, accountId);
    LimitVicinityCheckerJob.add(jobScheduler, accountId);
    segmentGroupEventJobService.scheduleJob(accountId);
  }

  private void scheduleQuartzJobs(String accountId) {
    scheduleAccountLevelJobs(accountId);

    List<String> appIds = appService.getAppIdsByAccountId(accountId);

    // 2. ScheduledTriggerJob
    List<Trigger> triggers = getAllScheduledTriggersForAccount(appIds);
    for (Trigger trigger : triggers) {
      // Scheduled triggers is using the cron expression as trigger. No need to add special delay.
      ScheduledTriggerJob.add(jobScheduler, accountId, trigger.getAppId(), trigger.getUuid(), trigger);
    }

    // 3. LdapGroupSyncJob
    List<LdapSettings> ldapSettings = getAllLdapSettingsForAccount(accountId);
    for (LdapSettings ldapSetting : ldapSettings) {
      LdapGroupSyncJob.add(jobScheduler, accountId, ldapSetting.getUuid());
      ldapGroupSyncJobHelper.syncJob(ldapSetting);
    }
    log.info("Started all background quartz jobs for account {}", accountId);
  }

  private void deleteQuartzJobs(String accountId) {
    // 1. Account level jobs
    AlertCheckJob.delete(jobScheduler, accountId);
    InstanceStatsCollectorJob.delete(jobScheduler, accountId);
    LimitVicinityCheckerJob.delete(jobScheduler, accountId);

    List<String> appIds = appService.getAppIdsByAccountId(accountId);

    // 2. ScheduledTriggerJob
    List<Trigger> triggers = getAllScheduledTriggersForAccount(appIds);
    for (Trigger trigger : triggers) {
      // Scheduled triggers is using the cron expression as trigger. No need to add special delay.
      ScheduledTriggerJob.delete(jobScheduler, trigger.getUuid());
    }

    // 3. LdapGroupSyncJob
    List<LdapSettings> ldapSettings = getAllLdapSettingsForAccount(accountId);
    for (LdapSettings ldapSetting : ldapSettings) {
      LdapGroupSyncJob.delete(jobScheduler, ssoSettingService, accountId, ldapSetting.getUuid());
    }
    log.info("Stopped all background quartz jobs for account {}", accountId);
  }

  private List<Trigger> getAllScheduledTriggersForAccount(List<String> appIds) {
    List<Trigger> triggers = new ArrayList<>();
    Query<Trigger> query = wingsPersistence.createQuery(Trigger.class).filter("appId in", appIds);
    try (HIterator<Trigger> iterator = new HIterator<>(query.fetch())) {
      for (Trigger trigger : iterator) {
        if (trigger.getCondition().getConditionType() == TriggerConditionType.SCHEDULED) {
          triggers.add(trigger);
        }
      }
    }

    return triggers;
  }

  private List<LdapSettings> getAllLdapSettingsForAccount(String accountId) {
    List<LdapSettings> ldapSettings = new ArrayList<>();
    Query<LdapSettings> query = wingsPersistence.createQuery(LdapSettings.class)
                                    .filter(LdapSettingsKeys.accountId, accountId)
                                    .filter("type", SSOType.LDAP);
    Iterator<LdapSettings> iterator = query.iterator();
    while (iterator.hasNext()) {
      ldapSettings.add(iterator.next());
    }

    return ldapSettings;
  }

  private void createDefaultNotificationGroup(Account account, Role role) {
    String name = role.getRoleType().getDisplayName();
    // check if the notification group name exists
    List<NotificationGroup> existingGroups =
        notificationSetupService.listNotificationGroups(account.getUuid(), role, name);
    if (isEmpty(existingGroups)) {
      log.info("Creating default {} notification group {} for account {}", ACCOUNT_ADMIN.getDisplayName(), name,
          account.getAccountName());
      NotificationGroup notificationGroup = aNotificationGroup()
                                                .withAppId(account.getAppId())
                                                .withAccountId(account.getUuid())
                                                .withRole(role)
                                                .withName(name)
                                                .withEditable(false)
                                                .withDefaultNotificationGroupForAccount(false)
                                                .build();

      // Reason we are setting withDefaultNotificationGroupForAccount(false), is We have also added a concept of default
      // group, where user can mark any editable notificationGroup as default (1 per account). This default group will
      // be selected for sending notifications in case of workflow execution. If no default group is set, then
      // automatically,  "ACCOUNT_ADMIN" notification group is selected. So for "ACCOUNT_ADMIN" isDefault = false, as we
      // want to first check for any explicitly set default notification group
      notificationSetupService.createNotificationGroup(notificationGroup);
    } else {
      log.info("Default notification group already exists for role {} and account {}", ACCOUNT_ADMIN.getDisplayName(),
          account.getAccountName());
    }
  }

  private void createSystemAppContainers(Account account) {
    List<SystemCatalog> systemCatalogs =
        systemCatalogService.list(aPageRequest()
                                      .addFilter(SystemCatalog.APP_ID_KEY2, EQ, GLOBAL_APP_ID)
                                      .addFilter("catalogType", EQ, APPSTACK)
                                      .build());
    log.debug("Creating default system app containers  ");
    for (SystemCatalog systemCatalog : systemCatalogs) {
      AppContainer appContainer = anAppContainer()
                                      .withAccountId(account.getUuid())
                                      .withAppId(systemCatalog.getAppId())
                                      .withChecksum(systemCatalog.getChecksum())
                                      .withChecksumType(systemCatalog.getChecksumType())
                                      .withFamily(systemCatalog.getFamily())
                                      .withStackRootDirectory(systemCatalog.getStackRootDirectory())
                                      .withFileName(systemCatalog.getFileName())
                                      .withFileUuid(systemCatalog.getFileUuid())
                                      .withFileType(systemCatalog.getFileType())
                                      .withSize(systemCatalog.getSize())
                                      .withName(systemCatalog.getName())
                                      .withSystemCreated(true)
                                      .withDescription(systemCatalog.getNotes())
                                      .withHardened(systemCatalog.isHardened())
                                      .withVersion(systemCatalog.getVersion())
                                      .build();
      try {
        appContainerService.save(appContainer);
      } catch (Exception e) {
        log.warn("Error while creating system app container " + appContainer, e);
      }
    }
  }

  @Override
  public boolean isFeatureFlagEnabled(String featureName, String accountId) {
    for (FeatureName feature : FeatureName.values()) {
      if (feature.name().equals(featureName)) {
        return featureFlagService.isEnabled(FeatureName.valueOf(featureName), accountId);
      }
    }
    return false;
  }

  @Override
  public List<Service> getServicesBreadCrumb(String accountId, User user) {
    PageRequest<String> request = aPageRequest().withOffset("0").withLimit(UNLIMITED_PAGE_SIZE).build();
    PageResponse<CVEnabledService> response = getServices(accountId, user, request, null);
    if (response != null && isNotEmpty(response.getResponse())) {
      List<Service> serviceList = new ArrayList<>();
      for (CVEnabledService cvEnabledService : response.getResponse()) {
        serviceList.add(Service.builder()
                            .name(cvEnabledService.getService().getName())
                            .uuid(cvEnabledService.getService().getUuid())
                            .build());
      }
      return serviceList;
    }
    return new ArrayList<>();
  }

  @Override
  public PageResponse<CVEnabledService> getServices(
      String accountId, User user, PageRequest<String> request, String serviceId) {
    if (user == null) {
      log.info("User is null when requesting for Services info. Returning null");
    }
    int offset = Integer.parseInt(request.getOffset());
    if (isNotEmpty(request.getLimit()) && request.getLimit().equals(UNLIMITED_PAGE_SIZE)) {
      request.setLimit(String.valueOf(Integer.MAX_VALUE));
    }
    int limit = Integer.parseInt(request.getLimit() != null ? request.getLimit() : "0");
    limit = limit == 0 ? SIZE_PER_SERVICES_REQUEST : limit;

    // fetch the list of apps, services and environments that the user has permissions to.
    Map<String, AppPermissionSummary> userAppPermissions =
        authService.getUserPermissionInfo(accountId, user, false).getAppPermissionMapInternal();

    final List<String> services = new ArrayList<>();
    Set<EnvInfo> envInfoSet = new HashSet<>();
    for (AppPermissionSummary summary : userAppPermissions.values()) {
      if (isNotEmpty(summary.getServicePermissions())) {
        services.addAll(summary.getServicePermissions().get(Action.READ));
      }
      if (isNotEmpty(summary.getEnvPermissions())) {
        envInfoSet.addAll(summary.getEnvPermissions().get(Action.READ));
      }
    }

    Set<String> allowedEnvs = new HashSet<>();
    for (EnvInfo envInfo : envInfoSet) {
      allowedEnvs.add(envInfo.getEnvId());
    }

    // Fetch and build he cvConfigs for the service/env that the user has permissions to, in parallel.
    final List<CVEnabledService> cvEnabledServices = Collections.synchronizedList(new ArrayList<>());
    if (serviceId != null) {
      // in thiscase we want to get the data only for this service.
      services.clear();
      services.add(serviceId);
    }

    List<CVConfiguration> cvConfigurationList =
        wingsPersistence.createQuery(CVConfiguration.class, excludeAuthorityCount)
            .field("appId")
            .in(userAppPermissions.keySet())
            .asList();
    if (cvConfigurationList == null) {
      return null;
    }
    Map<String, List<CVConfiguration>> serviceCvConfigMap = new HashMap<>();
    cvConfigurationList.forEach(cvConfiguration -> {
      String serviceIdOfConfig = cvConfiguration.getServiceId();
      if (!cvConfiguration.isWorkflowConfig() && services.contains(serviceIdOfConfig)
          && allowedEnvs.contains(cvConfiguration.getEnvId())) {
        cvConfigurationService.fillInServiceAndConnectorNames(cvConfiguration);
        List<CVConfiguration> configList = new ArrayList<>();
        if (serviceCvConfigMap.containsKey(serviceIdOfConfig)) {
          configList = serviceCvConfigMap.get(serviceIdOfConfig);
        }
        if (cvConfiguration.isEnabled24x7()) {
          configList.add(cvConfiguration);
          serviceCvConfigMap.put(serviceIdOfConfig, configList);
        }
      }
    });

    serviceCvConfigMap.forEach((configServiceId, cvConfigList) -> {
      String appId = cvConfigList.get(0).getAppId();
      String appName = cvConfigList.get(0).getAppName();
      String serviceName = cvConfigList.get(0).getServiceName();

      cvEnabledServices.add(CVEnabledService.builder()
                                .service(Service.builder().uuid(configServiceId).name(serviceName).appId(appId).build())
                                .appName(appName)
                                .appId(appId)
                                .cvConfig(cvConfigList)
                                .build());
    });

    // Wrap into a pageResponse and return
    int totalSize = cvEnabledServices.size();
    List<CVEnabledService> returnList = cvEnabledServices;
    if (offset < returnList.size()) {
      int endIndex = Math.min(returnList.size(), offset + limit);
      returnList = returnList.subList(offset, endIndex);
    } else {
      returnList = new ArrayList<>();
    }

    if (isNotEmpty(returnList)) {
      return PageResponseBuilder.aPageResponse()
          .withResponse(returnList)
          .withOffset(String.valueOf(offset + returnList.size()))
          .withTotal(totalSize)
          .build();
    }
    return PageResponseBuilder.aPageResponse()
        .withResponse(new ArrayList<>())
        .withOffset(String.valueOf(offset + returnList.size()))
        .withTotal(totalSize)
        .build();
  }

  @Override
  public Set<String> getWhitelistedDomains(String accountId) {
    Account account = get(accountId);
    return account.getWhitelistedDomains();
  }

  @Override
  public Account updateWhitelistedDomains(String accountId, Set<String> whitelistedDomains) {
    whitelistedDomains =
        whitelistedDomains.stream().map(String::trim).filter(EmptyPredicate::isNotEmpty).collect(Collectors.toSet());

    // Filter the valid domains after trimming trailing spaces
    Set<String> validDomains =
        whitelistedDomains.stream().filter(DomainValidator.getInstance()::isValid).collect(Collectors.toSet());

    if (whitelistedDomains.size() != validDomains.size()) {
      throw new WingsException("Invalid domain name");
    }

    UpdateOperations<Account> whitelistedDomainsUpdateOperations =
        wingsPersistence.createUpdateOperations(Account.class);
    setUnset(whitelistedDomainsUpdateOperations, AccountKeys.whitelistedDomains, validDomains);
    wingsPersistence.update(wingsPersistence.createQuery(Account.class).filter(Mapper.ID_KEY, accountId),
        whitelistedDomainsUpdateOperations);
    return get(accountId);
  }

  @Override
  public boolean updateAccountName(String accountId, String accountName) {
    Account account = getFromCache(accountId);
    if (account == null) {
      log.warn("accountId={} doesn't exist", accountId);
      return false;
    }
    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
    updateOperations.set(AccountKeys.accountName, accountName);

    try {
      UpdateResults updateResults = wingsPersistence.update(
          wingsPersistence.createQuery(Account.class).filter(Mapper.ID_KEY, accountId), updateOperations);
      if (updateResults != null && updateResults.getUpdatedCount() > 0) {
        log.info("Successfully updated account name to {} for accountId = {} ", accountName, accountId);
        return true;
      }
      log.info("Failed to update account name to {} for accountId = {} ", accountName, accountId);
      return false;
    } catch (DuplicateKeyException duplicateKeyException) {
      log.error("Account name already exists", duplicateKeyException);
      throw new InvalidRequestException("Account name already exists", INVALID_REQUEST, USER);
    }
  }

  @Override
  public boolean updateCompanyName(String accountId, String companyName) {
    Account account = getFromCache(accountId);
    if (account == null) {
      log.warn("accountId={} doesn't exist", accountId);
      return false;
    }
    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
    updateOperations.set(AccountKeys.companyName, companyName);
    UpdateResults updateResults = wingsPersistence.update(
        wingsPersistence.createQuery(Account.class).filter(Mapper.ID_KEY, accountId), updateOperations);
    if (updateResults != null && updateResults.getUpdatedCount() > 0) {
      log.info("Successfully updated company name to {} for accountId = {} ", companyName, accountId);
      return true;
    }
    log.info("Failed to update company name to {} for accountId = {} ", companyName, accountId);
    return false;
  }

  @Override
  public Account updateAccountName(String accountId, String accountName, String companyName) {
    notNullCheck("Account name can not be set to null!", accountName);
    UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);
    updateOperations.set(AccountKeys.accountName, accountName);
    if (isNotEmpty(companyName)) {
      updateOperations.set(AccountKeys.companyName, companyName);
    }
    wingsPersistence.update(
        wingsPersistence.createQuery(Account.class).filter(Mapper.ID_KEY, accountId), updateOperations);
    return get(accountId);
  }

  @Override
  public AccountSettingsResponse getAuthSettingsByAccountId(String accountId) {
    Account account = get(accountId);
    AuthenticationMechanism authenticationMechanism = account.getAuthenticationMechanism();
    if (authenticationMechanism == null) {
      authenticationMechanism = AuthenticationMechanism.USER_PASSWORD;
    }
    Set<String> whitelistedDomains = account.getWhitelistedDomains();
    OauthSettings oauthSettings = ssoSettingService.getOauthSettingsByAccountId(accountId);
    Set<OauthProviderType> oauthProviderTypes = oauthSettings == null ? null : oauthSettings.getAllowedProviders();
    return AccountSettingsResponse.builder()
        .authenticationMechanism(authenticationMechanism)
        .allowedDomains(whitelistedDomains)
        .oauthProviderTypes(oauthProviderTypes)
        .build();
  }

  @Override
  public boolean postCustomEvent(String accountId, AccountEvent accountEvent, boolean oneTimeOnly, boolean trialOnly) {
    eventPublishHelper.publishAccountEvent(accountId, accountEvent, oneTimeOnly, trialOnly);
    return true;
  }

  @Override
  public boolean isSSOEnabled(Account account) {
    return (account.getAuthenticationMechanism() != null)
        && (account.getAuthenticationMechanism() != AuthenticationMechanism.USER_PASSWORD);
  }

  /**
   * Checks whether the subdomain URL is taken by any other account or not
   *
   * @param subdomainUrl Object of type SubdomainUrl
   * @return true if subdomain URL is duplicate otherwise false
   */
  public boolean checkDuplicateSubdomainUrl(SubdomainUrl subdomainUrl) {
    return wingsPersistence.createQuery(Account.class).filter(AccountKeys.subdomainUrl, subdomainUrl.getUrl()).get()
        != null;
  }

  /**
   * Takes a User ID and does the following checks before adding subdomainUrl to the account
   * Sanity check on Url provided
   *
   * @param subdomainUrl subdomain URL object
   * @return boolean
   */
  @Override
  public boolean validateSubdomainUrl(SubdomainUrl subdomainUrl) {
    // Sanity check for subdomain URL
    String[] schemes = {"https"};
    UrlValidator urlValidator = new UrlValidator(schemes);
    return urlValidator.isValid(subdomainUrl.getUrl());
  }

  /**
   * Function to set subdomain Url of the account
   *
   * @param account      Account Object
   * @param subdomainUrl Subdomain URL
   */
  @Override
  public void setSubdomainUrl(Account account, SubdomainUrl subdomainUrl) {
    UpdateOperations<Account> updateOperation = wingsPersistence.createUpdateOperations(Account.class);
    updateOperation.set(AccountKeys.subdomainUrl, subdomainUrl.getUrl());
    wingsPersistence.update(account, updateOperation);
  }

  /**
   * Function to add subdomain URL
   *
   * @param userId
   * @param accountId
   * @param subDomainUrl
   * @return boolean
   */
  @Override
  public Boolean addSubdomainUrl(String userId, String accountId, SubdomainUrl subDomainUrl) {
    Account account = get(accountId);
    if (!isBlank(account.getSubdomainUrl())) {
      throw new InvalidRequestException("Account already has subdomain URL. Updating it is not allowed");
    }

    // Check if the user is a part of Harness User Group
    if (!harnessUserGroupService.isHarnessSupportUser(userId)) {
      throw new UnauthorizedException("User is not authorized to add subdomain URL", USER);
    }

    // Check if URL is not duplicate
    if (checkDuplicateSubdomainUrl(subDomainUrl)) {
      throw new InvalidArgumentsException("Subdomain URL is already taken", USER);
    }

    // Check if URL is valid
    if (!validateSubdomainUrl(subDomainUrl)) {
      throw new InvalidArgumentsException("Subdomain URL provided is invalid", USER);
    }

    setSubdomainUrl(get(accountId), subDomainUrl);
    return Boolean.TRUE;
  }

  @Override
  public Optional<String> getCeAccountType(String accountId) {
    Account account = getFromCache(accountId);
    if (account == null) {
      log.warn("accountId={} doesn't exist", accountId);
      return Optional.empty();
    }

    CeLicenseInfo licenseInfo = account.getCeLicenseInfo();
    if (null == licenseInfo) {
      log.warn("License info not present for account. accountId={}", accountId);
      return Optional.empty();
    }

    String accountType = licenseInfo.getLicenseType().toString();
    if (!licenseInfo.isValidLicenceType()) {
      log.warn("Invalid account type. accountType={}, accountId={}", accountType, accountId);
      return Optional.empty();
    }

    return Optional.of(accountType);
  }

  @Override
  public void setServiceGuardAccount(String accountId, ServiceGuardLimitDTO serviceGuardLimitDTO) {
    wingsPersistence.updateField(
        Account.class, accountId, AccountKeys.serviceGuardLimit, serviceGuardLimitDTO.getServiceGuardLimit());
  }

  @Override
  public CertRequirement getHttpsCertificateRequirement(String accountId) {
    if (isCertValidationRequired(accountId)) {
      return CertRequirement.UNKNOWN_REQUIREMENT;
    } else {
      return CertRequirement.CERTIFICATE_REQUIRED;
    }
  }

  @Override
  public void ensureDataRetention(List<Class<? extends AccountDataRetentionEntity>> entityClasses) {
    try (AcquiredLock acquiredLock =
             persistentLocker.acquireLock(AccountService.class, "ensureDataRetention", ofHours(6))) {
      if (acquiredLock == null) {
        log.info("We did not get the lock, bail");
        return;
      }

      long now = currentTimeMillis();

      Map<String, Long> accounts = obtainAccountDataRetentionMap();

      Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

      long assureInterval;
      if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
        assureInterval = ofDays(10).toMillis();
      } else if (Calendar.DAY_OF_WEEK == Calendar.SUNDAY) {
        assureInterval = ofDays(30).toMillis();
      } else {
        return;
      }

      long assureTo = now + assureInterval;
      log.info("Correct valid until values if needed for the next {}", assureTo);
      entityClasses.forEach(clz -> accountDataRetentionService.corectValidUntilAccount(clz, accounts, now, assureTo));
    }
  }

  @Override
  public Map<String, Long> obtainAccountDataRetentionMap() {
    List<Account> accountList = wingsPersistence.createQuery(Account.class, excludeAuthority)
                                    .project(AccountKeys.uuid, true)
                                    .project(AccountKeys.dataRetentionDurationMs, true)
                                    .asList();

    Map<String, Long> accounts = new HashMap<>();

    accountList.forEach(account -> {
      accounts.put(account.getUuid(),
          account.getDataRetentionDurationMs() == 0 ? ofDays(183).toMillis() : account.getDataRetentionDurationMs());
    });
    return accounts;
  }

  @Override
  public boolean enableHarnessUserGroupAccess(String accountId) {
    Account account = get(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, account);
    account.setHarnessSupportAccessAllowed(true);
    wingsPersistence.save(account);
    return true;
  }

  @Override
  public boolean disableHarnessUserGroupAccess(String accountId) {
    Account account = get(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, account);
    account.setHarnessSupportAccessAllowed(false);
    wingsPersistence.save(account);
    return true;
  }

  @Override
  public boolean isRestrictedAccessEnabled(String accountId) {
    Account account = get(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, account);
    if (account.isHarnessSupportAccessAllowed()) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isAutoInviteAcceptanceEnabled(String accountId) {
    if (!featureFlagService.isEnabled(AUTO_ACCEPT_SAML_ACCOUNT_INVITES, accountId)) {
      // This feature is restricted only to a certain accounts
      return false;
    }

    Account account = get(accountId);

    if (!AuthenticationMechanism.SAML.equals(account.getAuthenticationMechanism())) {
      // Currently this provision is only for SAML authenticated accounts
      return false;
    }

    List<SSOSettings> ssoSettings = ssoSettingService.getAllSsoSettings(accountId);
    return ssoSettings.stream().anyMatch(settings -> settings.getType() == SSOType.SAML);
  }

  @Override
  public Void setDefaultExperience(String accountId, DefaultExperience defaultExperience) {
    Account account = getFromCacheWithFallback(accountId);
    notNullCheck("Invalid Account for the given Id: " + accountId, account);
    notNullCheck("Invalid Default Experience: " + defaultExperience, defaultExperience);
    wingsPersistence.updateField(Account.class, accountId, DEFAULT_EXPERIENCE, defaultExperience);
    dbCache.invalidate(Account.class, account.getUuid());
    return null;
  }

  @Override
  public AuthenticationInfo getAuthenticationInfo(String accountId) {
    Account account = getFromCacheWithFallback(accountId);
    if (account == null) {
      throw new InvalidRequestException("Account not found");
    }

    AuthenticationMechanism authenticationMechanism = account.getAuthenticationMechanism();
    AuthenticationInfoBuilder builder =
        AuthenticationInfo.builder().authenticationMechanism(authenticationMechanism).accountId(accountId);
    builder.oauthEnabled(account.isOauthEnabled());
    switch (authenticationMechanism) {
      case SAML:
        SSORequest ssoRequest = samlClientService.generateSamlRequestFromAccount(account, false);
        builder.samlRedirectUrl(ssoRequest.getIdpRedirectUrl());
        break;
      case USER_PASSWORD:
      case OAUTH:
        builder.oauthEnabled(account.isOauthEnabled());
        if (account.isOauthEnabled()) {
          OauthSettings oauthSettings = ssoSettingService.getOauthSettingsByAccountId(accountId);
          builder.oauthProviders(new ArrayList<>(oauthSettings.getAllowedProviders()));
        }
        break;
      case LDAP: // No need to build anything extra for the response.
      default:
        // Nothing to do by default
    }
    return builder.build();
  }

  @Override
  public boolean updateRingName(String accountId, String ringName) {
    Account account = getFromCache(accountId);
    if (account == null) {
      log.warn("accountId={} doesn't exist", accountId);
      return false;
    }
    UpdateOperations<Account> updateOperations = persistence.createUpdateOperations(Account.class);
    updateOperations.set(AccountKeys.ringName, ringName);
    UpdateResults updateResults =
        persistence.update(persistence.createQuery(Account.class).filter(Mapper.ID_KEY, accountId), updateOperations);
    if (updateResults != null && updateResults.getUpdatedCount() > 0) {
      log.info("Successfully updated ring name to {} for accountId = {} ", ringName, accountId);
      return true;
    }
    log.info("Failed to update ring name to {} for accountId = {} ", ringName, accountId);
    return false;
  }
}
