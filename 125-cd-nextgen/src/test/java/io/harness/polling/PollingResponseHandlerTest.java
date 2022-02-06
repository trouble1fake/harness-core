/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling;

import static io.harness.delegate.task.artifacts.ArtifactSourceType.ARTIFACTORY_REGISTRY;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.DOCKER_REGISTRY;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.NEXUS_REGISTRY;
import static io.harness.polling.contracts.Type.ARTIFACTORY;
import static io.harness.polling.contracts.Type.DOCKER_HUB;
import static io.harness.polling.contracts.Type.ECR;
import static io.harness.polling.contracts.Type.GCR;
import static io.harness.polling.contracts.Type.GCS_HELM;
import static io.harness.polling.contracts.Type.HTTP_HELM;
import static io.harness.polling.contracts.Type.NEXUS;
import static io.harness.polling.contracts.Type.S3_HELM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.delegate.beans.polling.ArtifactPollingDelegateResponse;
import io.harness.delegate.beans.polling.ManifestPollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.delegate.beans.polling.PollingResponseInfc;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.polling.bean.PolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.PollingType;
import io.harness.polling.bean.artifact.ArtifactPolledResponse;
import io.harness.polling.bean.artifact.ArtifactoryRegistryArtifactInfo;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.polling.bean.artifact.EcrArtifactInfo;
import io.harness.polling.bean.artifact.GcrArtifactInfo;
import io.harness.polling.bean.artifact.NexusRegistryArtifactInfo;
import io.harness.polling.bean.manifest.HelmChartManifestInfo;
import io.harness.polling.bean.manifest.ManifestPolledResponse;
import io.harness.polling.contracts.PollingResponse;
import io.harness.polling.contracts.Type;
import io.harness.polling.service.PolledItemPublisher;
import io.harness.polling.service.intfc.PollingPerpetualTaskService;
import io.harness.polling.service.intfc.PollingService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class PollingResponseHandlerTest extends CategoryTest {
  private final String PERPETUAL_TASK_ID = "perpetualTaskId";
  private final String POLLING_DOC_ID = "pollingDocId";
  private final String ACCOUNT_ID = "accountId";
  private final String SIGNATURE_1 = "signature1";

  @InjectMocks private PollingResponseHandler pollingResponseHandler;
  @Mock PollingPerpetualTaskService pollingPerpetualTaskService;
  @Mock PollingService pollingService;
  @Mock PolledItemPublisher polledItemPublisher;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTaskOnDifferentPerpetualTaskId() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    when(pollingService.get(anyString(), anyString())).thenReturn(PollingDocument.builder().build());
    pollingResponseHandler.handlePollingResponse("ptTask", ACCOUNT_ID, delegateResponse);
    verify(pollingPerpetualTaskService).deletePerpetualTask("ptTask", ACCOUNT_ID);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void shouldDeletePollingDocumentOnEmptySignatureList() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    PollingDocument pollingDocument =
        PollingDocument.builder().uuid(POLLING_DOC_ID).perpetualTaskId(PERPETUAL_TASK_ID).build();
    when(pollingService.get(anyString(), anyString())).thenReturn(pollingDocument);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    verify(pollingService).delete(pollingDocument);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testFailureResponse() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    PollingDocument pollingDocument = getHttpHelmPollingDocument(null);
    when(pollingService.get(anyString(), anyString())).thenReturn(pollingDocument);

    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);

    verify(pollingService).get(ACCOUNT_ID, POLLING_DOC_ID);
    verify(pollingService).updateFailedAttempts(ACCOUNT_ID, POLLING_DOC_ID, 1);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void shouldDeletePerpetualTaskOnHighFailedAttempts() {
    PollingDelegateResponse delegateResponse = getFailedPollingDelegateResponse();
    PollingDocument pollingDocument = getHttpHelmPollingDocument(null);

    int failedAttempts = 3400;
    for (int i = 0; i < 100; i++) {
      pollingDocument.setFailedAttempts(failedAttempts + i);
      when(pollingService.get(anyString(), anyString())).thenReturn(pollingDocument);
      pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    }
    verify(pollingService, times(100)).get(ACCOUNT_ID, POLLING_DOC_ID);
    verify(pollingService, times(100)).updateFailedAttempts(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), anyInt());
    verify(pollingPerpetualTaskService, times(3)).resetPerpetualTask(any());
    verify(pollingPerpetualTaskService).deletePerpetualTask(PERPETUAL_TASK_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessHttpHelmPollingResponseWithDelegateRebalance() {
    testSuccessResponse(HTTP_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessS3HelmPollingResponseWithDelegateRebalance() {
    testSuccessResponse(S3_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessGcsHelmPollingResponseWithDelegateRebalance() {
    testSuccessResponse(GCS_HELM, PollingType.MANIFEST);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessDockerHubPollingResponseWithDelegateRebalance() {
    testSuccessResponse(DOCKER_HUB, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessGcrPollingResponseWithDelegateRebalance() {
    testSuccessResponse(GCR, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.INDER)
  @Category(UnitTests.class)
  public void testSuccessEcrPollingResponseWithDelegateRebalance() {
    testSuccessResponse(ECR, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessNexusRegistryPollingResponseWithDelegateRebalance() {
    testSuccessResponse(NEXUS, PollingType.ARTIFACT);
  }

  @Test
  @Owner(developers = OwnerRule.MLUKIC)
  @Category(UnitTests.class)
  public void testSuccessArtifactoryRegistryPollingResponseWithDelegateRebalance() {
    testSuccessResponse(ARTIFACTORY, PollingType.ARTIFACT);
  }

  private void testSuccessResponse(Type type, PollingType pollingType) {
    PollingDocument pollingDocument = getPollingDocumentFromType(type, null);
    PollingDelegateResponse delegateResponse = getPollingDelegateResponse(type, pollingType, true, 0, 1000, -1);

    // first collection. unpublished keys - [0-1000], tobeDeletedKeys = []
    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(pollingDocument);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, delegateResponse);
    PolledResponse polledResponse = pollingType.equals(PollingType.MANIFEST)
        ? validateFirstManifestCollectionOnManager()
        : validateFirstArtifactCollectionOnManager();

    PollingDocument savedPollingDocument = getPollingDocumentFromType(type, polledResponse);
    PollingDelegateResponse newDelegateResponse = getPollingDelegateResponse(type, pollingType, false, 1001, 1005, -1);

    // response with unpublished keys [1001-1005], tobeDeletedKeys = []
    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(savedPollingDocument);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, newDelegateResponse);
    verify(pollingService, times(2)).get(ACCOUNT_ID, POLLING_DOC_ID);

    PolledResponse newPolledResponse;
    if (pollingType.equals(PollingType.MANIFEST)) {
      newPolledResponse = assertAndGetManifestPolledResponse(2, 1006);
    } else {
      newPolledResponse = assertAndGetArtifactPolledResponse(2, 1006);
    }
    assertPublishedItem(type, 5, 1, pollingType);

    PollingDocument savedPollingDocument1 = getPollingDocumentFromType(type, newPolledResponse);
    PollingDelegateResponse newDelegateResponse1 = getPollingDelegateResponse(type, pollingType, true, 3, 1011, 2);

    // Delegate rebalanced. response with unpublished keys [3-1011], tobeDeletedKeys = []
    when(pollingService.get(ACCOUNT_ID, POLLING_DOC_ID)).thenReturn(savedPollingDocument1);
    pollingResponseHandler.handlePollingResponse(PERPETUAL_TASK_ID, ACCOUNT_ID, newDelegateResponse1);
    verify(pollingService, times(3)).get(ACCOUNT_ID, POLLING_DOC_ID);

    if (pollingType.equals(PollingType.MANIFEST)) {
      assertAndGetManifestPolledResponse(3, 1009);
    } else {
      assertAndGetArtifactPolledResponse(3, 1009);
    }
    assertPublishedItem(type, 6, 2, pollingType);
  }

  private ArtifactPolledResponse assertAndGetArtifactPolledResponse(int nofOfTimes, int expectedSize) {
    ArgumentCaptor<ArtifactPolledResponse> captor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService, times(nofOfTimes))
        .updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ArtifactPolledResponse newPolledResponse = captor.getValue();
    assertThat(newPolledResponse).isNotNull();
    assertThat(newPolledResponse.getAllPolledKeys()).hasSize(expectedSize);
    return newPolledResponse;
  }

  private ManifestPolledResponse assertAndGetManifestPolledResponse(int nofOfTimes, int expectedSize) {
    ArgumentCaptor<ManifestPolledResponse> captor = ArgumentCaptor.forClass(ManifestPolledResponse.class);
    verify(pollingService, times(nofOfTimes))
        .updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ManifestPolledResponse newPolledResponse = captor.getValue();
    assertThat(newPolledResponse).isNotNull();
    assertThat(newPolledResponse.getAllPolledKeys()).hasSize(expectedSize);
    return newPolledResponse;
  }

  private void assertPublishedItem(Type type, int publishedSize, int noOfTimePublished, PollingType pollingType) {
    ArgumentCaptor<PollingResponse> pollingResponseArgumentCaptor1 = ArgumentCaptor.forClass(PollingResponse.class);
    verify(polledItemPublisher, times(noOfTimePublished)).publishPolledItems(pollingResponseArgumentCaptor1.capture());
    PollingResponse pollingResponse = pollingResponseArgumentCaptor1.getValue();
    assertThat(pollingResponse).isNotNull();
    assertThat(pollingResponse.getType()).isEqualTo(type);
    assertThat(pollingResponse.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(pollingResponse.getSignaturesCount()).isEqualTo(1);
    assertThat(pollingResponse.getSignatures(0)).isEqualTo(SIGNATURE_1);
    assertThat(pollingResponse.getBuildInfo()).isNotNull();
    assertThat(pollingResponse.getBuildInfo().getName())
        .isEqualTo(pollingType == PollingType.MANIFEST ? "chartName" : "imagePath");
    assertThat(pollingResponse.getBuildInfo().getVersionsCount()).isEqualTo(publishedSize);
  }

  private PolledResponse validateFirstManifestCollectionOnManager() {
    verify(pollingService, times(1)).get(ACCOUNT_ID, POLLING_DOC_ID);

    ArgumentCaptor<ManifestPolledResponse> captor = ArgumentCaptor.forClass(ManifestPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ManifestPolledResponse polledResponse = captor.getValue();
    assertThat(polledResponse).isNotNull();
    assertThat(polledResponse.getAllPolledKeys()).hasSize(1001);
    verify(polledItemPublisher, never()).publishPolledItems(any());
    return polledResponse;
  }

  private PolledResponse validateFirstArtifactCollectionOnManager() {
    verify(pollingService, times(1)).get(ACCOUNT_ID, POLLING_DOC_ID);

    ArgumentCaptor<ArtifactPolledResponse> captor = ArgumentCaptor.forClass(ArtifactPolledResponse.class);
    verify(pollingService).updatePolledResponse(eq(ACCOUNT_ID), eq(POLLING_DOC_ID), captor.capture());
    ArtifactPolledResponse polledResponse = captor.getValue();
    assertThat(polledResponse).isNotNull();
    assertThat(polledResponse.getAllPolledKeys()).hasSize(1001);
    verify(polledItemPublisher, never()).publishPolledItems(any());
    return polledResponse;
  }

  private PollingDelegateResponse getPollingDelegateResponse(Type type, PollingType pollingType,
      boolean firstCollectionOnDelegate, int startIndexUnpublishedManifests, int endIndexUnpublishedManifest,
      int endIndexToBeDeleted) {
    if (pollingType.equals(PollingType.MANIFEST)) {
      return getManifestPollingDelegateResponse(
          firstCollectionOnDelegate, startIndexUnpublishedManifests, endIndexUnpublishedManifest, endIndexToBeDeleted);
    }

    return getArtifactPollingDelegateResponse(type, firstCollectionOnDelegate, startIndexUnpublishedManifests,
        endIndexUnpublishedManifest, endIndexToBeDeleted);
  }

  private PollingDelegateResponse getManifestPollingDelegateResponse(boolean firstCollectionOnDelegate,
      int startIndexUnpublishedManifests, int endIndexUnpublishedManifest, int endIndexToBeDeleted) {
    List<String> unpublishedManifests =
        IntStream.rangeClosed(startIndexUnpublishedManifests, endIndexUnpublishedManifest)
            .boxed()
            .map(String::valueOf)
            .collect(Collectors.toList());
    Set<String> toBeDeletedKeys =
        IntStream.rangeClosed(0, endIndexToBeDeleted).boxed().map(String::valueOf).collect(Collectors.toSet());
    ManifestPollingDelegateResponse manifestPollingDelegateResponse =
        ManifestPollingDelegateResponse.builder()
            .firstCollectionOnDelegate(firstCollectionOnDelegate)
            .unpublishedManifests(unpublishedManifests)
            .toBeDeletedKeys(toBeDeletedKeys)
            .build();
    return getPollingDelegateResponse(manifestPollingDelegateResponse);
  }

  private PollingDelegateResponse getArtifactPollingDelegateResponse(Type type, boolean firstCollectionOnDelegate,
      int startIndexUnpublished, int endIndexUnpublished, int endIndexToBeDeleted) {
    Set<String> toBeDeletedKeys =
        IntStream.rangeClosed(0, endIndexToBeDeleted).boxed().map(String::valueOf).collect(Collectors.toSet());
    List<ArtifactDelegateResponse> artifactDelegateResponses;
    switch (type) {
      case DOCKER_HUB:
        artifactDelegateResponses = getDockerArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case GCR:
        artifactDelegateResponses = getGcrArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case NEXUS:
        artifactDelegateResponses = getNexusArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      case ARTIFACTORY:
        artifactDelegateResponses =
            getArtifactoryArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
        break;
      default:
        artifactDelegateResponses = getEcrArtifactDelegateResponseList(startIndexUnpublished, endIndexUnpublished);
    }
    ArtifactPollingDelegateResponse artifactPollingDelegateResponse =
        ArtifactPollingDelegateResponse.builder()
            .firstCollectionOnDelegate(firstCollectionOnDelegate)
            .toBeDeletedKeys(toBeDeletedKeys)
            .unpublishedArtifacts(artifactDelegateResponses)
            .build();
    return getPollingDelegateResponse(artifactPollingDelegateResponse);
  }

  private List<ArtifactDelegateResponse> getDockerArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i -> DockerArtifactDelegateResponse.builder().sourceType(DOCKER_REGISTRY).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getGcrArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> GcrArtifactDelegateResponse.builder().sourceType(ArtifactSourceType.GCR).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getEcrArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> EcrArtifactDelegateResponse.builder().sourceType(ArtifactSourceType.ECR).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getNexusArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i -> NexusArtifactDelegateResponse.builder().sourceType(NEXUS_REGISTRY).tag(String.valueOf(i)).build())
        .collect(Collectors.toList());
  }

  private List<ArtifactDelegateResponse> getArtifactoryArtifactDelegateResponseList(int startIndex, int endIndex) {
    return IntStream.rangeClosed(startIndex, endIndex)
        .boxed()
        .map(i
            -> ArtifactoryArtifactDelegateResponse.builder()
                   .sourceType(ARTIFACTORY_REGISTRY)
                   .tag(String.valueOf(i))
                   .build())
        .collect(Collectors.toList());
  }

  private PollingDelegateResponse getPollingDelegateResponse(PollingResponseInfc pollingResponseInfc) {
    return PollingDelegateResponse.builder()
        .pollingDocId(POLLING_DOC_ID)
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .accountId(ACCOUNT_ID)
        .pollingResponseInfc(pollingResponseInfc)
        .build();
  }

  private PollingDelegateResponse getFailedPollingDelegateResponse() {
    return PollingDelegateResponse.builder()
        .pollingDocId(POLLING_DOC_ID)
        .accountId(ACCOUNT_ID)
        .commandExecutionStatus(CommandExecutionStatus.FAILURE)
        .errorMessage("Incorrect Credentials")
        .build();
  }

  private PollingDocument getPollingDocumentFromType(Type type, PolledResponse polledResponse) {
    switch (type) {
      case HTTP_HELM:
        return getHttpHelmPollingDocument(polledResponse);
      case S3_HELM:
        return getS3HelmPollingDocument(polledResponse);
      case GCS_HELM:
        return getGcsHelmPollingDocument(polledResponse);
      case DOCKER_HUB:
        return getDockerHubPollingDocument(polledResponse);
      case GCR:
        return getGcrPollingDocument(polledResponse);
      case NEXUS:
        return getNexusRegistryPollingDocument(polledResponse);
      case ARTIFACTORY:
        return getArtifactoryRegistryPollingDocument(polledResponse);
      default:
        return getEcrPollingDocument(polledResponse);
    }
  }

  private PollingDocument getHttpHelmPollingDocument(PolledResponse polledResponse) {
    HelmChartManifestInfo helmChartManifestInfo = HelmChartManifestInfo.builder()
                                                      .storeType(StoreConfigType.HTTP)
                                                      .helmVersion(HelmVersion.V3)
                                                      .chartName("chartName")
                                                      .store(HttpStoreConfig.builder().build())
                                                      .build();
    return getPollingDocument(polledResponse, helmChartManifestInfo, PollingType.MANIFEST);
  }

  private PollingDocument getS3HelmPollingDocument(PolledResponse polledResponse) {
    HelmChartManifestInfo helmChartManifestInfo = HelmChartManifestInfo.builder()
                                                      .storeType(StoreConfigType.S3)
                                                      .helmVersion(HelmVersion.V3)
                                                      .chartName("chartName")
                                                      .store(S3StoreConfig.builder().build())
                                                      .build();
    return getPollingDocument(polledResponse, helmChartManifestInfo, PollingType.MANIFEST);
  }

  private PollingDocument getGcsHelmPollingDocument(PolledResponse polledResponse) {
    HelmChartManifestInfo helmChartManifestInfo = HelmChartManifestInfo.builder()
                                                      .storeType(StoreConfigType.GCS)
                                                      .helmVersion(HelmVersion.V3)
                                                      .chartName("chartName")
                                                      .store(GcsStoreConfig.builder().build())
                                                      .build();
    return getPollingDocument(polledResponse, helmChartManifestInfo, PollingType.MANIFEST);
  }

  private PollingDocument getDockerHubPollingDocument(PolledResponse polledResponse) {
    DockerHubArtifactInfo dockerHubArtifactInfo = DockerHubArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, dockerHubArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getGcrPollingDocument(PolledResponse polledResponse) {
    GcrArtifactInfo gcrArtifactInfo = GcrArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, gcrArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getEcrPollingDocument(PolledResponse polledResponse) {
    EcrArtifactInfo ecrArtifactInfo = EcrArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, ecrArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getNexusRegistryPollingDocument(PolledResponse polledResponse) {
    NexusRegistryArtifactInfo nexusRegistryArtifactInfo =
        NexusRegistryArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, nexusRegistryArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getArtifactoryRegistryPollingDocument(PolledResponse polledResponse) {
    ArtifactoryRegistryArtifactInfo artifactoryRegistryArtifactInfo =
        ArtifactoryRegistryArtifactInfo.builder().imagePath("imagePath").build();
    return getPollingDocument(polledResponse, artifactoryRegistryArtifactInfo, PollingType.ARTIFACT);
  }

  private PollingDocument getPollingDocument(
      PolledResponse polledResponse, PollingInfo pollingInfo, PollingType pollingType) {
    return PollingDocument.builder()
        .uuid(POLLING_DOC_ID)
        .accountId(ACCOUNT_ID)
        .perpetualTaskId(PERPETUAL_TASK_ID)
        .signatures(Collections.singletonList(SIGNATURE_1))
        .pollingType(pollingType)
        .pollingInfo(pollingInfo)
        .polledResponse(polledResponse)
        .build();
  }
}
