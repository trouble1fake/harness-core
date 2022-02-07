/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeLevel.ACCOUNT;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeLevel.ORGANIZATION;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeLevel.PROJECT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_GROUP_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_TOPIC_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;
import static io.harness.lock.DistributedLockImplementation.MONGO;

import io.harness.AccessControlClientModule;
import io.harness.accesscontrol.acl.api.ACLResource;
import io.harness.accesscontrol.acl.api.ACLResourceImpl;
import io.harness.accesscontrol.aggregator.AggregatorStackDriverMetricsPublisherImpl;
import io.harness.accesscontrol.aggregator.consumers.AccessControlChangeEventFailureHandler;
import io.harness.accesscontrol.commons.events.EventConsumer;
import io.harness.accesscontrol.commons.iterators.AccessControlIteratorsConfig;
import io.harness.accesscontrol.commons.notifications.NotificationConfig;
import io.harness.accesscontrol.commons.outbox.AccessControlOutboxEventHandler;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.preference.AccessControlPreferenceModule;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.PrincipalValidator;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountServiceImpl;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountValidator;
import io.harness.accesscontrol.principals.serviceaccounts.events.ServiceAccountEventConsumer;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupService;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupServiceImpl;
import io.harness.accesscontrol.principals.usergroups.UserGroupValidator;
import io.harness.accesscontrol.principals.usergroups.events.UserGroupEventConsumer;
import io.harness.accesscontrol.principals.users.HarnessUserService;
import io.harness.accesscontrol.principals.users.HarnessUserServiceImpl;
import io.harness.accesscontrol.principals.users.UserValidator;
import io.harness.accesscontrol.principals.users.events.UserMembershipEventConsumer;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupServiceImpl;
import io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupEventConsumer;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentHandler;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentService;
import io.harness.accesscontrol.roleassignments.privileged.PrivilegedRoleAssignmentServiceImpl;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDao;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentDaoImpl;
import io.harness.accesscontrol.roleassignments.validation.RoleAssignmentActionValidator;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeServiceImpl;
import io.harness.accesscontrol.scopes.harness.events.ScopeEventConsumer;
import io.harness.accesscontrol.support.SupportService;
import io.harness.accesscontrol.support.SupportServiceImpl;
import io.harness.accesscontrol.support.persistence.SupportPreferenceDao;
import io.harness.accesscontrol.support.persistence.SupportPreferenceDaoImpl;
import io.harness.account.AccountClientModule;
import io.harness.aggregator.AggregatorModule;
import io.harness.aggregator.consumers.ChangeEventFailureHandler;
import io.harness.aggregator.consumers.RoleAssignmentCRUDEventHandler;
import io.harness.aggregator.consumers.UserGroupCRUDEventHandler;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.ff.FeatureFlagClientModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.metrics.modules.MetricsModule;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.organization.OrganizationClientModule;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.project.ProjectClientModule;
import io.harness.redis.RedisConfig;
import io.harness.resourcegroupclient.ResourceGroupClientModule;
import io.harness.serviceaccount.ServiceAccountClientModule;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.token.TokenClientModule;
import io.harness.usergroups.UserGroupClientModule;
import io.harness.usermembership.UserMembershipClientModule;
import io.harness.version.VersionModule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.redisson.api.RedissonClient;
import ru.vyarus.guice.validator.ValidationModule;

@OwnedBy(PL)
@Slf4j
public class AccessControlModule extends AbstractModule {
  private static AccessControlModule instance;
  private final AccessControlConfiguration config;

  private AccessControlModule(AccessControlConfiguration config) {
    this.config = config;
  }

  public static synchronized AccessControlModule getInstance(AccessControlConfiguration config) {
    if (instance == null) {
      instance = new AccessControlModule(config);
    }
    return instance;
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return config.getDistributedLockImplementation() == null ? MONGO : config.getDistributedLockImplementation();
  }

  @Provides
  @Singleton
  NotificationConfig notificationConfig() {
    return config.getNotificationConfig();
  }

  @Provides
  @Named("lock")
  @Singleton
  public RedisConfig redisLockConfig() {
    return config.getRedisLockConfig();
  }

  @Provides
  @Named("eventsFrameworkRedissonClient")
  @Singleton
  public RedissonClient getRedissonClient() {
    RedisConfig redisConfig = config.getEventsConfig().getRedisConfig();
    if (config.getEventsConfig().isEnabled()) {
      return RedisUtils.getClient(redisConfig);
    }
    return null;
  }

