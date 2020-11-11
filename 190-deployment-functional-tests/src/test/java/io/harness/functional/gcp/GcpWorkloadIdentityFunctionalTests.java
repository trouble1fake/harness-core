package io.harness.functional.gcp;

import static io.harness.rule.OwnerRule.ABOSII;
import static java.lang.System.getProperty;
import static org.assertj.core.api.Assertions.assertThat;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;

import com.google.inject.Inject;

import io.harness.category.element.CDFunctionalTests;
import io.harness.filesystem.FileIo;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.testframework.restutils.SettingsUtils;
import io.restassured.path.json.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class GcpWorkloadIdentityFunctionalTests extends AbstractFunctionalTest {
  private static final String GCLOUD_WELL_KNOWN_FILE_LOCATION = ".config/gcloud/application_default_credentials.json";
  private static final String GCLOUDD_CREDENTIALS_ENV_VAR = "GOOGLE_APPLICATION_CREDENTIALS";
  private static final String CLOUD_PROVIDER_NAME = "GCP Workload Identity Non-K8s Env Test";
  private static final String DELEGATE_SELECTOR = "primary";

  @Inject ScmSecret scmSecret;

  private boolean cleanupCredentialsFile;
  private String credentialsFilePath;

  @Before
  public void setUp() {
    setupCredentialFile();
  }

  @After
  public void cleanUp() {
    if (cleanupCredentialsFile && credentialsFilePath != null) {
      try {
        FileIo.deleteFileIfExists(credentialsFilePath);
      } catch (IOException exception) {
        log.error("Unable to clean credentials file at {}", credentialsFilePath, exception);
      }
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(CDFunctionalTests.class)
  // Even if delegate is running in k8s, we expect that credentials(env) file has priority
  public void testCreateGcpCloudProviderInNonK8sDelegate() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withName(CLOUD_PROVIDER_NAME)
                                            .withCategory(CLOUD_PROVIDER)
                                            .withAccountId(getAccount().getUuid())
                                            .withValue(GcpConfig.builder()
                                                           .accountId(getAccount().getUuid())
                                                           .useDelegate(true)
                                                           .delegateSelector(DELEGATE_SELECTOR)
                                                           .build())
                                            .build();
    try {
      deleteExistingCloudProvider();
      checkConnectivity(settingAttribute);
      checkCreated(settingAttribute);
    } finally {
      deleteExistingCloudProvider();
    }
  }

  private void setupCredentialFile() {
    if (setupEnvironmentCredentials()) {
      return;
    }

    File configPath = new File(getProperty("user.home", ""), ".config");
    File credentialsFile = new File(configPath, GCLOUD_WELL_KNOWN_FILE_LOCATION);
    log.info("Check for predefined credentials file at {}", credentialsFile.getPath());
    if (credentialsFile.exists()) {
      return;
    }

    throw new IllegalStateException("Unable to find gcloud credentials file");
  }

  private boolean setupEnvironmentCredentials() {
    String envFilePath = System.getenv(GCLOUDD_CREDENTIALS_ENV_VAR);
    if (envFilePath == null) {
      log.info("No environment variable {} defined for", GCLOUDD_CREDENTIALS_ENV_VAR);
      return false;
    }

    File envCredentialsFile = new File(envFilePath);
    // For our tests it should be enough any credentials file.
    // In case if env var is already defined we should be fine anyway
    if (envCredentialsFile.exists()) {
      log.info("Environment file already exists, will not override it");
      return true;
    }

    try {
      FileIo.createDirectoryIfDoesNotExist(envCredentialsFile.getParent());
      FileIo.writeFile(envFilePath, scmSecret.decrypt(new SecretName("gcp_playground")));
      cleanupCredentialsFile = true;
      credentialsFilePath = envFilePath;
      return true;
    } catch (IOException exception) {
      log.error("Unable to create credentials file for path {}", envFilePath, exception);
      return false;
    }
  }

  private void deleteExistingCloudProvider() {
    JsonPath response =
        SettingsUtils.listCloudproviderConnector(bearerToken, getAccount().getUuid(), CLOUD_PROVIDER.name());
    List<Map<String, Object>> existingConnectors = response.getList("resource.response");
    existingConnectors.stream()
        .filter(setting -> CLOUD_PROVIDER_NAME.equals(setting.get("name")))
        .findFirst()
        .ifPresent(setting -> SettingsUtils.delete(bearerToken, getAccount().getUuid(), (String) setting.get("uuid")));
  }

  private void checkConnectivity(SettingAttribute settingAttribute) {
    JsonPath json = SettingsUtils.validateConnectivity(bearerToken, getAccount().getUuid(), settingAttribute);
    assertThat((Object) json.get("resource")).isNotNull();
    assertThat((Boolean) json.get("resource.valid")).isTrue();
  }

  private void checkCreated(SettingAttribute settingAttribute) {
    JsonPath json = SettingsUtils.create(bearerToken, getAccount().getUuid(), settingAttribute);
    assertThat((Object) json.get("resource")).isNotNull();
    assertThat((Object) json.get("resource.uuid")).isNotNull();
  }
}
