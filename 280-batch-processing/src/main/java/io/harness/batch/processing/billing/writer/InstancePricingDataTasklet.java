/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.writer;

import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.ccm.InstanceFamilyAndRegion;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.ccm.commons.beans.Pricing;
import io.harness.ccm.commons.beans.PricingSource;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.pricing.client.CloudInfoPricingClient;
import io.harness.pricing.dto.cloudinfo.ProductDetailResponse;
import io.harness.pricing.dto.cloudinfo.ProductDetails;
import io.harness.pricing.dto.cloudinfo.ProductDetailsResponse;
import io.harness.pricing.dto.cloudinfo.ZonePrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
public class InstancePricingDataTasklet implements Tasklet {
  @Autowired private InstanceDataDao instanceDataDao;
  @Autowired private BatchMainConfig config;
  @Autowired private CustomBillingMetaDataService customBillingMetaDataService;
  @Autowired private BigQueryHelperService bigQueryHelperService;
  @Autowired private CloudInfoPricingClient banzaiPricingClient;

  private JobParameters parameters;
  private int batchSize;
  private BatchJobType batchJobType;
  private final String COMPUTE_SERVICE = "compute";

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    parameters = chunkContext.getStepContext().getStepExecution().getJobParameters();
    batchSize = config.getBatchQueryConfig().getInstanceDataBatchSize();
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    Instant startTime = getFieldValueFromJobParams(CCMJobConstants.JOB_START_DATE);
    Instant endTime = getFieldValueFromJobParams(CCMJobConstants.JOB_END_DATE);
    batchJobType = CCMJobConstants.getBatchJobTypeFromJobParams(parameters, CCMJobConstants.BATCH_JOB_TYPE);
    List<InstanceData> instanceDataLists;
    Instant activeInstanceIterator = startTime;

    do {
      instanceDataLists =
          instanceDataDao.getInstanceDataListForPricingUpdate(accountId, batchSize, activeInstanceIterator, endTime);
      if (!instanceDataLists.isEmpty()) {
        activeInstanceIterator = instanceDataLists.get(instanceDataLists.size() - 1).getActiveInstanceIterator();
        if (instanceDataLists.get(0).getActiveInstanceIterator().equals(activeInstanceIterator)) {
          activeInstanceIterator = activeInstanceIterator.plus(1, ChronoUnit.MILLIS);
        }
      }
      Map<String, List<InstanceData>> awsInstances =
      instanceDataLists.stream().filter((InstanceData instanceData) ->
          getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER, instanceData)
              .equals(CloudProvider.AWS.name())
      ).collect(Collectors.groupingBy(InstanceData::getCloudProviderInstanceId));
      Set<String> leftOverInstances = awsInstances.keySet();
      // call BQHelperSerivce with awsInstances.keySet
      String awsDataSetId = customBillingMetaDataService.getAwsDataSetId(accountId);
      Map<String, Pricing> pricingDataByResourceId = bigQueryHelperService.getAwsPricingDataByResourceIds(
          new ArrayList<>(leftOverInstances), startTime, endTime, awsDataSetId);
      // update awsInstances
      leftOverInstances.removeAll(pricingDataByResourceId.keySet());
      Set<InstanceFamilyAndRegion> instanceFamilyAndRegions = new HashSet<>();
      for (String resourceId: leftOverInstances) {
        InstanceData instanceData = awsInstances.get(resourceId).get(0);
        instanceFamilyAndRegions.add(new InstanceFamilyAndRegion(
            instanceData.getMetaData().get(InstanceMetaDataConstants.INSTANCE_FAMILY),
            instanceData.getMetaData().get(InstanceMetaDataConstants.REGION)));
      }
      Map<InstanceFamilyAndRegion, Pricing> pricingDataByInstanceFamilyAndRegion =
          bigQueryHelperService.getAwsPricingDataByInstanceFamilyAndRegion(new ArrayList<>(instanceFamilyAndRegions),
              startTime, endTime, awsDataSetId);
      // update awsInstances
      Set<String> instanceFamilies = new HashSet<>();
      leftOverInstances.removeIf((String resourceId) -> {
        InstanceData instanceData = awsInstances.get(resourceId).get(0);
        String instanceFamily = instanceData.getMetaData().get(InstanceMetaDataConstants.INSTANCE_FAMILY);
        String region = instanceData.getMetaData().get(InstanceMetaDataConstants.REGION);
        if (pricingDataByInstanceFamilyAndRegion.containsKey(new InstanceFamilyAndRegion(instanceFamily, region))) {
          return true;
        } else {
          instanceFamilies.add(instanceFamily);
          return false;
        }
      });
      Map<String, Pricing> pricingDataByInstanceFamily = bigQueryHelperService.getAwsPricingDataByInstanceFamily(
          new ArrayList<>(instanceFamilies), startTime, endTime, awsDataSetId);
      // update awsInstances
      leftOverInstances.removeIf((String resourceId) -> pricingDataByInstanceFamily.containsKey(
          awsInstances.get(resourceId).get(0).getMetaData().get(InstanceMetaDataConstants.INSTANCE_FAMILY)));
      // get pricing from public api
      for (String resourceId: leftOverInstances) {
        try {
          InstanceData instanceData = awsInstances.get(resourceId).get(0);
          Call<ProductDetailResponse> pricingInfoCall = banzaiPricingClient.getPricingInfo(
              CloudProvider.AWS.getCloudProviderName(),
              instanceData.getMetaData().get(InstanceMetaDataConstants.CLUSTER_TYPE).equals(ClusterType.K8S.name()) ?
                  CloudProvider.AWS.getK8sService() : COMPUTE_SERVICE,
              instanceData.getMetaData().get(InstanceMetaDataConstants.REGION),
              instanceData.getMetaData().get(InstanceMetaDataConstants.INSTANCE_FAMILY));
          double pricePerHour = 0;
          Response<ProductDetailResponse> pricingInfo = pricingInfoCall.execute();
          if (null != pricingInfo.body() && null != pricingInfo.body().getProduct()) {
            ProductDetails product = pricingInfo.body().getProduct();
            pricePerHour = product.getOnDemandPrice();
            if (InstanceCategory.valueOf(instanceData.getMetaData().get(InstanceMetaDataConstants.INSTANCE_CATEGORY))
                .equals(InstanceCategory.SPOT)) {
              pricePerHour = product.getSpotPrice()
                  .stream()
                  .filter(zonePrice -> zonePrice.getZone().equals(instanceData.getMetaData().get(InstanceMetaDataConstants.ZONE)))
                  .findFirst()
                  .map(ZonePrice::getPrice)
                  .orElse(pricePerHour);
            }
          } else {
            log.info("Null response from banzai service");
          }

          instanceData.setPricing(new Pricing(new BigDecimal(pricePerHour), PricingSource.PUBLIC_API));

        } catch (IOException e) {
          log.error("Exception in pricing service ", e);
        }
      }
      // update awsInstances

      Map<String, List<InstanceData>> azureInstances =
          instanceDataLists.stream().filter((InstanceData instanceData) ->
              getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLOUD_PROVIDER, instanceData)
                  .equals(CloudProvider.AZURE.name())
          ).collect(Collectors.groupingBy(InstanceData::getCloudProviderInstanceId));

    } while (instanceDataLists.size() == batchSize);
    return null;
  }

  private Instant getFieldValueFromJobParams(String fieldName) {
    return Instant.ofEpochMilli(Long.parseLong(parameters.getString(fieldName)));
  }
}