  @Provides
  @Named(ENTITY_CRUD)
  @Singleton
  public Consumer getEntityCrudConsumer(
      @Nullable @Named("eventsFrameworkRedissonClient") RedissonClient redissonClient) {
    RedisConfig redisConfig = config.getEventsConfig().getRedisConfig();
    if (!config.getEventsConfig().isEnabled()) {
      return NoOpConsumer.of(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME);
    }
    return RedisConsumer.of(ENTITY_CRUD, ACCESS_CONTROL_SERVICE.getServiceId(), redissonClient,
        ENTITY_CRUD_MAX_PROCESSING_TIME, ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace());
  }

  @Provides
  @Named(USERMEMBERSHIP)
  @Singleton
  public Consumer getUserMembershipConsumer(
      @Nullable @Named("eventsFrameworkRedissonClient") RedissonClient redissonClient) {
    RedisConfig redisConfig = config.getEventsConfig().getRedisConfig();
    if (!config.getEventsConfig().isEnabled()) {
      return NoOpConsumer.of(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME);
    }
    return RedisConsumer.of(USERMEMBERSHIP, ACCESS_CONTROL_SERVICE.getServiceId(), redissonClient,
        Duration.ofMinutes(10), 3, redisConfig.getEnvNamespace());
  }

  @Provides
  public AccessControlIteratorsConfig getIteratorsConfig() {
    return config.getIteratorsConfig();
  }

