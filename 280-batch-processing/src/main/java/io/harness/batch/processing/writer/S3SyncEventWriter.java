/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.RestCallToNGManagerClientUtils.execute;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AWS;

import io.harness.batch.processing.BatchProcessingException;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.S3SyncRecord;
import io.harness.batch.processing.service.impl.AwsS3SyncServiceImpl;
import io.harness.ccm.commons.dao.AWSConnectorToBucketMappingDao;
import io.harness.ccm.commons.entities.AWSConnectorToBucketMapping;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.AwsCurAttributesDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.ff.FeatureFlagService;
import io.harness.filter.FilterType;
import io.harness.ng.beans.PageResponse;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.AwsS3BucketDetails;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class S3SyncEventWriter extends EventWriter implements ItemWriter<SettingAttribute> {
  @Autowired private AwsS3SyncServiceImpl awsS3SyncService;
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private AWSConnectorToBucketMappingDao awsConnectorToBucketMappingDao;
  private JobParameters parameters;
  private static final String MASTER = "MASTER";

  @BeforeStep
  public void beforeStep(final StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
  }

  @Override
  public void write(List<? extends SettingAttribute> dummySettingAttributeList) {
    String accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    boolean areAllSyncSuccessful = true;
    areAllSyncSuccessful = syncCurrentGenAwsContainers(accountId);
    boolean isSuccessfulSync = syncNextGenContainers(accountId);
    areAllSyncSuccessful = areAllSyncSuccessful && isSuccessfulSync;
    if (!areAllSyncSuccessful) {
      throw new BatchProcessingException("AWS S3 sync failed", null);
    }
  }

  public boolean syncCurrentGenAwsContainers(String accountId) {
    List<ConnectorResponseDTO> currentGenConnectorResponses = new ArrayList<>();

    List<SettingAttribute> ceConnectorsList =
        cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AWS);

    log.info("Processing batch size of {} in S3SyncEventWriter", ceConnectorsList.size());

    ceConnectorsList.forEach(settingAttribute -> {
      if (settingAttribute.getValue() instanceof CEAwsConfig
          && (((CEAwsConfig) settingAttribute.getValue()).getAwsAccountType()).equals(MASTER)
          && ((CEAwsConfig) settingAttribute.getValue()).getAwsCrossAccountAttributes() != null) {
        CEAwsConfig ceAwsConfig = (CEAwsConfig) settingAttribute.getValue();
        AwsS3BucketDetails s3BucketDetails = ceAwsConfig.getS3BucketDetails();
        AwsCrossAccountAttributes awsCrossAccountAttributes =
            ((CEAwsConfig) settingAttribute.getValue()).getAwsCrossAccountAttributes();
        AwsCurAttributesDTO awsCurAttributesBuilder = AwsCurAttributesDTO.builder()
                                                          .s3Prefix(s3BucketDetails.getS3Prefix())
                                                          .region(s3BucketDetails.getRegion())
                                                          .reportName(ceAwsConfig.getCurReportName())
                                                          .s3BucketName(s3BucketDetails.getS3BucketName())
                                                          .build();
        CrossAccountAccessDTO crossAccountAccessBuilder =
            CrossAccountAccessDTO.builder()
                .crossAccountRoleArn(awsCrossAccountAttributes.getCrossAccountRoleArn())
                .externalId(awsCrossAccountAttributes.getExternalId())
                .build();
        ConnectorConfigDTO connectorConfig = CEAwsConnectorDTO.builder()
                                                 .awsAccountId(ceAwsConfig.getAwsMasterAccountId())
                                                 .crossAccountAccess(crossAccountAccessBuilder)
                                                 .curAttributes(awsCurAttributesBuilder)
                                                 .featuresEnabled(Arrays.asList(CEFeatures.BILLING))
                                                 .build();
        ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                                .connectorConfig(connectorConfig)
                                                .connectorType(ConnectorType.CE_AWS)
                                                .identifier(settingAttribute.getUuid())
                                                .name(settingAttribute.getName())
                                                .build();
        ConnectorResponseDTO connectorResponse = ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
        currentGenConnectorResponses.add(connectorResponse);
      }
    });
    return syncAwsContainers(currentGenConnectorResponses, accountId, false);
  }

  public boolean syncNextGenContainers(String accountId) {
    List<ConnectorResponseDTO> nextGenConnectorResponses = new ArrayList<>();
    PageResponse<ConnectorResponseDTO> response = null;
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .types(Arrays.asList(ConnectorType.CE_AWS))
            .ccmConnectorFilter(CcmConnectorFilter.builder().featuresEnabled(Arrays.asList(CEFeatures.BILLING)).build())
            .build();
    connectorFilterPropertiesDTO.setFilterType(FilterType.CONNECTOR);
    int page = 0;
    int size = 100;
    do {
      response = getConnectors(accountId, page, size, connectorFilterPropertiesDTO);
      if (response != null && isNotEmpty(response.getContent())) {
        nextGenConnectorResponses.addAll(response.getContent());
      }
      page++;
    } while (response != null && isNotEmpty(response.getContent()));
    log.info("Processing batch size of {} in S3SyncEventWriter (From NG)", nextGenConnectorResponses.size());
    return syncAwsContainers(nextGenConnectorResponses, accountId, true);
  }

  PageResponse getConnectors(
      String accountId, int page, int size, ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO) {
    return execute(
        connectorResourceClient.listConnectors(accountId, null, null, page, size, connectorFilterPropertiesDTO, false));
  }

  public boolean syncAwsContainers(List<ConnectorResponseDTO> connectorResponses, String accountId, boolean isNextGen) {
    boolean areAllSyncSuccessful = true;
    for (ConnectorResponseDTO connector : connectorResponses) {
      ConnectorInfoDTO connectorInfo = connector.getConnector();
      CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
      if (ceAwsConnectorDTO != null && ceAwsConnectorDTO.getCrossAccountAccess() != null) {
        String destinationBucket = null;
        if (isNextGen) {
          AWSConnectorToBucketMapping awsConnectorToBucketMapping =
              awsConnectorToBucketMappingDao.getByAwsConnectorId(accountId, connectorInfo.getIdentifier());
          if (awsConnectorToBucketMapping != null) {
            destinationBucket = awsConnectorToBucketMapping.getDestinationBucket();
          }
        }
        AwsCurAttributesDTO curAttributes = ceAwsConnectorDTO.getCurAttributes();
        CrossAccountAccessDTO crossAccountAccess = ceAwsConnectorDTO.getCrossAccountAccess();
        S3SyncRecord s3SyncRecord = S3SyncRecord.builder()
                                        .accountId(accountId)
                                        .settingId(connectorInfo.getIdentifier())
                                        .billingAccountId(ceAwsConnectorDTO.getAwsAccountId())
                                        .curReportName(curAttributes.getReportName())
                                        .billingBucketPath(String.join("/", "s3://" + curAttributes.getS3BucketName(),
                                            curAttributes.getS3Prefix().equals("/") ? "" : curAttributes.getS3Prefix(),
                                            curAttributes.getReportName()))
                                        .billingBucketRegion(curAttributes.getRegion())
                                        .externalId(crossAccountAccess.getExternalId())
                                        .roleArn(crossAccountAccess.getCrossAccountRoleArn())
                                        .destinationBucket(destinationBucket)
                                        .build();
        boolean isSuccessfulSync = awsS3SyncService.syncBuckets(s3SyncRecord);
        areAllSyncSuccessful = areAllSyncSuccessful && isSuccessfulSync;
      }
    }
    log.info("syncAwsContainers  areAllSyncSuccessful: {}, isNextGen: {}", areAllSyncSuccessful, isNextGen);
    return areAllSyncSuccessful;
  }
}
