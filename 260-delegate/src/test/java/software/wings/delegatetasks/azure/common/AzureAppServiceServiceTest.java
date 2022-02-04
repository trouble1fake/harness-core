/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.common;

import static io.harness.azure.model.AzureConstants.SAVE_EXISTING_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.WEB_APP_INSTANCE_STATUS_RUNNING;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.microsoft.azure.management.appservice.DeploymentSlot;
import com.microsoft.azure.management.appservice.implementation.SiteInstanceInner;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureAppServiceServiceTest extends WingsBaseTest {
  private static final String SLOT_NAME = "slotName";
  private static final String TARGET_SLOT_NAME = "targetSlotName";
  private static final String APP_NAME = "appName";
  private static final String RESOURCE_GROUP_NAME = "resourceGroupName";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String IMAGE_AND_TAG = "image/tag";
  private static final String DEPLOYMENT_SLOT_ID = "deploymentSlotId";
  private static final String APP_SERVICE_PLAN_ID = "appServicePlanId";
  private static final String DEFAULT_HOST_NAME = "defaultHostName";

  @Mock private AzureWebClient mockAzureWebClient;

  @Spy @InjectMocks AzureAppServiceService azureAppServiceService;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFetchDeploymentDataNoDeploymentSlot() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();
    doReturn(Optional.empty()).when(mockAzureWebClient).getDeploymentSlotByName(azureWebClientContext, SLOT_NAME);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> azureAppServiceService.fetchDeploymentData(azureWebClientContext, SLOT_NAME));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetAzureAppServicePreDeploymentData() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();
    Map<String, AzureAppServiceApplicationSetting> userAddedAppSettings = new HashMap<>();
    userAddedAppSettings.put("appSetting1", getAppSettings("appSetting1"));
    userAddedAppSettings.put("appSetting2", getAppSettings("appSetting2"));

    Map<String, AzureAppServiceConnectionString> userAddedConnSettings = new HashMap<>();

    userAddedConnSettings.put("connSetting1", getConnectionSettings("connSetting1"));
    userAddedConnSettings.put("connSetting2", getConnectionSettings("connSetting2"));

    Map<String, AzureAppServiceApplicationSetting> existingAppSettingsOnSlot = new HashMap<>();
    existingAppSettingsOnSlot.put("appSetting1", getAppSettings("appSetting1"));

    doReturn(existingAppSettingsOnSlot)
        .when(mockAzureWebClient)
        .listDeploymentSlotAppSettings(azureWebClientContext, SLOT_NAME);

    Map<String, AzureAppServiceConnectionString> existingConnSettingsOnSlot = new HashMap<>();
    existingConnSettingsOnSlot.put("connSetting1", getConnectionSettings("connSetting1"));

    doReturn(existingConnSettingsOnSlot)
        .when(mockAzureWebClient)
        .listDeploymentSlotConnectionStrings(azureWebClientContext, SLOT_NAME);

    Map<String, AzureAppServiceApplicationSetting> dockerSettingsNeedBeUpdatedInRollback = new HashMap<>();
    doReturn(dockerSettingsNeedBeUpdatedInRollback)
        .when(mockAzureWebClient)
        .listDeploymentSlotDockerSettings(azureWebClientContext, SLOT_NAME);

    Optional<String> dockerImageNameAndTag = Optional.of(IMAGE_AND_TAG);
    doReturn(dockerImageNameAndTag)
        .when(mockAzureWebClient)
        .getSlotDockerImageNameAndTag(azureWebClientContext, SLOT_NAME);

    doReturn(2.0).when(mockAzureWebClient).getDeploymentSlotTrafficWeight(azureWebClientContext, SLOT_NAME);

    ILogStreamingTaskClient logStreamingTaskClient = mock(ILogStreamingTaskClient.class);
    LogCallback logCallback = mock(LogCallback.class);
    doReturn(logCallback).when(logStreamingTaskClient).obtainLogCallback(SAVE_EXISTING_CONFIGURATIONS);

    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        azureAppServiceService.getAzureAppServicePreDeploymentDataAndLog(azureWebClientContext, SLOT_NAME,
            TARGET_SLOT_NAME, userAddedAppSettings, userAddedConnSettings, true, logStreamingTaskClient);

    Assertions.assertThat(azureAppServicePreDeploymentData.getAppSettingsToRemove().get("appSetting2")).isNotNull();
    Assertions.assertThat(azureAppServicePreDeploymentData.getAppSettingsToAdd().get("appSetting1")).isNotNull();
    Assertions.assertThat(azureAppServicePreDeploymentData.getConnStringsToRemove().get("connSetting2")).isNotNull();
    Assertions.assertThat(azureAppServicePreDeploymentData.getConnStringsToAdd().get("connSetting1")).isNotNull();
    Assertions.assertThat(azureAppServicePreDeploymentData.getAppSettingsToRemove().size()).isEqualTo(1);
    Assertions.assertThat(azureAppServicePreDeploymentData.getAppSettingsToAdd().size()).isEqualTo(1);
    Assertions.assertThat(azureAppServicePreDeploymentData.getConnStringsToRemove().size()).isEqualTo(1);
    Assertions.assertThat(azureAppServicePreDeploymentData.getConnStringsToAdd().size()).isEqualTo(1);
    Assertions.assertThat(azureAppServicePreDeploymentData.getDockerSettingsToAdd().size()).isEqualTo(0);
    Assertions.assertThat(azureAppServicePreDeploymentData.getAppName()).isEqualTo(APP_NAME);
    Assertions.assertThat(azureAppServicePreDeploymentData.getImageNameAndTag()).isEqualTo(IMAGE_AND_TAG);
    Assertions.assertThat(azureAppServicePreDeploymentData.getTrafficWeight()).isEqualTo(2);

    assertThatThrownBy(
        ()
            -> azureAppServiceService.getAzureAppServicePreDeploymentDataAndLog(azureWebClientContext, "",
                TARGET_SLOT_NAME, userAddedAppSettings, userAddedConnSettings, true, logStreamingTaskClient))
        .isInstanceOf(Exception.class);

    doReturn("STOPPED").when(mockAzureWebClient).getSlotState(any(), eq(TARGET_SLOT_NAME));
    assertThatThrownBy(
        ()
            -> azureAppServiceService.getAzureAppServicePreDeploymentDataAndLog(azureWebClientContext, SLOT_NAME,
                TARGET_SLOT_NAME, userAddedAppSettings, userAddedConnSettings, true, logStreamingTaskClient))
        .isInstanceOf(Exception.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFetchDeploymentData() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();
    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(DEPLOYMENT_SLOT_ID).when(deploymentSlot).id();
    doReturn(APP_SERVICE_PLAN_ID).when(deploymentSlot).appServicePlanId();
    doReturn(DEFAULT_HOST_NAME).when(deploymentSlot).defaultHostName();

    doReturn(Optional.of(deploymentSlot))
        .when(mockAzureWebClient)
        .getDeploymentSlotByName(azureWebClientContext, SLOT_NAME);

    SiteInstanceInner siteInstanceInner = mock(SiteInstanceInner.class);
    doReturn("id").when(siteInstanceInner).id();
    doReturn("name").when(siteInstanceInner).name();
    doReturn("type").when(siteInstanceInner).type();

    doReturn(Collections.singletonList(siteInstanceInner))
        .when(mockAzureWebClient)
        .listInstanceIdentifiersSlot(azureWebClientContext, SLOT_NAME);

    List<AzureAppDeploymentData> azureAppDeploymentDataList =
        azureAppServiceService.fetchDeploymentData(azureWebClientContext, SLOT_NAME);

    Assertions.assertThat(azureAppDeploymentDataList.size()).isEqualTo(1);
    AzureAppDeploymentData appDeploymentData = azureAppDeploymentDataList.get(0);
    Assertions.assertThat(appDeploymentData.getInstanceState()).isEqualTo(WEB_APP_INSTANCE_STATUS_RUNNING);
    Assertions.assertThat(appDeploymentData.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
    Assertions.assertThat(appDeploymentData.getResourceGroup()).isEqualTo(RESOURCE_GROUP_NAME);
    Assertions.assertThat(appDeploymentData.getAppName()).isEqualTo(APP_NAME);
    Assertions.assertThat(appDeploymentData.getDeploySlot()).isEqualTo(SLOT_NAME);
    Assertions.assertThat(appDeploymentData.getDeploySlotId()).isEqualTo(DEPLOYMENT_SLOT_ID);
    Assertions.assertThat(appDeploymentData.getAppServicePlanId()).isEqualTo(APP_SERVICE_PLAN_ID);
    Assertions.assertThat(appDeploymentData.getHostName()).isEqualTo(DEFAULT_HOST_NAME);
    Assertions.assertThat(appDeploymentData.getInstanceName()).isEqualTo("name");
    Assertions.assertThat(appDeploymentData.getInstanceId()).isEqualTo("id");
    Assertions.assertThat(appDeploymentData.getInstanceType()).isEqualTo("type");
  }

  private AzureWebClientContext getAzureWebClientContext() {
    return AzureWebClientContext.builder()
        .appName(APP_NAME)
        .resourceGroupName(RESOURCE_GROUP_NAME)
        .subscriptionId(SUBSCRIPTION_ID)
        .azureConfig(AzureConfig.builder().build())
        .build();
  }

  private AzureAppServiceConnectionString getConnectionSettings(String name) {
    return AzureAppServiceConnectionString.builder().name(name).value(name + "value").build();
  }

  private AzureAppServiceApplicationSetting getAppSettings(String name) {
    return AzureAppServiceApplicationSetting.builder().name(name).value(name + "value").build();
  }
}
