/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.AuthorizationServiceHeader.BEARER;
import static io.harness.AuthorizationServiceHeader.CI_MANAGER;
import static io.harness.AuthorizationServiceHeader.CV_NEXT_GEN;
import static io.harness.AuthorizationServiceHeader.DEFAULT;
import static io.harness.AuthorizationServiceHeader.DELEGATE_SERVICE;
import static io.harness.AuthorizationServiceHeader.IDENTITY_SERVICE;
import static io.harness.AuthorizationServiceHeader.MANAGER;
import static io.harness.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.AuthorizationServiceHeader.NOTIFICATION_SERVICE;
import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.accesscontrol.AccessControlConfiguration.getResourceClasses;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.stream.Collectors.toSet;

import io.harness.Microservice;
import io.harness.accesscontrol.commons.bootstrap.AccessControlManagementJob;
import io.harness.accesscontrol.commons.events.EntityCrudEventListenerService;
import io.harness.accesscontrol.commons.events.UserMembershipEventListenerService;
import io.harness.accesscontrol.commons.migration.AccessControlMigrationProvider;
import io.harness.accesscontrol.principals.serviceaccounts.iterators.ServiceAccountReconciliationIterator;
import io.harness.accesscontrol.principals.usergroups.iterators.UserGroupReconciliationIterator;
import io.harness.accesscontrol.principals.users.iterators.UserReconciliationIterator;
import io.harness.accesscontrol.resources.resourcegroups.iterators.ResourceGroupReconciliationIterator;
import io.harness.accesscontrol.scopes.harness.iterators.ScopeReconciliationIterator;
import io.harness.accesscontrol.support.reconciliation.SupportPreferenceReconciliationIterator;
import io.harness.accesscontrol.support.reconciliation.SupportRoleAssignmentsReconciliationService;
import io.harness.aggregator.AggregatorService;
import io.harness.aggregator.MongoOffsetCleanupJob;
import io.harness.annotations.dev.OwnedBy;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.enforcement.client.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.ConstraintViolationExceptionMapper;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.outbox.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.remote.CharsetResponseFilter;
import io.harness.request.RequestContextFilter;
import io.harness.resource.VersionInfoResource;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;
import io.harness.token.remote.TokenClient;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