  @Override
  protected void configure() {
    install(VersionModule.getInstance());
    install(PrimaryVersionManagerModule.getInstance());
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        5, 100, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));
    install(ExecutorModule.getInstance());
    install(PersistentLockModule.getInstance());
    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();
    install(new ValidationModule(validatorFactory));

    install(
        new ServiceAccountClientModule(config.getServiceAccountClientConfiguration().getServiceAccountServiceConfig(),
            config.getServiceAccountClientConfiguration().getServiceAccountServiceSecret(),
            ACCESS_CONTROL_SERVICE.getServiceId()));

    install(AccessControlClientModule.getInstance(
        config.getAccessControlClientConfiguration(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new ResourceGroupClientModule(config.getResourceGroupClientConfiguration().getResourceGroupServiceConfig(),
        config.getResourceGroupClientConfiguration().getResourceGroupServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new UserGroupClientModule(config.getUserGroupClientConfiguration().getUserGroupServiceConfig(),
        config.getUserGroupClientConfiguration().getUserGroupServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new UserMembershipClientModule(config.getUserClientConfiguration().getUserServiceConfig(),
        config.getUserClientConfiguration().getUserServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new AuditClientModule(config.getAuditClientConfig(), config.getDefaultServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId(), config.isEnableAudit()));

    install(new AccountClientModule(config.getAccountClientConfiguration().getAccountServiceConfig(),
        config.getAccountClientConfiguration().getAccountServiceSecret(), ACCESS_CONTROL_SERVICE.toString()));

    install(new ProjectClientModule(config.getProjectClientConfiguration().getProjectServiceConfig(),
        config.getProjectClientConfiguration().getProjectServiceSecret(), ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new OrganizationClientModule(config.getOrganizationClientConfiguration().getOrganizationServiceConfig(),
        config.getOrganizationClientConfiguration().getOrganizationServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId()));

    install(new TokenClientModule(config.getServiceAccountClientConfiguration().getServiceAccountServiceConfig(),
        config.getServiceAccountClientConfiguration().getServiceAccountServiceSecret(),
        ACCESS_CONTROL_SERVICE.getServiceId()));

    install(
        FeatureFlagClientModule.getInstance(config.getFeatureFlagClientConfiguration().getFeatureFlagServiceConfig(),
            config.getFeatureFlagClientConfiguration().getFeatureFlagServiceSecret(),
            ACCESS_CONTROL_SERVICE.getServiceId()));

    install(
        EnforcementClientModule.getInstance(config.getOrganizationClientConfiguration().getOrganizationServiceConfig(),
            config.getOrganizationClientConfiguration().getOrganizationServiceSecret(),
            ACCESS_CONTROL_SERVICE.getServiceId(), config.getEnforcementClientConfiguration()));

    install(new TransactionOutboxModule(config.getOutboxPollConfig(), ACCESS_CONTROL_SERVICE.getServiceId(),
        config.getAggregatorConfiguration().isExportMetricsToStackDriver()));
    install(NGMigrationSdkModule.getInstance());

    install(AccessControlPersistenceModule.getInstance(config.getMongoConfig()));
    install(AccessControlCoreModule.getInstance());
    install(AccessControlPreferenceModule.getInstance());

    if (config.getAggregatorConfiguration().isEnabled()) {
      install(AggregatorModule.getInstance(config.getAggregatorConfiguration()));
      bind(ChangeEventFailureHandler.class).to(AccessControlChangeEventFailureHandler.class);
    }
    bind(TimeLimiter.class).toInstance(HTimeLimiter.create());

    bind(OutboxEventHandler.class).to(AccessControlOutboxEventHandler.class);

    MapBinder<String, ScopeLevel> scopesByKey = MapBinder.newMapBinder(binder(), String.class, ScopeLevel.class);
    scopesByKey.addBinding(ACCOUNT.toString()).toInstance(ACCOUNT);
    scopesByKey.addBinding(ORGANIZATION.toString()).toInstance(ORGANIZATION);
    scopesByKey.addBinding(PROJECT.toString()).toInstance(PROJECT);

    bind(HarnessScopeService.class).to(HarnessScopeServiceImpl.class);

    bind(HarnessResourceGroupService.class).to(HarnessResourceGroupServiceImpl.class);
    bind(HarnessUserGroupService.class).to(HarnessUserGroupServiceImpl.class);
    bind(HarnessUserService.class).to(HarnessUserServiceImpl.class);
    bind(HarnessServiceAccountService.class).to(HarnessServiceAccountServiceImpl.class);

    bind(UserGroupCRUDEventHandler.class).to(PrivilegedRoleAssignmentHandler.class);
    bind(RoleAssignmentCRUDEventHandler.class).to(PrivilegedRoleAssignmentHandler.class);

    MapBinder<PrincipalType, PrincipalValidator> validatorByPrincipalType =
        MapBinder.newMapBinder(binder(), PrincipalType.class, PrincipalValidator.class);
    validatorByPrincipalType.addBinding(USER).to(UserValidator.class);
    validatorByPrincipalType.addBinding(USER_GROUP).to(UserGroupValidator.class);
    validatorByPrincipalType.addBinding(SERVICE_ACCOUNT).to(ServiceAccountValidator.class);

    Multibinder<EventConsumer> entityCrudEventConsumers =
        Multibinder.newSetBinder(binder(), EventConsumer.class, Names.named(ENTITY_CRUD));
    entityCrudEventConsumers.addBinding().to(ResourceGroupEventConsumer.class);
    entityCrudEventConsumers.addBinding().to(UserGroupEventConsumer.class);
    entityCrudEventConsumers.addBinding().to(ServiceAccountEventConsumer.class);
    entityCrudEventConsumers.addBinding().to(ScopeEventConsumer.class);

    Multibinder<EventConsumer> userMembershipEventConsumers =
        Multibinder.newSetBinder(binder(), EventConsumer.class, Names.named(USERMEMBERSHIP));
    userMembershipEventConsumers.addBinding().to(UserMembershipEventConsumer.class);

    binder()
        .bind(new TypeLiteral<HarnessActionValidator<RoleAssignment>>() {})
        .annotatedWith(Names.named(RoleAssignmentDTO.MODEL_NAME))
        .to(RoleAssignmentActionValidator.class);

    bind(SupportPreferenceDao.class).to(SupportPreferenceDaoImpl.class);
    bind(SupportService.class).to(SupportServiceImpl.class);

    bind(PrivilegedRoleAssignmentDao.class).to(PrivilegedRoleAssignmentDaoImpl.class);
    bind(PrivilegedRoleAssignmentService.class).to(PrivilegedRoleAssignmentServiceImpl.class);

    bind(ACLResource.class).to(ACLResourceImpl.class);

    if (config.getAggregatorConfiguration().isExportMetricsToStackDriver()) {
      install(new MetricsModule());
      bind(MetricsPublisher.class).to(AggregatorStackDriverMetricsPublisherImpl.class).in(Scopes.SINGLETON);
    } else {
      log.info("No configuration provided for Stack Driver, aggregator metrics will not be recorded");
    }
  }
}
