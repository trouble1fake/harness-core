/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;

/**
 * Delegate task handler to setup CI build environment on a K8 cluster including creation of pod as well as image and
 * git secrets.
 */

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.ci.k8s.PodStatus.Status.RUNNING;
import static io.harness.delegate.beans.ci.pod.CIContainerType.LITE_ENGINE;
import static io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder.getSecretName;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerParams;
import io.harness.delegate.beans.ci.pod.PodParams;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.ci.pod.SecretVarParams;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.citasks.CIInitializeTaskHandler;
import io.harness.delegate.task.citasks.cik8handler.helper.DelegateServiceTokenHelper;
import io.harness.delegate.task.citasks.cik8handler.helper.ProxyVariableHelper;
import io.harness.delegate.task.citasks.cik8handler.k8java.CIK8JavaClientHandler;
import io.harness.delegate.task.citasks.cik8handler.k8java.pod.PodSpecBuilder;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Watch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIK8InitializeTaskHandler implements CIInitializeTaskHandler {
  @Inject private CIK8JavaClientHandler cik8JavaClientHandler;
  @Inject private PodSpecBuilder podSpecBuilder;
  @Inject private K8sConnectorHelper k8sConnectorHelper;
  @Inject private SecretSpecBuilder secretSpecBuilder;
  @Inject private KubernetesHelperService kubernetesHelperService;
  @Inject private K8EventHandler k8EventHandler;
  @Inject private ProxyVariableHelper proxyVariableHelper;
  @Inject private DelegateServiceTokenHelper delegateServiceTokenHelper;
  @Inject private ApiClientFactory apiClientFactory;

  @NotNull private Type type = CIInitializeTaskHandler.Type.GCP_K8;

  private static final String DOCKER_CONFIG_KEY = ".dockercfg";
  private static final String HARNESS_IMAGE_SECRET = "HARNESS_IMAGE_SECRET";
  private static final String HARNESS_SECRETS_LIST = "HARNESS_SECRETS_LIST";

  @Override
  public Type getType() {
    return type;
  }

  public K8sTaskExecutionResponse executeTaskInternal(
      CIInitializeTaskParams ciInitializeTaskParams, ILogStreamingTaskClient logStreamingTaskClient, String taskId) {
    Stopwatch timer = Stopwatch.createStarted();
    CIK8InitializeTaskParams cik8InitializeTaskParams = (CIK8InitializeTaskParams) ciInitializeTaskParams;
    String cik8BuildTaskParamsStr = cik8InitializeTaskParams.toString();
    ConnectorDetails gitConnectorDetails = cik8InitializeTaskParams.getCik8PodParams().getGitConnector();

    PodParams podParams = cik8InitializeTaskParams.getCik8PodParams();
    String namespace = podParams.getNamespace();
    String podName = podParams.getName();

    K8sTaskExecutionResponse result;
    CiK8sTaskResponse k8sTaskResponse = null;
    try (AutoLogContext ignore1 = new K8LogContext(podParams.getName(), null, OVERRIDE_ERROR)) {
      try {
        KubernetesConfig kubernetesConfig =
            k8sConnectorHelper.getKubernetesConfig(cik8InitializeTaskParams.getK8sConnector());
        ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);
        CoreV1Api coreV1Api = new CoreV1Api(apiClient);

        createImageSecrets(coreV1Api, namespace, (CIK8PodParams<CIK8ContainerParams>) podParams);
        createEnvVariablesSecrets(
            coreV1Api, namespace, (CIK8PodParams<CIK8ContainerParams>) podParams, gitConnectorDetails);

        if (cik8InitializeTaskParams.getServicePodParams() != null) {
          for (CIK8ServicePodParams servicePodParams : cik8InitializeTaskParams.getServicePodParams()) {
            log.info("Creating service for container: {}", servicePodParams);
            createServicePod(coreV1Api, namespace, servicePodParams);
          }
        }

        log.info("Setting up pod spec");
        V1Pod pod = podSpecBuilder.createSpec(podParams).build();
        log.info("Creating pod with spec: {}", pod);
        cik8JavaClientHandler.createOrReplacePodWithRetries(coreV1Api, pod, namespace);
        Watch<V1Event> watch =
            k8EventHandler.startAsyncPodEventWatch(kubernetesConfig, namespace, podName, logStreamingTaskClient);
        PodStatus podStatus = cik8JavaClientHandler.waitUntilPodIsReady(
            coreV1Api, podName, namespace, cik8InitializeTaskParams.getPodMaxWaitUntilReadySecs());
        if (watch != null) {
          k8EventHandler.stopEventWatch(watch);
        }

        k8sTaskResponse =
            CiK8sTaskResponse.builder().podStatus(podStatus).podName(podName).podNamespace(namespace).build();
        boolean isPodRunning = podStatus.getStatus() == RUNNING;
        if (isPodRunning) {
          result = K8sTaskExecutionResponse.builder()
                       .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                       .k8sTaskResponse(k8sTaskResponse)
                       .build();
        } else {
          result = K8sTaskExecutionResponse.builder()
                       .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                       .errorMessage(podStatus.getErrorMessage())
                       .k8sTaskResponse(k8sTaskResponse)
                       .build();
        }
      } catch (Exception ex) {
        log.error("Exception in processing CI K8 build setup task: {}", cik8BuildTaskParamsStr, ex);
        result = K8sTaskExecutionResponse.builder()
                     .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                     .errorMessage(ex.getMessage())
                     .k8sTaskResponse(k8sTaskResponse)
                     .build();
      }
    }
    log.info("CI lite-engine task took: {} for pod: {} ", timer.stop(), podParams.getName());
    return result;
  }

  private void createServicePod(CoreV1Api coreV1Api, String namespace, CIK8ServicePodParams servicePodParams)
      throws ApiException {
    V1Pod pod = podSpecBuilder.createSpec((PodParams) servicePodParams.getCik8PodParams()).build();
    log.info("Creating service pod with spec: {}", pod);

    cik8JavaClientHandler.createOrReplacePodWithRetries(coreV1Api, pod, namespace);

    cik8JavaClientHandler.createService(coreV1Api, namespace, servicePodParams.getServiceName(),
        servicePodParams.getSelectorMap(), servicePodParams.getPorts());
  }

  private void createImageSecrets(CoreV1Api coreV1Api, String namespace, CIK8PodParams<CIK8ContainerParams> podParams) {
    log.info("Creating image secrets for pod name: {}", podParams.getName());
    Stopwatch timer = Stopwatch.createStarted();
    List<CIK8ContainerParams> containerParamsList = new ArrayList<>();
    Optional.ofNullable(podParams.getContainerParamsList()).ifPresent(containerParamsList::addAll);
    Optional.ofNullable(podParams.getInitContainerParamsList()).ifPresent(containerParamsList::addAll);

    for (CIK8ContainerParams containerParams : containerParamsList) {
      String secretName = format("%s-image-%s", podParams.getName(), containerParams.getName());
      V1Secret imgSecret = cik8JavaClientHandler.createRegistrySecret(
          coreV1Api, namespace, secretName, containerParams.getImageDetailsWithConnector());
      log.info("Registry secret creation for pod name {} is complete", podParams.getName());
      if (imgSecret != null) {
        containerParams.setImageSecret(secretName);
        switch (containerParams.getContainerType()) {
          case SERVICE:
          case PLUGIN:
            updateContainerWithSecretVariable(HARNESS_IMAGE_SECRET,
                SecretParams.builder().type(SecretParams.Type.TEXT).secretKey(DOCKER_CONFIG_KEY).build(), secretName,
                containerParams);
            break;
          default:
            break;
        }
      }
    }
    log.info("Image secret creation took: {} for pod: {} ", timer.stop(), podParams.getName());
  }

  private void createEnvVariablesSecrets(CoreV1Api coreV1Api, String namespace,
      CIK8PodParams<CIK8ContainerParams> podParams, ConnectorDetails gitConnectorDetails) {
    Stopwatch timer = Stopwatch.createStarted();
    log.info("Creating env variables for pod name: {}", podParams.getName());
    List<CIK8ContainerParams> containerParamsList = podParams.getContainerParamsList();
    String k8SecretName = getSecretName(podParams.getName());

    Map<String, String> secretData = new HashMap<>();
    for (CIK8ContainerParams containerParams : containerParamsList) {
      log.info(
          "Creating env variables for container {} present on pod: {}", containerParams.getName(), podParams.getName());

      if (containerParams.getContainerSecrets() == null) {
        continue;
      }

      List<SecretVariableDetails> secretVariableDetails =
          containerParams.getContainerSecrets().getSecretVariableDetails();
      Map<String, ConnectorDetails> connectorDetailsMap =
          containerParams.getContainerSecrets().getConnectorDetailsMap();
      Map<String, ConnectorDetails> functorConnectors = containerParams.getContainerSecrets().getFunctorConnectors();
      Map<String, SecretParams> plainTextSecretsByName =
          containerParams.getContainerSecrets().getPlainTextSecretsByName();
      Map<String, String> envVarsWithSecretRef = containerParams.getEnvVarsWithSecretRef();

      if (isNotEmpty(envVarsWithSecretRef)) {
        log.info("Creating environment variables with secret functor value for container {} present on pod: {}",
            containerParams.getName(), podParams.getName());
        Map<String, String> envVarsWithSecretRefSecretData =
            getAndUpdateEnvVarsWithSecretRefSecretData(envVarsWithSecretRef, containerParams, k8SecretName);
        secretData.putAll(envVarsWithSecretRefSecretData);
      }

      if (isNotEmpty(functorConnectors)) {
        log.info("Creating git hub app token env variables for container {} present on pod: {}",
            containerParams.getName(), podParams.getName());
        Map<String, String> githubAppTokenSecretData =
            getAndUpdateGithubAppTokenSecretData(functorConnectors, containerParams, k8SecretName);
        secretData.putAll(githubAppTokenSecretData);
      }
      if (isNotEmpty(secretVariableDetails)) {
        log.info("Creating custom secret env variables for container {} present on pod: {}", containerParams.getName(),
            podParams.getName());
        Map<String, String> customVarSecretData =
            getAndUpdateCustomVariableSecretData(secretVariableDetails, containerParams, k8SecretName);
        secretData.putAll(customVarSecretData);
      }
      if (isNotEmpty(plainTextSecretsByName)) {
        Map<String, String> plainTextSecretData =
            getAndUpdateSecretParamsData(plainTextSecretsByName, containerParams, k8SecretName);
        secretData.putAll(plainTextSecretData);
      }

      if (isNotEmpty(connectorDetailsMap)) {
        log.info("Creating connector env variables for container {} present on pod: {}", containerParams.getName(),
            podParams.getName());
        switch (containerParams.getContainerType()) {
          case LITE_ENGINE:
          case PLUGIN:
            Map<String, String> connectorSecretData =
                getAndUpdateConnectorSecretData(connectorDetailsMap, containerParams, k8SecretName);
            secretData.putAll(connectorSecretData);
            break;
          default:
            break;
        }
      }

      log.info("Creating proxy env variables for container {} present on pod: {}", containerParams.getName(),
          podParams.getName());
      secretData.putAll(getAndUpdateProxyConfigurationSecretData(containerParams, k8SecretName));
      if (containerParams.getContainerType() == LITE_ENGINE) {
        log.info("Creating delegate service token for container {} present on pod: {}", containerParams.getName(),
            podParams.getName());
        secretData.putAll(getAndUpdateDelegateServiceToken(containerParams, k8SecretName));
      }
    }

    log.info("Creating git secret env variables for pod: {}", podParams.getName());
    Map<String, String> gitSecretData =
        getAndUpdateGitSecretData(gitConnectorDetails, containerParamsList, k8SecretName);
    secretData.putAll(gitSecretData);
    log.info("Determined environment secrets to create for stage for pod {}", podParams.getName());

    for (CIK8ContainerParams containerParams : containerParamsList) {
      Set<String> allSecrets = new HashSet<>();
      if (!isEmpty(containerParams.getSecretEnvVars())) {
        allSecrets.addAll(containerParams.getSecretEnvVars().keySet());
        updateContainerWithEnvVariable(allSecrets, containerParams);
      }
    }

    Map<String, byte[]> data = new HashMap<>();

    if (isNotEmpty(secretData)) {
      for (Map.Entry<String, String> entry : secretData.entrySet()) {
        data.put(entry.getKey(), EncodingUtils.decodeBase64(entry.getValue()));
      }

      log.info("Creating environment secrets for pod name: {}", podParams.getName());
      cik8JavaClientHandler.createEnvSecret(coreV1Api, namespace, k8SecretName, data);
      log.info("Environment k8 secret creation is complete for pod name: {}", podParams.getName());
    }
    log.info("Environment variable creation took: {} for pod: {} ", timer.stop(), podParams.getName());
  }

  private Map<String, String> getAndUpdateEnvVarsWithSecretRefSecretData(
      Map<String, String> envVarsWithSecretRef, CIK8ContainerParams containerParams, String k8SecretName) {
    Map<String, SecretParams> secretData =
        secretSpecBuilder.createSecretParamsForPlainTextSecret(envVarsWithSecretRef, containerParams.getName());
    if (isNotEmpty(secretData)) {
      updateContainer(containerParams, k8SecretName, secretData);
      return secretData.values().stream().collect(Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    } else {
      return Collections.emptyMap();
    }
  }

  private Map<String, String> getAndUpdateGithubAppTokenSecretData(
      Map<String, ConnectorDetails> functorConnectors, CIK8ContainerParams containerParams, String secretName) {
    Map<String, SecretParams> secretData = secretSpecBuilder.fetchGithubAppToken(functorConnectors);
    if (isNotEmpty(secretData)) {
      updateContainer(containerParams, secretName, secretData);
      return secretData.values().stream().collect(Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    } else {
      return Collections.emptyMap();
    }
  }

  private Map<String, String> getAndUpdateConnectorSecretData(
      Map<String, ConnectorDetails> pluginConnectors, CIK8ContainerParams containerParams, String secretName) {
    Map<String, SecretParams> secretData = secretSpecBuilder.decryptConnectorSecretVariables(pluginConnectors);
    if (isNotEmpty(secretData)) {
      updateContainer(containerParams, secretName, secretData);
      return secretData.values().stream().collect(Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    } else {
      return Collections.emptyMap();
    }
  }

  private Map<String, String> getAndUpdateCustomVariableSecretData(
      List<SecretVariableDetails> secretVariableDetails, CIK8ContainerParams containerParams, String secretName) {
    Map<String, SecretParams> customVarSecretData =
        secretSpecBuilder.decryptCustomSecretVariables(secretVariableDetails);
    if (isNotEmpty(customVarSecretData)) {
      updateContainer(containerParams, secretName, customVarSecretData);
      return customVarSecretData.values().stream().collect(
          Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    } else {
      return Collections.emptyMap();
    }
  }

  private Map<String, String> getAndUpdateSecretParamsData(
      Map<String, SecretParams> secretParamsByName, CIK8ContainerParams containerParams, String k8SecretName) {
    if (isNotEmpty(secretParamsByName)) {
      updateContainer(containerParams, k8SecretName, secretParamsByName);
      return secretParamsByName.values().stream().collect(
          Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    } else {
      return Collections.emptyMap();
    }
  }

  private Map<String, String> getAndUpdateProxyConfigurationSecretData(
      CIK8ContainerParams containerParams, String secretName) {
    if (proxyVariableHelper != null && proxyVariableHelper.checkIfProxyIsConfigured()) {
      Map<String, SecretParams> proxyConfiguration = proxyVariableHelper.getProxyConfiguration();
      updateContainer(containerParams, secretName, proxyConfiguration);
      return proxyConfiguration.values().stream().collect(
          Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    } else {
      return Collections.emptyMap();
    }
  }

  private Map<String, String> getAndUpdateDelegateServiceToken(CIK8ContainerParams containerParams, String secretName) {
    Map<String, SecretParams> serviceTokenSecretParams = delegateServiceTokenHelper.getServiceTokenSecretParams();
    updateContainer(containerParams, secretName, serviceTokenSecretParams);
    return serviceTokenSecretParams.values().stream().collect(
        Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
  }

  private Map<String, String> getAndUpdateGitSecretData(
      ConnectorDetails gitConnector, List<CIK8ContainerParams> containerParamsList, String secretName) {
    Map<String, SecretParams> gitSecretData = secretSpecBuilder.decryptGitSecretVariables(gitConnector);
    if (isNotEmpty(gitSecretData)) {
      for (CIK8ContainerParams containerParams : containerParamsList) {
        updateContainer(containerParams, secretName, gitSecretData);
      }

      return gitSecretData.values().stream().collect(
          Collectors.toMap(SecretParams::getSecretKey, SecretParams::getValue));
    } else {
      return Collections.emptyMap();
    }
  }

  private void updateContainer(
      CIK8ContainerParams containerParams, String secretName, Map<String, SecretParams> secretData) {
    for (Map.Entry<String, SecretParams> secretDataEntry : secretData.entrySet()) {
      switch (secretDataEntry.getValue().getType()) {
        case FILE:
        case TEXT:
          updateContainerWithSecretVariable(
              secretDataEntry.getKey(), secretDataEntry.getValue(), secretName, containerParams);
          break;
        default:
          unhandled(secretDataEntry.getValue().getType());
      }
    }
  }

  private void updateContainerWithSecretVariable(
      String variableName, SecretParams secretParam, String secretName, ContainerParams containerParams) {
    Map<String, SecretVarParams> secretEnvVars = containerParams.getSecretEnvVars();
    if (secretEnvVars == null) {
      secretEnvVars = new HashMap<>();
      containerParams.setSecretEnvVars(secretEnvVars);
    }
    secretEnvVars.put(
        variableName, SecretVarParams.builder().secretKey(secretParam.getSecretKey()).secretName(secretName).build());
  }

  private void updateContainerWithEnvVariable(Set<String> allSecrets, ContainerParams containerParams) {
    if (isEmpty(allSecrets)) {
      return;
    }

    Map<String, String> secretEnvVars = containerParams.getEnvVars();
    if (secretEnvVars == null) {
      secretEnvVars = new HashMap<>();
      containerParams.setEnvVars(secretEnvVars);
    }
    String secret = String.join(",", allSecrets);
    secretEnvVars.put(HARNESS_SECRETS_LIST, secret);
  }
}