@OwnedBy(PL)
@Slf4j
public class AccessControlApplication extends Application<AccessControlConfiguration> {
  private static final String APPLICATION_NAME = "Access Control Service";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new AccessControlApplication().run(args);
  }

  private final MetricRegistry metricRegistry = new MetricRegistry();

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<AccessControlConfiguration> bootstrap) {
    initializeLogging();
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addBundle(new SwaggerBundle<AccessControlConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(AccessControlConfiguration appConfig) {
        return getSwaggerConfiguration(appConfig);
      }
    });
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
  }

  @Override
  public void run(AccessControlConfiguration appConfig, Environment environment) {
    log.info("Starting Access Control Application ...");
    MaintenanceController.forceMaintenance(true);
    Injector injector =
        Guice.createInjector(AccessControlModule.getInstance(appConfig), new MetricRegistryModule(metricRegistry));
    injector.getInstance(HPersistence.class);
    registerCorsFilter(appConfig, environment);
    registerResources(environment, injector);
    registerJerseyProviders(environment);
    registerJerseyFeatures(environment);
    registerCharsetResponseFilter(environment, injector);
    registerCorrelationFilter(environment, injector);
    registerRequestContextFilter(environment);
    registerAuthFilters(appConfig, environment, injector);
    registerHealthCheck(environment, injector);
    registerManagedBeans(appConfig, environment, injector);
    registerMigrations(injector);
    registerIterators(injector);
    registerScheduledJobs(injector);
    registerOasResource(appConfig, environment, injector);
    initializeEnforcementFramework(injector);
    AccessControlManagementJob accessControlManagementJob = injector.getInstance(AccessControlManagementJob.class);
    accessControlManagementJob.run();

    if (appConfig.getAggregatorConfiguration().isEnabled()) {
      environment.lifecycle().manage(injector.getInstance(AggregatorService.class));
      environment.lifecycle().manage(injector.getInstance(MongoOffsetCleanupJob.class));
    }

    if (appConfig.getAggregatorConfiguration().isExportMetricsToStackDriver()) {
      initializeMonitoring(injector);
    }

    MaintenanceController.forceMaintenance(false);
  }

  private void initializeMonitoring(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Access Control Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  public void registerIterators(Injector injector) {
    injector.getInstance(ResourceGroupReconciliationIterator.class).registerIterators();
    injector.getInstance(UserGroupReconciliationIterator.class).registerIterators();
    injector.getInstance(UserReconciliationIterator.class).registerIterators();
    injector.getInstance(ServiceAccountReconciliationIterator.class).registerIterators();
    injector.getInstance(SupportPreferenceReconciliationIterator.class).registerIterators();
    injector.getInstance(ScopeReconciliationIterator.class).registerIterators();
  }

  public void registerScheduledJobs(Injector injector) {
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
  }

  private void registerJerseyFeatures(Environment environment) {
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerOasResource(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(getOasConfig(configuration));
    environment.jersey().register(openApiResource);
  }

  private OpenAPIConfiguration getOasConfig(AccessControlConfiguration appConfig) {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("Access Control API Reference")
            .description(
                "This is the Open Api Spec 3 for the Access Control Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("1.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", appConfig.getHostname(), appConfig.getBasePathPrefix());
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", appConfig.getHostname(), appConfig.getBasePathPrefix());
    }
    Collection<Class<?>> classes = getResourceClasses();
    classes.add(AccessControlSwaggerListener.class);
    Set<String> packages = getUniquePackages(classes);
    return new SwaggerConfiguration().openAPI(oas).prettyPrint(true).resourcePackages(packages).scannerClass(
        "io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  private void registerCorsFilter(AccessControlConfiguration appConfig, Environment environment) {
    Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : getResourceClasses()) {
      environment.jersey().register(injector.getInstance(resource));
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerManagedBeans(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.getEventsConfig().isEnabled()) {
      environment.lifecycle().manage(injector.getInstance(EntityCrudEventListenerService.class));
      environment.lifecycle().manage(injector.getInstance(UserMembershipEventListenerService.class));
    }
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(SupportRoleAssignmentsReconciliationService.class));
  }

  private void registerJerseyProviders(Environment environment) {
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(ConstraintViolationExceptionMapper.class);
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(NGAccessDeniedExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerAuthFilters(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isAuthEnabled()) {
      registerAccessControlAuthFilter(configuration, environment, injector);
      registerInternalApiAuthFilter(configuration, environment);
    }
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthenticationExemptedRequestsPredicate() {
    return getAuthFilterPredicate(PublicApi.class)
        .or(resourceInfoAndRequest
            -> resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/version")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger.yaml")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith(
                    "/openapi.json"));
  }

  private void registerAccessControlAuthFilter(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        (getAuthenticationExemptedRequestsPredicate().negate())
            .and((getAuthFilterPredicate(InternalApi.class)).negate());
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(BEARER.getServiceId(), configuration.getJwtAuthSecret());
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(MANAGER.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(NG_MANAGER.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(CI_MANAGER.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(CV_NEXT_GEN.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(DELEGATE_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(NOTIFICATION_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(PIPELINE_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(ACCESS_CONTROL_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(IDENTITY_SERVICE.getServiceId(), configuration.getIdentityServiceSecret());
    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
  }

  private void registerInternalApiAuthFilter(AccessControlConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getDefaultServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }

  private SwaggerBundleConfiguration getSwaggerConfiguration(AccessControlConfiguration appConfig) {
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration = new SwaggerBundleConfiguration();
    Collection<Class<?>> classes = getResourceClasses();
    classes.add(AccessControlSwaggerListener.class);
    String resourcePackage = String.join(",", getUniquePackages(classes));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setVersion("1.0");
    defaultSwaggerBundleConfiguration.setHost(appConfig.getHostname());
    defaultSwaggerBundleConfiguration.setUriPrefix(appConfig.getBasePathPrefix());
    defaultSwaggerBundleConfiguration.setTitle("Access Control Service API Reference");
    return defaultSwaggerBundleConfiguration;
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.ACCESSCONTROL) // this is only for locking purpose
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(AccessControlMigrationProvider.class); }
        })
        .build();
  }

  private void initializeEnforcementFramework(Injector injector) {
    Map<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>> featureRestrictionNameClassHashMap =
        ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder().build();
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(featureRestrictionNameClassHashMap)
            .build();
    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends CustomRestrictionInterface>>builder().build())
            .build();
    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }
}
