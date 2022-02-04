/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.deployment;

import static io.harness.azure.model.AzureConstants.SLOT_STARTING_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.SLOT_STOPPING_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.START_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.STOP_DEPLOYMENT_SLOT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.LogStreamingTaskClient;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;

import com.microsoft.azure.management.appservice.DeploymentSlot;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import rx.Completable;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureAppServiceDeploymentServiceTest extends WingsBaseTest {
  private static final String SLOT_NAME = "slotName";
  private static final String TARGET_SLOT_NAME = "targetSlotName";
  private static final String APP_NAME = "appName";
  private static final String RESOURCE_GROUP_NAME = "resourceGroupName";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String IMAGE_AND_TAG = "image/tag";

  @Mock private AzureWebClient mockAzureWebClient;
  @Mock private LogStreamingTaskClient mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private SlotSteadyStateChecker slotSteadyStateChecker;

  @Spy @InjectMocks AzureAppServiceDeploymentService azureAppServiceDeploymentService;

  @Before
  public void setup() {
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDeployDockerImage() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove =
        Collections.singletonMap("appSetting2", getAppSettings("appSetting2"));
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        Collections.singletonMap("appSetting1", getAppSettings("appSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToRemove =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceApplicationSetting> dockerSettings = Collections.singletonMap("dockerSetting1",
        AzureAppServiceApplicationSetting.builder().name("dockerSetting1").value("dockerSetting1value").build());

    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logStreamingTaskClient(mockLogStreamingTaskClient)
            .dockerSettings(dockerSettings)
            .azureWebClientContext(azureWebClientContext)
            .appSettingsToAdd(appSettingsToAdd)
            .appSettingsToRemove(appSettingsToRemove)
            .connSettingsToAdd(connSettingsToAdd)
            .connSettingsToRemove(connSettingsToRemove)
            .build();

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Completable.complete()).when(deploymentSlot).stopAsync();
    doReturn(Completable.complete()).when(deploymentSlot).startAsync();
    doReturn(Optional.of(deploymentSlot))
        .when(mockAzureWebClient)
        .getDeploymentSlotByName(azureWebClientContext, SLOT_NAME);

    azureAppServiceDeploymentService.deployDockerImage(
        azureAppServiceDockerDeploymentContext, AzureAppServicePreDeploymentData.builder().build());

    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STOPPING_STATUS_CHECK_INTERVAL), any(), eq(STOP_DEPLOYMENT_SLOT), any());
    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STARTING_STATUS_CHECK_INTERVAL), any(), eq(START_DEPLOYMENT_SLOT), any());
    verify(mockAzureWebClient, times(1))
        .deleteDeploymentSlotAppSettings(azureWebClientContext, SLOT_NAME, appSettingsToRemove);
    verify(mockAzureWebClient, times(1))
        .updateDeploymentSlotAppSettings(azureWebClientContext, SLOT_NAME, appSettingsToAdd);
    verify(mockAzureWebClient, times(1))
        .deleteDeploymentSlotConnectionStrings(azureWebClientContext, SLOT_NAME, connSettingsToRemove);
    verify(mockAzureWebClient, times(1))
        .updateDeploymentSlotConnectionStrings(azureWebClientContext, SLOT_NAME, connSettingsToAdd);
    verify(mockAzureWebClient, times(1))
        .updateDeploymentSlotDockerSettings(azureWebClientContext, SLOT_NAME, dockerSettings);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDeployDockerImageFailure() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove =
        Collections.singletonMap("appSetting2", getAppSettings("appSetting2"));
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        Collections.singletonMap("appSetting1", getAppSettings("appSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToRemove =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceApplicationSetting> dockerSettings = Collections.singletonMap("dockerSetting1",
        AzureAppServiceApplicationSetting.builder().name("dockerSetting1").value("dockerSetting1value").build());

    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logStreamingTaskClient(mockLogStreamingTaskClient)
            .dockerSettings(dockerSettings)
            .azureWebClientContext(azureWebClientContext)
            .appSettingsToAdd(appSettingsToAdd)
            .appSettingsToRemove(appSettingsToRemove)
            .connSettingsToAdd(connSettingsToAdd)
            .connSettingsToRemove(connSettingsToRemove)
            .build();

    doThrow(Exception.class).when(mockAzureWebClient).deleteDeploymentSlotAppSettings(any(), any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);

    reset(mockAzureWebClient);
    doThrow(Exception.class).when(mockAzureWebClient).stopDeploymentSlotAsync(any(), any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);

    reset(mockAzureWebClient);
    doThrow(Exception.class).when(mockAzureWebClient).startDeploymentSlotAsync(any(), any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);

    reset(mockAzureWebClient);
    doThrow(Exception.class).when(mockAzureWebClient).deleteDeploymentSlotDockerSettings(any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDeployDockerImageNoAppAndConnSettingsToAddOrRemove() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logStreamingTaskClient(mockLogStreamingTaskClient)
            .dockerSettings(new HashMap<>())
            .azureWebClientContext(azureWebClientContext)
            .build();

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Completable.complete()).when(deploymentSlot).stopAsync();
    doReturn(Completable.complete()).when(deploymentSlot).startAsync();
    doReturn(Optional.of(deploymentSlot))
        .when(mockAzureWebClient)
        .getDeploymentSlotByName(azureWebClientContext, SLOT_NAME);

    azureAppServiceDeploymentService.deployDockerImage(
        azureAppServiceDockerDeploymentContext, AzureAppServicePreDeploymentData.builder().build());

    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STOPPING_STATUS_CHECK_INTERVAL), any(), eq(STOP_DEPLOYMENT_SLOT), any());
    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STARTING_STATUS_CHECK_INTERVAL), any(), eq(START_DEPLOYMENT_SLOT), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRerouteProductionSlotTraffic() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();
    azureAppServiceDeploymentService.rerouteProductionSlotTraffic(
        azureWebClientContext, SLOT_NAME, 50, mockLogStreamingTaskClient);
    verify(mockAzureWebClient, times(1)).rerouteProductionSlotTraffic(azureWebClientContext, SLOT_NAME, 50);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void swapSlotsUsingCallback() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();
    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logStreamingTaskClient(mockLogStreamingTaskClient)
            .dockerSettings(new HashMap<>())
            .azureWebClientContext(azureWebClientContext)
            .build();

    azureAppServiceDeploymentService.swapSlotsUsingCallback(
        azureAppServiceDockerDeploymentContext, TARGET_SLOT_NAME, mockLogStreamingTaskClient);

    ArgumentCaptor<SlotStatusVerifier> statusVerifierArgument = ArgumentCaptor.forClass(SlotStatusVerifier.class);

    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STOPPING_STATUS_CHECK_INTERVAL), any(), eq(SLOT_SWAP), statusVerifierArgument.capture());
    SlotStatusVerifier statusVerifier = statusVerifierArgument.getValue();
    assertThat(statusVerifier).isNotNull();
    assertThat(statusVerifier).isInstanceOf(SwapSlotStatusVerifier.class);
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
