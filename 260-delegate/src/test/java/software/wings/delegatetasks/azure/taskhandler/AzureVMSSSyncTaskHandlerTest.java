/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureNetworkClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureVMData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.request.AzureVMSSGetVirtualMachineScaleSetParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancerBackendPoolsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancersNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListResourceGroupsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListSubscriptionsParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVMDataParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVirtualMachineScaleSetsParameters;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSGetVirtualMachineScaleSetResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancerBackendPoolsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancersNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListResourceGroupsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListSubscriptionsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVMDataResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVirtualMachineScaleSetsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVMs;
import com.microsoft.azure.management.compute.VirtualMachineSizeTypes;
import com.microsoft.azure.management.compute.implementation.VirtualMachineScaleSetVMInner;
import com.microsoft.azure.management.network.LoadBalancer;
import com.microsoft.azure.management.network.LoadBalancerBackend;
import com.microsoft.azure.management.network.PublicIPAddressDnsSettings;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.resources.Subscription;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSSyncTaskHandlerTest extends WingsBaseTest {
  @Mock private AzureComputeClient mockAzureComputeClient;
  @Mock private AzureNetworkClient mockAzureNetworkClient;
  @Mock VirtualMachineScaleSetVMs virtualMachineScaleSetVMs;
  @Mock VirtualMachineScaleSetVM virtualMachineScaleSetVM;
  @Mock VirtualMachineScaleSet virtualMachineScaleSet;
  @Mock Subscription subscription;

  @Spy @InjectMocks AzureVMSSSyncTaskHandler handler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListSubscriptions() {
    doReturn(Collections.singletonList(subscription)).when(mockAzureComputeClient).listSubscriptions(any());

    AzureVMSSListSubscriptionsParameters azureVMSSListSubscriptionsParameters =
        AzureVMSSListSubscriptionsParameters.builder().build();
    AzureVMSSTaskExecutionResponse response =
        handler.executeTaskInternal(azureVMSSListSubscriptionsParameters, AzureConfig.builder().build());

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskExecutionResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskExecutionResponse).isNotNull();
    assertThat(azureVMSSTaskExecutionResponse instanceof AzureVMSSListSubscriptionsResponse).isTrue();

    AzureVMSSListSubscriptionsResponse listResourceGroupsNamesResponse =
        (AzureVMSSListSubscriptionsResponse) azureVMSSTaskExecutionResponse;
    assertThat(listResourceGroupsNamesResponse.getSubscriptions().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListResourceGroupsNames() {
    List<String> resourceGroupList = Collections.singletonList("resourceGroup1, resourceGroup2");
    doReturn(resourceGroupList)
        .when(mockAzureComputeClient)
        .listResourceGroupsNamesBySubscriptionId(any(), anyString());

    AzureVMSSListResourceGroupsNamesParameters azureVMSSTaskParameters =
        AzureVMSSListResourceGroupsNamesParameters.builder().subscriptionId("subscriptionId").build();
    AzureVMSSTaskExecutionResponse response =
        handler.executeTaskInternal(azureVMSSTaskParameters, AzureConfig.builder().build());

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskExecutionResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskExecutionResponse).isNotNull();
    assertThat(azureVMSSTaskExecutionResponse instanceof AzureVMSSListResourceGroupsNamesResponse).isTrue();

    AzureVMSSListResourceGroupsNamesResponse listResourceGroupsNamesResponse =
        (AzureVMSSListResourceGroupsNamesResponse) azureVMSSTaskExecutionResponse;
    assertThat(listResourceGroupsNamesResponse.getResourceGroupsNames()).isEqualTo(resourceGroupList);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListVirtualMachineScaleSets() {
    doReturn(Collections.singletonList(virtualMachineScaleSet))
        .when(mockAzureComputeClient)
        .listVirtualMachineScaleSetsByResourceGroupName(any(), anyString(), anyString());

    AzureVMSSListVirtualMachineScaleSetsParameters azureVMSSListVirtualMachineScaleSetsParameters =
        AzureVMSSListVirtualMachineScaleSetsParameters.builder().build();
    AzureVMSSTaskExecutionResponse response =
        handler.executeTaskInternal(azureVMSSListVirtualMachineScaleSetsParameters, AzureConfig.builder().build());

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskExecutionResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskExecutionResponse).isNotNull();
    assertThat(azureVMSSTaskExecutionResponse instanceof AzureVMSSListVirtualMachineScaleSetsResponse).isTrue();

    AzureVMSSListVirtualMachineScaleSetsResponse listVirtualMachineScaleSetsResponse =
        (AzureVMSSListVirtualMachineScaleSetsResponse) azureVMSSTaskExecutionResponse;
    assertThat(listVirtualMachineScaleSetsResponse.getVirtualMachineScaleSets().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVirtualMachineScaleSets() {
    doReturn(Optional.of(virtualMachineScaleSet))
        .when(mockAzureComputeClient)
        .getVirtualMachineScaleSetByName(any(), anyString(), anyString(), any());

    PagedList<VirtualMachineScaleSetVM> pageList = getPageList();
    pageList.add(virtualMachineScaleSetVM);
    doReturn(virtualMachineScaleSetVMs).when(virtualMachineScaleSet).virtualMachines();
    doReturn(pageList).when(virtualMachineScaleSetVMs).list();
    doReturn("administratorName").when(virtualMachineScaleSetVM).administratorUserName();

    AzureVMSSGetVirtualMachineScaleSetParameters azureVMSSGetVirtualMachineScaleSetParameters =
        AzureVMSSGetVirtualMachineScaleSetParameters.builder().build();
    AzureVMSSTaskExecutionResponse response =
        handler.executeTaskInternal(azureVMSSGetVirtualMachineScaleSetParameters, AzureConfig.builder().build());

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskExecutionResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskExecutionResponse).isNotNull();
    assertThat(azureVMSSTaskExecutionResponse instanceof AzureVMSSGetVirtualMachineScaleSetResponse).isTrue();

    AzureVMSSGetVirtualMachineScaleSetResponse getVirtualMachineScaleSetResponse =
        (AzureVMSSGetVirtualMachineScaleSetResponse) azureVMSSTaskExecutionResponse;
    assertThat(getVirtualMachineScaleSetResponse.getVirtualMachineScaleSet()).isNotNull();
    assertThat(getVirtualMachineScaleSetResponse.getVirtualMachineScaleSet().getVirtualMachineAdministratorUsername())
        .isEqualTo("administratorName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLoadBalancerNames() {
    LoadBalancer mockLoadBalancer = mock(LoadBalancer.class);
    doReturn("loadBalancerName").when(mockLoadBalancer).name();
    doReturn(Collections.singletonList(mockLoadBalancer))
        .when(mockAzureNetworkClient)
        .listLoadBalancersByResourceGroup(any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"));

    AzureVMSSListLoadBalancersNamesParameters azureVMSSListLoadBalancersNamesParameters =
        AzureVMSSListLoadBalancersNamesParameters.builder()
            .subscriptionId("subscriptionId")
            .resourceGroupName("resourceGroupName")
            .build();

    AzureVMSSTaskExecutionResponse response =
        handler.executeTaskInternal(azureVMSSListLoadBalancersNamesParameters, AzureConfig.builder().build());

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskExecutionResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskExecutionResponse).isNotNull();
    assertThat(azureVMSSTaskExecutionResponse instanceof AzureVMSSListLoadBalancersNamesResponse).isTrue();

    AzureVMSSListLoadBalancersNamesResponse listLoadBalancersNamesResponse =
        (AzureVMSSListLoadBalancersNamesResponse) azureVMSSTaskExecutionResponse;
    assertThat(listLoadBalancersNamesResponse.getLoadBalancersNames()).isNotNull();
    assertThat(listLoadBalancersNamesResponse.getLoadBalancersNames()).isNotEmpty();
    assertThat(listLoadBalancersNamesResponse.getLoadBalancersNames().get(0)).isEqualTo("loadBalancerName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListLoadBalancerBackendPoolsNames() {
    LoadBalancerBackend loadBalancerBackend = mock(LoadBalancerBackend.class);
    doReturn("backendPoolName").when(loadBalancerBackend).name();
    doReturn(Collections.singletonList(loadBalancerBackend))
        .when(mockAzureNetworkClient)
        .listLoadBalancerBackendPools(
            any(AzureConfig.class), eq("subscriptionId"), eq("resourceGroupName"), eq("loadBalancerName"));

    AzureVMSSListLoadBalancerBackendPoolsNamesParameters listLoadBalancerBackendPoolsNamesParameters =
        AzureVMSSListLoadBalancerBackendPoolsNamesParameters.builder()
            .subscriptionId("subscriptionId")
            .resourceGroupName("resourceGroupName")
            .loadBalancerName("loadBalancerName")
            .build();

    AzureVMSSTaskExecutionResponse response =
        handler.executeTaskInternal(listLoadBalancerBackendPoolsNamesParameters, AzureConfig.builder().build());

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskExecutionResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskExecutionResponse).isNotNull();
    assertThat(azureVMSSTaskExecutionResponse instanceof AzureVMSSListLoadBalancerBackendPoolsNamesResponse).isTrue();

    AzureVMSSListLoadBalancerBackendPoolsNamesResponse listLoadBalancerBackendPoolsNamesResponse =
        (AzureVMSSListLoadBalancerBackendPoolsNamesResponse) azureVMSSTaskExecutionResponse;
    assertThat(listLoadBalancerBackendPoolsNamesResponse.getLoadBalancerBackendPoolsNames()).isNotNull();
    assertThat(listLoadBalancerBackendPoolsNamesResponse.getLoadBalancerBackendPoolsNames()).isNotEmpty();
    assertThat(listLoadBalancerBackendPoolsNamesResponse.getLoadBalancerBackendPoolsNames().get(0))
        .isEqualTo("backendPoolName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListVirtualMachineScaleSetVMs() {
    String ipAddress = "IpAddress";
    String fqdn = "FQDN";
    VirtualMachineScaleSetVMInner virtualMachineScaleSetVMInner = mock(VirtualMachineScaleSetVMInner.class);
    doReturn("vmId").when(virtualMachineScaleSetVMInner).id();

    VirtualMachineScaleSetVM virtualMachineScaleSetVM = mock(VirtualMachineScaleSetVM.class);
    doReturn(virtualMachineScaleSetVMInner).when(virtualMachineScaleSetVM).inner();

    PowerState powerState = PowerState.RUNNING;
    doReturn(powerState).when(virtualMachineScaleSetVM).powerState();

    VirtualMachineSizeTypes virtualMachineSizeTypes = VirtualMachineSizeTypes.STANDARD_B1S;
    doReturn(virtualMachineSizeTypes).when(virtualMachineScaleSetVM).size();

    mockGetVMPublicIPAddress(ipAddress, fqdn);

    doReturn(Collections.singletonList(virtualMachineScaleSetVM))
        .when(mockAzureComputeClient)
        .listVirtualMachineScaleSetVMs(any(AzureConfig.class), eq("subscriptionId"), eq("vmssId"));

    AzureVMSSListVMDataParameters listVMDataParameters =
        AzureVMSSListVMDataParameters.builder().subscriptionId("subscriptionId").vmssId("vmssId").build();

    AzureVMSSTaskExecutionResponse response =
        handler.executeTaskInternal(listVMDataParameters, AzureConfig.builder().build());

    assertThat(response).isNotNull();
    AzureVMSSTaskResponse azureVMSSTaskExecutionResponse = response.getAzureVMSSTaskResponse();
    assertThat(azureVMSSTaskExecutionResponse).isNotNull();
    assertThat(azureVMSSTaskExecutionResponse instanceof AzureVMSSListVMDataResponse).isTrue();

    AzureVMSSListVMDataResponse listVMDataResponse = (AzureVMSSListVMDataResponse) azureVMSSTaskExecutionResponse;
    assertThat(listVMDataResponse.getVmData()).isNotNull();
    assertThat(listVMDataResponse.getVmData()).isNotEmpty();
    assertThat(listVMDataResponse.getVmData().get(0))
        .isEqualToComparingFieldByField(AzureVMData.builder()
                                            .id("vmId")
                                            .ip(ipAddress)
                                            .powerState("running")
                                            .size(String.valueOf(VirtualMachineSizeTypes.STANDARD_B1S))
                                            .publicDns(fqdn)
                                            .build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNoSyncTask() {
    AzureVMSSSetupTaskParameters noSyncTask = AzureVMSSSetupTaskParameters.builder().build();

    assertThatThrownBy(() -> handler.executeTaskInternal(noSyncTask, AzureConfig.builder().build()))
        .isInstanceOfAny(InvalidRequestException.class)
        .hasMessage("Unrecognized object of class: [AZURE_VMSS_SETUP] while executing sync task");
  }

  private void mockGetVMPublicIPAddress(String ipAddress, String fqdn) {
    PublicIPAddressInner publicIPAddressInner = new PublicIPAddressInner();
    publicIPAddressInner.withIpAddress(ipAddress);
    PublicIPAddressDnsSettings dnsSettings = new PublicIPAddressDnsSettings();
    dnsSettings.withFqdn(fqdn);
    publicIPAddressInner.withDnsSettings(dnsSettings);
    doReturn(Optional.of(publicIPAddressInner)).when(mockAzureComputeClient).getVMPublicIPAddress(any());
  }

  @NotNull
  public PagedList<VirtualMachineScaleSetVM> getPageList() {
    return new PagedList<VirtualMachineScaleSetVM>() {
      @Override
      public Page<VirtualMachineScaleSetVM> nextPage(String s) {
        return new Page<VirtualMachineScaleSetVM>() {
          @Override
          public String nextPageLink() {
            return null;
          }
          @Override
          public List<VirtualMachineScaleSetVM> items() {
            return null;
          }
        };
      }
    };
  }
}
