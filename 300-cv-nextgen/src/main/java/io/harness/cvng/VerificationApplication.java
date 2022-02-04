/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.AuthorizationServiceHeader.BEARER;
import static io.harness.AuthorizationServiceHeader.DEFAULT;
import static io.harness.AuthorizationServiceHeader.IDENTITY_SERVICE;
import static io.harness.cvng.cdng.services.impl.CVNGNotifyEventListener.CVNG_ORCHESTRATION;
import static io.harness.cvng.migration.beans.CVNGSchema.CVNGMigrationStatus.RUNNING;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.security.ServiceTokenGenerator.VERIFICATION_SERVICE_SECRET;

import static com.google.inject.matcher.Matchers.not;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.ModuleType;
import io.harness.PipelineServiceUtilityModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheModule;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.Activity.ActivityKeys;
import io.harness.cvng.activity.jobs.ActivityStatusJob;
import io.harness.cvng.activity.jobs.HarnessCDCurrentGenEventsHandler;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.cdng.jobs.CVNGStepTaskHandler;
import io.harness.cvng.cdng.services.impl.CVNGFilterCreationResponseMerger;
import io.harness.cvng.cdng.services.impl.CVNGModuleInfoProvider;
import io.harness.cvng.cdng.services.impl.CVNGNotifyEventListener;
import io.harness.cvng.cdng.services.impl.CVNGPipelineServiceInfoProvider;
import io.harness.cvng.client.NextGenClientModule;
import io.harness.cvng.client.VerificationManagerClientModule;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.entities.DeletedCVConfig;
import io.harness.cvng.core.entities.DeletedCVConfig.DeletedCVConfigKeys;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask.MonitoringSourcePerpetualTaskKeys;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.ChangeSource.ChangeSourceKeys;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.demo.CVNGDemoPerpetualTask;
import io.harness.cvng.core.entities.demo.CVNGDemoPerpetualTask.CVNGDemoPerpetualTaskKeys;
import io.harness.cvng.core.jobs.CVConfigCleanupHandler;
import io.harness.cvng.core.jobs.CVNGDemoPerpetualTaskHandler;
import io.harness.cvng.core.jobs.ChangeSourceDemoHandler;
import io.harness.cvng.core.jobs.DataCollectionTasksPerpetualTaskStatusUpdateHandler;
import io.harness.cvng.core.jobs.DeploymentChangeEventConsumer;
import io.harness.cvng.core.jobs.EntityCRUDStreamConsumer;
import io.harness.cvng.core.jobs.MonitoringSourcePerpetualTaskHandler;
import io.harness.cvng.core.jobs.PersistentLockCleanup;
import io.harness.cvng.core.jobs.SLIDataCollectionTaskCreateNextTaskHandler;
import io.harness.cvng.core.jobs.ServiceGuardDataCollectionTaskCreateNextTaskHandler;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.exception.BadRequestExceptionMapper;
import io.harness.cvng.exception.ConstraintViolationExceptionMapper;
import io.harness.cvng.exception.NotFoundExceptionMapper;
import io.harness.cvng.governance.beans.ExpansionKeysConstants;
import io.harness.cvng.governance.services.SLOPolicyExpansionHandler;
import io.harness.cvng.metrics.services.impl.CVNGMetricsPublisher;
import io.harness.cvng.migration.CVNGSchemaHandler;
import io.harness.cvng.migration.beans.CVNGSchema;
import io.harness.cvng.migration.beans.CVNGSchema.CVNGSchemaKeys;
import io.harness.cvng.migration.service.CVNGMigrationService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.cvng.statemachine.jobs.AnalysisOrchestrationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;
import io.harness.cvng.verificationjob.jobs.ProcessQueuedVerificationJobInstanceHandler;
import io.harness.cvng.verificationjob.jobs.VerificationJobInstanceTimeoutHandler;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.ff.FeatureFlagConfig;
import io.harness.govern.ProviderModule;
import io.harness.health.HealthService;
import io.harness.iterator.PersistenceIterator;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.morphia.MorphiaModule;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.notification.module.NotificationClientModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.pms.contracts.plan.ExpansionRequestType;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PipelineEventConsumerController;
import io.harness.pms.sdk.PmsSdkConfiguration;
import io.harness.pms.sdk.PmsSdkInitHelper;
import io.harness.pms.sdk.PmsSdkModule;
import io.harness.pms.sdk.core.SdkDeployMode;
import io.harness.pms.sdk.core.governance.JsonExpansionHandlerInfo;
import io.harness.pms.sdk.execution.events.facilitators.FacilitatorEventRedisConsumer;
import io.harness.pms.sdk.execution.events.interrupts.InterruptEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.advise.NodeAdviseEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.resume.NodeResumeEventRedisConsumer;
import io.harness.pms.sdk.execution.events.node.start.NodeStartEventRedisConsumer;
import io.harness.pms.sdk.execution.events.orchestrationevent.OrchestrationEventRedisConsumer;
import io.harness.pms.sdk.execution.events.plan.CreatePartialPlanRedisConsumer;
import io.harness.pms.sdk.execution.events.progress.ProgressEventRedisConsumer;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.queue.QueueListenerController;
import io.harness.queue.QueuePublisher;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resource.VersionInfoResource;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.serializer.CVNGStepRegistrar;
import io.harness.serializer.CvNextGenRegistrars;
import io.harness.serializer.JsonSubtypeResolver;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PipelineServiceUtilAdviserRegistrar;
import io.harness.serializer.PrimaryVersionManagerRegistrars;
import io.harness.token.TokenClientModule;
import io.harness.token.remote.TokenClient;
import io.harness.waiter.NotifyEvent;
import io.harness.waiter.NotifyQueuePublisherRegister;
import io.harness.waiter.ProgressUpdateService;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.glassfish.jersey.server.model.Resource;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.mongodb.morphia.converters.TypeConverter;
import org.reflections.Reflections;
import org.springframework.core.convert.converter.Converter;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class VerificationApplication extends Application<VerificationConfiguration> {
  private static String APPLICATION_NAME = "Verification NextGen Application";
  private static final SecureRandom random = new SecureRandom();

  private final MetricRegistry metricRegistry = new MetricRegistry();
  private HarnessMetricRegistry harnessMetricRegistry;
  private HPersistence hPersistence;

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new VerificationApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<VerificationConfiguration> bootstrap) {
    initializeLogging();
    log.info("bootstrapping ...");
    // Enable variable substitution with environment variables
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    bootstrap.addBundle(new SwaggerBundle<VerificationConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
          VerificationConfiguration verificationServiceConfiguration) {
        return verificationServiceConfiguration.getSwaggerBundleConfiguration();
      }
    });
    bootstrap.setMetricRegistry(metricRegistry);
    configureObjectMapper(bootstrap.getObjectMapper());
    log.info("bootstrapping done.");
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    mapper.setSubtypeResolver(new JsonSubtypeResolver(mapper.getSubtypeResolver()));
  }
  private void createConsumerThreadsToListenToEvents(Injector injector) {
    new Thread(injector.getInstance(EntityCRUDStreamConsumer.class)).start();
    new Thread(injector.getInstance(DeploymentChangeEventConsumer.class)).start();
  }

  private void scheduleMaintenanceActivities(Injector injector, VerificationConfiguration configuration) {
    ScheduledThreadPoolExecutor maintenanceActivitiesExecutor =
        new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("maintenance-activities").build());
    if (configuration.getDistributedLockImplementation() == DistributedLockImplementation.MONGO) {
      maintenanceActivitiesExecutor.scheduleWithFixedDelay(
          injector.getInstance(PersistentLockCleanup.class), random.nextInt(15), 15L, TimeUnit.MINUTES);
    }
  }

  @Override
  public void run(VerificationConfiguration configuration, Environment environment) {
    log.info("Starting app ...");
    MaintenanceController.forceMaintenance(true);
    ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                                            .configure()
                                            .parameterNameProvider(new ReflectionParameterNameProvider())
                                            .buildValidatorFactory();

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(CvNextGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CvNextGenRegistrars.morphiaRegistrars)
            .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(CvNextGenRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }
    });

    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return configuration.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return configuration.getFeatureFlagConfig();
      }
    });
    modules.add(MetricsInstrumentationModule.builder()
                    .withMetricRegistry(metricRegistry)
                    .withMatcher(not(new AbstractMatcher<TypeLiteral<?>>() {
                      @Override
                      public boolean matches(TypeLiteral<?> typeLiteral) {
                        return typeLiteral.getRawType().isAnnotationPresent(Path.class);
                      }
                    }))
                    .build());
    modules.add(new ValidationModule(validatorFactory));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return configuration.getMongoConnectionFactory();
      }
    });
    modules.add(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "cvng_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "cvng_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "cvng_delegateTaskProgressResponses")
            .build();
      }
    });

    modules.add(MorphiaModule.getInstance());
    modules.add(new CVServiceModule(configuration));
    modules.add(new PersistentLockModule());
    modules.add(new EventsFrameworkModule(configuration.getEventsFrameworkConfiguration()));
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(new VerificationManagerClientModule(configuration.getManagerClientConfig().getBaseUrl()));
    modules.add(new NextGenClientModule(configuration.getNgManagerServiceConfig()));
    modules.add(new SecretNGManagerClientModule(
        ServiceHttpClientConfig.builder().baseUrl(configuration.getNgManagerServiceConfig().getNgManagerUrl()).build(),
        configuration.getNgManagerServiceConfig().getManagerServiceSecret(), "NextGenManager"));
    modules.add(new CVNextGenCommonsServiceModule());
    modules.add(new NotificationClientModule(configuration.getNotificationClientConfiguration()));
    modules.add(new CvPersistenceModule());
    modules.add(new CacheModule(configuration.getCacheConfig()));
    modules.add(new TokenClientModule(
        ServiceHttpClientConfig.builder().baseUrl(configuration.getNgManagerServiceConfig().getNgManagerUrl()).build(),
        configuration.getNgManagerServiceConfig().getManagerServiceSecret(), "NextGenManager"));
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(false)
                                                    .requireValidatorInit(false)
                                                    .build();

    // Pipeline Service Modules
    PmsSdkConfiguration pmsSdkConfiguration = getPmsSdkConfiguration(configuration);
    modules.add(PmsSdkModule.getInstance(pmsSdkConfiguration));
    modules.add(PipelineServiceUtilityModule.getInstance());

    Injector injector = Guice.createInjector(modules);
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
    initializeServiceSecretKeys();
    harnessMetricRegistry = injector.getInstance(HarnessMetricRegistry.class);
    initMetrics(injector);
    autoCreateCollectionsAndIndexes(injector);
    registerCorrelationFilter(environment, injector);
    registerAuthFilters(environment, injector, configuration);
    registerManagedBeans(environment, injector);
    registerResources(environment, injector);
    registerVerificationTaskOrchestrationIterator(injector);
    registerVerificationJobInstanceDataCollectionTaskIterator(injector);
    registerDataCollectionTaskIterator(injector);
    registerCreateNextSLIDataCollectionTaskIterator(injector);
    registerCreateNextDataCollectionTaskIterator(injector);
    registerCVNGDemoPerpetualTaskIterator(injector);
    registerDataCollectionTasksPerpetualTaskStatusUpdateIterator(injector);
    injector.getInstance(CVNGStepTaskHandler.class).registerIterator();
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
    registerExceptionMappers(environment.jersey());
    registerCVConfigCleanupIterator(injector);
    registerHealthChecks(environment, injector);
    createConsumerThreadsToListenToEvents(injector);
    registerCVNGSchemaMigrationIterator(injector);
    registerActivityIterator(injector);
    registerVerificationJobInstanceTimeoutIterator(injector);
    registerPipelineSDK(configuration, injector);
    registerWaitEnginePublishers(injector);
    registerPmsSdkEvents(injector);
    registerDemoGenerationIterator(injector);
    scheduleSidekickProcessing(injector);
    scheduleMaintenanceActivities(injector, configuration);

    log.info("Leaving startup maintenance mode");
    MaintenanceController.forceMaintenance(false);
    registerUpdateProgressScheduler(injector);
    runMigrations(injector);

    log.info("Starting app done");
  }

  private void scheduleSidekickProcessing(Injector injector) {
    ScheduledThreadPoolExecutor workflowVerificationExecutor =
        new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("side-kick").build());
    workflowVerificationExecutor.scheduleWithFixedDelay(
        () -> injector.getInstance(SideKickService.class).processNext(), 5, 5, TimeUnit.SECONDS);
  }

  private void registerUpdateProgressScheduler(Injector injector) {
    // This is need for wait notify update progress for CVNG step.
    ScheduledThreadPoolExecutor waitNotifyUpdateProgressExecutor =
        new ScheduledThreadPoolExecutor(2, new ThreadFactoryBuilder().setNameFormat("wait-notify-update").build());
    waitNotifyUpdateProgressExecutor.scheduleWithFixedDelay(
        injector.getInstance(ProgressUpdateService.class), 0L, 5L, TimeUnit.SECONDS);
  }

  private void registerQueueListeners(Injector injector) {
    log.info("Initializing queue listeners...");
    QueueListenerController queueListenerController = injector.getInstance(QueueListenerController.class);
    queueListenerController.register(injector.getInstance(CVNGNotifyEventListener.class), 1);
  }

  private void registerWaitEnginePublishers(Injector injector) {
    final QueuePublisher<NotifyEvent> publisher =
        injector.getInstance(Key.get(new TypeLiteral<QueuePublisher<NotifyEvent>>() {}));
    final NotifyQueuePublisherRegister notifyQueuePublisherRegister =
        injector.getInstance(NotifyQueuePublisherRegister.class);
    notifyQueuePublisherRegister.register(
        CVNG_ORCHESTRATION, payload -> publisher.send(Arrays.asList(CVNG_ORCHESTRATION), payload));
  }

  public void registerPipelineSDK(VerificationConfiguration configuration, Injector injector) {
    PmsSdkConfiguration sdkConfig = getPmsSdkConfiguration(configuration);
    if (sdkConfig.getDeploymentMode().equals(SdkDeployMode.REMOTE)) {
      try {
        PmsSdkInitHelper.initializeSDKInstance(injector, sdkConfig);
        if (configuration.getShouldConfigureWithPMS()) {
          registerQueueListeners(injector);
        }
      } catch (Exception e) {
        log.error("Failed To register pipeline sdk", e);
        // Don't fail for now. We have to find out retry strategy
        System.exit(1);
      }
    }
  }

  private PmsSdkConfiguration getPmsSdkConfiguration(VerificationConfiguration config) {
    boolean remote = false;
    if (config.getShouldConfigureWithPMS() != null && config.getShouldConfigureWithPMS()) {
      remote = true;
    }

    return PmsSdkConfiguration.builder()
        .deploymentMode(remote ? SdkDeployMode.REMOTE : SdkDeployMode.LOCAL)
        .moduleType(ModuleType.CV)
        .pipelineServiceInfoProviderClass(CVNGPipelineServiceInfoProvider.class)
        .grpcServerConfig(config.getPmsSdkGrpcServerConfig())
        .pmsGrpcClientConfig(config.getPmsGrpcClientConfig())
        .engineSteps(CVNGStepRegistrar.getEngineSteps())
        .engineAdvisers(PipelineServiceUtilAdviserRegistrar.getEngineAdvisers())
        .engineFacilitators(new HashMap<>())
        .filterCreationResponseMerger(new CVNGFilterCreationResponseMerger())
        .executionSummaryModuleInfoProviderClass(CVNGModuleInfoProvider.class)
        .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
        .jsonExpansionHandlers(getJsonExpansionHandlers())
        .build();
  }

  private List<JsonExpansionHandlerInfo> getJsonExpansionHandlers() {
    List<JsonExpansionHandlerInfo> jsonExpansionHandlers = new ArrayList<>();
    JsonExpansionInfo sloPolicyInfo =
        JsonExpansionInfo.newBuilder()
            .setExpansionType(ExpansionRequestType.LOCAL_FQN)
            .setKey(YAMLFieldNameConstants.STAGE + YamlNode.PATH_SEP + YAMLFieldNameConstants.SPEC)
            .setExpansionKey(ExpansionKeysConstants.SLO_POLICY_EXPANSION_KEY)
            .setStageType(StepType.newBuilder().setType("Deployment").setStepCategory(StepCategory.STAGE).build())
            .build();
    JsonExpansionHandlerInfo sloPolicyHandler = JsonExpansionHandlerInfo.builder()
                                                    .jsonExpansionInfo(sloPolicyInfo)
                                                    .expansionHandler(SLOPolicyExpansionHandler.class)
                                                    .build();
    jsonExpansionHandlers.add(sloPolicyHandler);
    return jsonExpansionHandlers;
  }

  private void initMetrics(Injector injector) {
    injector.getInstance(MetricService.class)
        .initializeMetrics(Arrays.asList(injector.getInstance(CVNGMetricsPublisher.class)));
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private void autoCreateCollectionsAndIndexes(Injector injector) {
    hPersistence = injector.getInstance(HPersistence.class);
  }

  private void registerActivityIterator(Injector injector) {
    ScheduledThreadPoolExecutor workflowVerificationExecutor =
        new ScheduledThreadPoolExecutor(5, new ThreadFactoryBuilder().setNameFormat("Iterator-Activity").build());
    Handler<Activity> handler = injector.getInstance(ActivityStatusJob.class);
    PersistenceIterator activityIterator =
        MongoPersistenceIterator.<Activity, MorphiaFilterExpander<Activity>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(Activity.class)
            .fieldName(ActivityKeys.verificationIteration)
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofSeconds(15))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(7))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.field(ActivityKeys.verificationJobInstanceIds)
                       .exists()
                       .field(ActivityKeys.analysisStatus)
                       .in(Lists.newArrayList(
                           ActivityVerificationStatus.NOT_STARTED, ActivityVerificationStatus.IN_PROGRESS)))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(activityIterator);
    workflowVerificationExecutor.scheduleWithFixedDelay(() -> activityIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerVerificationTaskOrchestrationIterator(Injector injector) {
    // TODO: Reevaluate the thread count here. 20 might be enough now but as we scale, we need to reconsider.
    int poolSize = 20;
    ScheduledThreadPoolExecutor workflowVerificationExecutor = new ScheduledThreadPoolExecutor(
        poolSize, new ThreadFactoryBuilder().setNameFormat("Iterator-Analysis").build());
    Handler<AnalysisOrchestrator> handler = injector.getInstance(AnalysisOrchestrationJob.class);

    PersistenceIterator analysisOrchestrationIterator =
        MongoPersistenceIterator.<AnalysisOrchestrator, MorphiaFilterExpander<AnalysisOrchestrator>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(AnalysisOrchestrator.class)
            .fieldName(AnalysisOrchestratorKeys.analysisOrchestrationIteration)
            .targetInterval(ofSeconds(15))
            .acceptableNoAlertDelay(ofSeconds(30))
            .executorService(workflowVerificationExecutor)
            .semaphore(new Semaphore(poolSize - 1))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.field(AnalysisOrchestratorKeys.status)
                       .in(Lists.newArrayList(AnalysisStatus.CREATED, AnalysisStatus.RUNNING)))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(analysisOrchestrationIterator);
    workflowVerificationExecutor.scheduleWithFixedDelay(
        () -> analysisOrchestrationIterator.process(), 0, 5, TimeUnit.SECONDS);
  }

  private void registerVerificationJobInstanceTimeoutIterator(Injector injector) {
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("verificationJobInstance-timeout-iterator").build());
    Handler<VerificationJobInstance> handler = injector.getInstance(VerificationJobInstanceTimeoutHandler.class);
    PersistenceIterator persistenceIterator =
        MongoPersistenceIterator.<VerificationJobInstance, MorphiaFilterExpander<VerificationJobInstance>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(VerificationJobInstance.class)
            .fieldName(VerificationJobInstanceKeys.timeoutTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(scheduledThreadPoolExecutor)
            .semaphore(new Semaphore(1))
            .handler(handler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.field(VerificationJobInstanceKeys.executionStatus).in(ExecutionStatus.nonFinalStatuses()))
            .redistribute(true)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .build();
    injector.injectMembers(persistenceIterator);
    scheduledThreadPoolExecutor.scheduleWithFixedDelay(() -> persistenceIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerDemoGenerationIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("demo-generator-change-source").build());
    ChangeSourceDemoHandler changeSourceDemoHandler = injector.getInstance(ChangeSourceDemoHandler.class);
    PersistenceIterator demoDataIterator =
        MongoPersistenceIterator.<ChangeSource, MorphiaFilterExpander<ChangeSource>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(ChangeSource.class)
            .fieldName(ChangeSourceKeys.demoDataGenerationIteration)
            .targetInterval(ofMinutes(80))
            .acceptableNoAlertDelay(ofMinutes(10))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(2))
            .handler(changeSourceDemoHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.filter(ChangeSourceKeys.isConfiguredForDemo, true))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(demoDataIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(() -> demoDataIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("cv-config-data-collection-iterator").build());

    MonitoringSourcePerpetualTaskHandler monitoringSourcePerpetualTaskHandler =
        injector.getInstance(MonitoringSourcePerpetualTaskHandler.class);
    PersistenceIterator monitoringSourceIterator =
        MongoPersistenceIterator
            .<MonitoringSourcePerpetualTask, MorphiaFilterExpander<MonitoringSourcePerpetualTask>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(MonitoringSourcePerpetualTask.class)
            .fieldName(MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(5))
            .handler(monitoringSourcePerpetualTaskHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.criteria(MonitoringSourcePerpetualTaskKeys.perpetualTaskId).doesNotExist())
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(monitoringSourceIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(() -> monitoringSourceIterator.process(), 0, 30, TimeUnit.SECONDS);

    HarnessCDCurrentGenEventsHandler harnessCDCurrentGenEventsHandler =
        injector.getInstance(HarnessCDCurrentGenEventsHandler.class);
    PersistenceIterator harnessCDIterator =
        MongoPersistenceIterator
            .<HarnessCDCurrentGenChangeSource, MorphiaFilterExpander<HarnessCDCurrentGenChangeSource>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(HarnessCDCurrentGenChangeSource.class)
            .fieldName(ChangeSourceKeys.dataCollectionTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(5))
            .handler(harnessCDCurrentGenEventsHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.filter(ChangeSourceKeys.type, ChangeSourceType.HARNESS_CD_CURRENT_GEN))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(harnessCDIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(() -> harnessCDIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerCreateNextDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("create-next-task-iterator").build());
    ServiceGuardDataCollectionTaskCreateNextTaskHandler serviceGuardDataCollectionTaskCreateNextTaskHandler =
        injector.getInstance(ServiceGuardDataCollectionTaskCreateNextTaskHandler.class);
    PersistenceIterator liveMonitoringDataCollectionTaskRecoverHandlerIterator =
        MongoPersistenceIterator.<CVConfig, MorphiaFilterExpander<CVConfig>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(CVConfig.class)
            .fieldName(CVConfigKeys.createNextTaskIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(3))
            .handler(serviceGuardDataCollectionTaskCreateNextTaskHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.filter(CVConfigKeys.enabled, true))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(liveMonitoringDataCollectionTaskRecoverHandlerIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(
        () -> liveMonitoringDataCollectionTaskRecoverHandlerIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerCreateNextSLIDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("create-next-sli-task-iterator").build());
    SLIDataCollectionTaskCreateNextTaskHandler sliDataCollectionTaskCreateNextTaskHandler =
        injector.getInstance(SLIDataCollectionTaskCreateNextTaskHandler.class);
    PersistenceIterator sliDataCollectionTaskRecoverHandlerIterator =
        MongoPersistenceIterator.<ServiceLevelIndicator, MorphiaFilterExpander<ServiceLevelIndicator>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(ServiceLevelIndicator.class)
            .fieldName(ServiceLevelIndicatorKeys.createNextTaskIteration)
            .targetInterval(ofMinutes(5))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(3))
            .handler(sliDataCollectionTaskCreateNextTaskHandler)
            .schedulingType(REGULAR)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(sliDataCollectionTaskRecoverHandlerIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(
        () -> sliDataCollectionTaskRecoverHandlerIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerCVNGDemoPerpetualTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor cvngDemoPerpetualTaskExecutor = new ScheduledThreadPoolExecutor(
        3, new ThreadFactoryBuilder().setNameFormat("create-cvng-perpetual-task-iterator").build());

    CVNGDemoPerpetualTaskHandler cvngDemoPerpetualTaskHandler =
        injector.getInstance(CVNGDemoPerpetualTaskHandler.class);

    PersistenceIterator cvngDemoPerpetualTaskIterator =
        MongoPersistenceIterator.<CVNGDemoPerpetualTask, MorphiaFilterExpander<CVNGDemoPerpetualTask>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(CVNGDemoPerpetualTask.class)
            .fieldName(CVNGDemoPerpetualTaskKeys.createNextTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(cvngDemoPerpetualTaskExecutor)
            .semaphore(new Semaphore(3))
            .handler(cvngDemoPerpetualTaskHandler)
            .schedulingType(REGULAR)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();

    injector.injectMembers(cvngDemoPerpetualTaskIterator);
    cvngDemoPerpetualTaskExecutor.scheduleWithFixedDelay(
        () -> cvngDemoPerpetualTaskIterator.process(), 0, 1, TimeUnit.MINUTES);
  }

  private void registerDataCollectionTasksPerpetualTaskStatusUpdateIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionTasksPerpetualTaskStatusUpdateExecutor =
        new ScheduledThreadPoolExecutor(3,
            new ThreadFactoryBuilder()
                .setNameFormat("create-data-collection-tasks-perpetual-task-status-update-iterator")
                .build());

    DataCollectionTasksPerpetualTaskStatusUpdateHandler dataCollectionTasksPerpetualTaskStatusUpdateHandler =
        injector.getInstance(DataCollectionTasksPerpetualTaskStatusUpdateHandler.class);

    PersistenceIterator dataCollectionTasksPerpetualTaskStatusUpdateIterator =
        MongoPersistenceIterator.<DataCollectionTask, MorphiaFilterExpander<DataCollectionTask>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(DataCollectionTask.class)
            .fieldName(DataCollectionTaskKeys.workerStatusIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionTasksPerpetualTaskStatusUpdateExecutor)
            .semaphore(new Semaphore(3))
            .handler(dataCollectionTasksPerpetualTaskStatusUpdateHandler)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query.and(
                    query.or(query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.QUEUED),
                        query.and(
                            query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.RUNNING),
                            query.criteria(DataCollectionTaskKeys.lastUpdatedAt)
                                .lessThan(injector.getInstance(Clock.class)
                                              .instant()
                                              .minus(5, ChronoUnit.MINUTES)
                                              .toEpochMilli()))),
                    query.criteria(DataCollectionTaskKeys.validAfter)
                        .lessThan(injector.getInstance(Clock.class).instant().minus(3, ChronoUnit.MINUTES))))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();

    injector.injectMembers(dataCollectionTasksPerpetualTaskStatusUpdateIterator);
    dataCollectionTasksPerpetualTaskStatusUpdateExecutor.scheduleWithFixedDelay(
        () -> dataCollectionTasksPerpetualTaskStatusUpdateIterator.process(), 0, 3, TimeUnit.MINUTES);
  }

  private void registerVerificationJobInstanceDataCollectionTaskIterator(Injector injector) {
    ScheduledThreadPoolExecutor verificationTaskExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("verification-job-instance-data-collection-iterator").build());
    ProcessQueuedVerificationJobInstanceHandler handler =
        injector.getInstance(ProcessQueuedVerificationJobInstanceHandler.class);
    // TODO: setup alert if this goes above acceptable threshold.
    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<VerificationJobInstance, MorphiaFilterExpander<VerificationJobInstance>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(VerificationJobInstance.class)
            .fieldName(VerificationJobInstanceKeys.dataCollectionTaskIteration)
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(verificationTaskExecutor)
            .semaphore(new Semaphore(5))
            .handler(handler)
            .schedulingType(REGULAR)
            // TODO: find a way to implement retry logic.
            .filterExpander(query -> query.filter(VerificationJobInstanceKeys.executionStatus, ExecutionStatus.QUEUED))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    verificationTaskExecutor.scheduleWithFixedDelay(() -> dataCollectionIterator.process(), 0, 30, TimeUnit.SECONDS);
  }
  private void registerCVConfigCleanupIterator(Injector injector) {
    ScheduledThreadPoolExecutor dataCollectionExecutor = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("cv-config-cleanup-iterator").build());
    CVConfigCleanupHandler cvConfigCleanupHandler = injector.getInstance(CVConfigCleanupHandler.class);
    // TODO: setup alert if this goes above acceptable threshold.
    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<DeletedCVConfig, MorphiaFilterExpander<DeletedCVConfig>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(DeletedCVConfig.class)
            .fieldName(DeletedCVConfigKeys.dataCollectionTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .executorService(dataCollectionExecutor)
            .semaphore(new Semaphore(5))
            .handler(cvConfigCleanupHandler)
            .schedulingType(REGULAR)
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    dataCollectionExecutor.scheduleWithFixedDelay(() -> dataCollectionIterator.process(), 0, 30, TimeUnit.SECONDS);
  }

  private void registerHealthChecks(Environment environment, Injector injector) {
    HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("CV nextgen", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerAuthFilters(
      Environment environment, Injector injector, VerificationConfiguration configuration) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
        -> resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null
        || resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class) != null;
    serviceToSecretMapping.put(BEARER.getServiceId(), configuration.getManagerAuthConfig().getJwtAuthSecret());
    serviceToSecretMapping.put(
        IDENTITY_SERVICE.getServiceId(), configuration.getManagerAuthConfig().getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(
        DEFAULT.getServiceId(), configuration.getNgManagerServiceConfig().getManagerServiceSecret());
    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
    environment.jersey().register(injector.getInstance(CVNGAuthenticationFilter.class));
  }

  private void registerCVNGSchemaMigrationIterator(Injector injector) {
    ScheduledThreadPoolExecutor migrationExecutor = new ScheduledThreadPoolExecutor(
        2, new ThreadFactoryBuilder().setNameFormat("cvng-schema-migration-iterator").build());
    CVNGSchemaHandler cvngSchemaMigrationHandler = injector.getInstance(CVNGSchemaHandler.class);

    PersistenceIterator dataCollectionIterator =
        MongoPersistenceIterator.<CVNGSchema, MorphiaFilterExpander<CVNGSchema>>builder()
            .mode(PersistenceIterator.ProcessMode.PUMP)
            .clazz(CVNGSchema.class)
            .fieldName(CVNGSchemaKeys.cvngNextIteration)
            .targetInterval(ofMinutes(30))
            .acceptableNoAlertDelay(ofMinutes(5))
            .executorService(migrationExecutor)
            .semaphore(new Semaphore(3))
            .handler(cvngSchemaMigrationHandler)
            .schedulingType(REGULAR)
            .filterExpander(query -> query.criteria(CVNGSchemaKeys.cvngMigrationStatus).notEqual(RUNNING))
            .persistenceProvider(injector.getInstance(MorphiaPersistenceProvider.class))
            .redistribute(true)
            .build();
    injector.injectMembers(dataCollectionIterator);
    migrationExecutor.scheduleWithFixedDelay(() -> dataCollectionIterator.process(), 0, 15, TimeUnit.MINUTES);
  }

  private void initializeServiceSecretKeys() {
    // TODO: using env variable directly for now. The whole secret management needs to move to env variable and
    // cv-nextgen should have a new secret with manager along with other services. Change this once everything is
    // standardized for service communication.
    VERIFICATION_SERVICE_SECRET.set(System.getenv(CVNextGenConstants.VERIFICATION_SERVICE_SECRET));
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(MaintenanceController.class));

    // Pipeline SDK Consumers
    environment.lifecycle().manage(injector.getInstance(PipelineEventConsumerController.class));
  }

  private void registerPmsSdkEvents(Injector injector) {
    log.info("Initializing sdk event redis abstract consumers...");
    PipelineEventConsumerController pipelineEventConsumerController =
        injector.getInstance(PipelineEventConsumerController.class);
    pipelineEventConsumerController.register(injector.getInstance(InterruptEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(OrchestrationEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(FacilitatorEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeStartEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(ProgressEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeAdviseEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(NodeResumeEventRedisConsumer.class), 1);
    pipelineEventConsumerController.register(injector.getInstance(CreatePartialPlanRedisConsumer.class), 1);
  }

  private void registerResources(Environment environment, Injector injector) {
    long startTimeMs = System.currentTimeMillis();
    Reflections reflections = new Reflections(this.getClass().getPackage().getName());
    reflections.getTypesAnnotatedWith(Path.class).forEach(resource -> {
      if (!resource.getPackage().getName().endsWith("resources")) {
        throw new IllegalStateException("Resource classes should be in resources package." + resource);
      }
      if (Resource.isAcceptable(resource)) {
        long startTime = System.currentTimeMillis();
        Object resourceClass = injector.getInstance(resource);
        log.info("Time to get instance: " + (System.currentTimeMillis() - startTime) + " ms"
            + resourceClass.getClass().getSimpleName());
        startTime = System.currentTimeMillis();
        environment.jersey().register(resourceClass);
        log.info("Time to register resource: " + (System.currentTimeMillis() - startTime) + " ms"
            + resourceClass.getClass().getSimpleName());
      }
    });
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
    log.info("Registered all the resources. Time taken(ms): {}", System.currentTimeMillis() - startTimeMs);
  }

  private void registerExceptionMappers(JerseyEnvironment jersey) {
    jersey.register(ConstraintViolationExceptionMapper.class);
    jersey.register(NotFoundExceptionMapper.class);
    jersey.register(BadRequestExceptionMapper.class);
    jersey.register(WingsExceptionMapperV2.class);
    jersey.register(GenericExceptionMapperV2.class);
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void runMigrations(Injector injector) {
    injector.getInstance(CVNGMigrationService.class).runMigrations();
  }
}
