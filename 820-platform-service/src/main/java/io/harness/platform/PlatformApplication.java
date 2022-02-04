/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform;

import static io.harness.AuthorizationServiceHeader.BEARER;
import static io.harness.AuthorizationServiceHeader.DEFAULT;
import static io.harness.AuthorizationServiceHeader.IDENTITY_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.platform.PlatformConfiguration.getPlatformServiceCombinedResourceClasses;
import static io.harness.platform.audit.AuditServiceSetup.AUDIT_SERVICE;
import static io.harness.platform.notification.NotificationServiceSetup.NOTIFICATION_SERVICE;
import static io.harness.platform.resourcegroup.ResourceGroupServiceSetup.RESOURCE_GROUP_SERVICE;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.stream.Collectors.toSet;

import io.harness.AuthorizationServiceHeader;
import io.harness.GodInjector;
import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.notification.exception.NotificationExceptionMapper;
import io.harness.platform.audit.AuditServiceModule;
import io.harness.platform.audit.AuditServiceSetup;
import io.harness.platform.notification.NotificationServiceModule;
import io.harness.platform.notification.NotificationServiceSetup;
import io.harness.platform.remote.HealthResource;
import io.harness.platform.resourcegroup.ResourceGroupServiceModule;
import io.harness.platform.resourcegroup.ResourceGroupServiceSetup;
import io.harness.remote.NGObjectMapperHelper;
import io.harness.request.RequestContextFilter;
import io.harness.secret.ConfigSecretUtils;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;
import io.harness.swagger.SwaggerBundleConfigurationFactory;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.token.TokenClientModule;
import io.harness.token.remote.TokenClient;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(PL)
public class PlatformApplication extends Application<PlatformConfiguration> {
  private static final String APPLICATION_NAME = "Platform Microservice";
  public static final String PLATFORM_SERVICE = "PlatformService";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new PlatformApplication().run(args);
  }

  private final MetricRegistry metricRegistry = new MetricRegistry();

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<PlatformConfiguration> bootstrap) {
    initializeLogging();
    bootstrap.addBundle(new SwaggerBundle<PlatformConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(PlatformConfiguration appConfig) {
        return getSwaggerConfiguration(appConfig);
      }
    });
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapper(bootstrap.getObjectMapper());
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    NGObjectMapperHelper.configureNGObjectMapper(mapper);
  }

  @Override
  public void run(PlatformConfiguration appConfig, Environment environment) {
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(appConfig.getCommonPoolConfig().getCorePoolSize(),
        appConfig.getCommonPoolConfig().getMaxPoolSize(), appConfig.getCommonPoolConfig().getIdleTime(),
        appConfig.getCommonPoolConfig().getTimeUnit(),
        new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));
    log.info("Starting Platform Application ...");
    ConfigSecretUtils.resolveSecrets(appConfig.getSecretsConfiguration(), appConfig);
    MaintenanceController.forceMaintenance(true);
    GodInjector godInjector = new GodInjector();
    godInjector.put(NOTIFICATION_SERVICE,
        Guice.createInjector(new NotificationServiceModule(appConfig), new MetricRegistryModule(metricRegistry)));
    if (appConfig.getResoureGroupServiceConfig().isEnableResourceGroup()) {
      godInjector.put(RESOURCE_GROUP_SERVICE,
          Guice.createInjector(new ResourceGroupServiceModule(appConfig), new MetricRegistryModule(metricRegistry)));
    }
    if (appConfig.getAuditServiceConfig().isEnableAuditService()) {
      godInjector.put(AUDIT_SERVICE,
          Guice.createInjector(new AuditServiceModule(appConfig), new MetricRegistryModule(metricRegistry)));
    }

    godInjector.put(PLATFORM_SERVICE,
        Guice.createInjector(new TokenClientModule(appConfig.getRbacServiceConfig(),
            appConfig.getPlatformSecrets().getNgManagerServiceSecret(),
            AuthorizationServiceHeader.PLATFORM_SERVICE.getServiceId())));

    registerCommonResources(appConfig, environment, godInjector);
    registerCorsFilter(appConfig, environment);
    registerJerseyProviders(environment);
    registerJerseyFeatures(environment);
    registerAuthFilters(appConfig, environment, godInjector);
    registerRequestContextFilter(environment);
    registerOasResource(appConfig, environment, godInjector.get(PLATFORM_SERVICE));

    new NotificationServiceSetup().setup(
        appConfig.getNotificationServiceConfig(), environment, godInjector.get(NOTIFICATION_SERVICE));

    if (appConfig.getResoureGroupServiceConfig().isEnableResourceGroup()) {
      new ResourceGroupServiceSetup().setup(
          appConfig.getResoureGroupServiceConfig(), environment, godInjector.get(RESOURCE_GROUP_SERVICE));
    }

    if (appConfig.getAuditServiceConfig().isEnableAuditService()) {
      new AuditServiceSetup().setup(appConfig.getAuditServiceConfig(), environment, godInjector.get(AUDIT_SERVICE));
    }

    if (appConfig.getResoureGroupServiceConfig().isEnableResourceGroup()) {
      blockingMigrations(godInjector.get(RESOURCE_GROUP_SERVICE));
    }

    MaintenanceController.forceMaintenance(false);
  }

  private void registerOasResource(PlatformConfiguration appConfig, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(getOasConfig(appConfig));
    environment.jersey().register(openApiResource);
  }

  private void blockingMigrations(Injector injector) {
    //    This is is temporary one time blocking migration
    injector.getInstance(PurgeDeletedResourceGroups.class).cleanUp();
  }

  private void registerCommonResources(
      PlatformConfiguration appConfig, Environment environment, GodInjector godInjector) {
    if (Resource.isAcceptable(HealthResource.class)) {
      List<HealthService> healthServices = new ArrayList<>();
      healthServices.add(godInjector.get(NOTIFICATION_SERVICE).getInstance(HealthService.class));
      if (appConfig.getResoureGroupServiceConfig().isEnableResourceGroup()) {
        healthServices.add(godInjector.get(RESOURCE_GROUP_SERVICE).getInstance(HealthService.class));
      }
      if (appConfig.getAuditServiceConfig().isEnableAuditService()) {
        healthServices.add(godInjector.get(AUDIT_SERVICE).getInstance(HealthService.class));
      }
      environment.jersey().register(new HealthResource(healthServices));
    }
  }

  private void registerJerseyFeatures(Environment environment) {
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerCorsFilter(PlatformConfiguration appConfig, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private OpenAPIConfiguration getOasConfig(PlatformConfiguration appConfig) {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("Platform Service API Reference")
            .description(
                "This is the Open Api Spec 3 for the Platform Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("3.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", appConfig.getHostname(), appConfig.getBasePathPrefix());
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error(
          "The base URL of the server could not be set. {}/{}", appConfig.hostname, appConfig.getBasePathPrefix());
    }
    Collection<Class<?>> allResourceClasses = getPlatformServiceCombinedResourceClasses(appConfig);
    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly(allResourceClasses).stream().map(Class::getCanonicalName).collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  private void registerJerseyProviders(Environment environment) {
    environment.jersey().register(NotificationExceptionMapper.class);
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(NGAccessDeniedExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
  }

  public SwaggerBundleConfiguration getSwaggerConfiguration(PlatformConfiguration appConfig) {
    Collection<Class<?>> classes = getPlatformServiceCombinedResourceClasses(appConfig);
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration(classes);
    String resourcePackage = String.join(",", getUniquePackages(classes));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(appConfig.hostname);
    defaultSwaggerBundleConfiguration.setUriPrefix(appConfig.basePathPrefix);
    defaultSwaggerBundleConfiguration.setVersion("1.0");
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost("{{host}}");
    defaultSwaggerBundleConfiguration.setTitle("Platform Service API Reference");
    return defaultSwaggerBundleConfiguration;
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerAuthFilters(PlatformConfiguration configuration, Environment environment, GodInjector injector) {
    if (configuration.isEnableAuth()) {
      registerNextGenAuthFilter(configuration, environment, injector);
      registerInternalApiAuthFilter(configuration, environment);
    }
  }

  private void registerNextGenAuthFilter(
      PlatformConfiguration configuration, Environment environment, GodInjector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        (getAuthenticationExemptedRequestsPredicate().negate())
            .and((getAuthFilterPredicate(InternalApi.class)).negate());
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(BEARER.getServiceId(), configuration.getPlatformSecrets().getJwtAuthSecret());
    serviceToSecretMapping.put(
        IDENTITY_SERVICE.getServiceId(), configuration.getPlatformSecrets().getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getPlatformSecrets().getNgManagerServiceSecret());
    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.get(PLATFORM_SERVICE).getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
  }

  private void registerInternalApiAuthFilter(PlatformConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getPlatformSecrets().getNgManagerServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthenticationExemptedRequestsPredicate() {
    return getAuthFilterPredicate(PublicApi.class)
        .or(resourceInfoAndRequest
            -> resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("api/version")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("api/swagger")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith(
                    "api/swagger.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/openapi.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith(
                    "api/swagger.yaml"));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly(Collection<Class<?>> allResourceClasses) {
    return allResourceClasses.stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }
}
