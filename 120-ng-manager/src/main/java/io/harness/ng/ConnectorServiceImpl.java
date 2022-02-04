/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.NGConstants.CONNECTOR_HEARTBEAT_LOG_PREFIX;
import static io.harness.NGConstants.CONNECTOR_STRING;
import static io.harness.connector.ConnectivityStatus.FAILURE;
import static io.harness.connector.ConnectivityStatus.SUCCESS;
import static io.harness.connector.ConnectorCategory.SECRET_MANAGER;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.errorhandling.NGErrorHelper.DEFAULT_ERROR_SUMMARY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_IDENTIFIER_METRICS_KEY;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ng.NextGenModule.SECRET_MANAGER_CONNECTOR_SERVICE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.EntityReference;
import io.harness.connector.ConnectorActivityDetails;
import io.harness.connector.ConnectorCatalogueResponseDTO;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorConnectivityDetails.ConnectorConnectivityDetailsBuilder;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorRegistryFactory;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.entities.Connector;
import io.harness.connector.helper.ConnectorLogContext;
import io.harness.connector.helper.HarnessManagedConnectorHelper;
import io.harness.connector.impl.ConnectorErrorMessagesHelper;
import io.harness.connector.services.ConnectorActivityService;
import io.harness.connector.services.ConnectorHeartbeatService;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.stats.ConnectorStatistics;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.repositories.ConnectorRepository;
import io.harness.utils.FullyQualifiedIdentifierHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.DX)
public class ConnectorServiceImpl implements ConnectorService {
  private final ConnectorService defaultConnectorService;
  private final ConnectorService secretManagerConnectorService;
  private final ConnectorActivityService connectorActivityService;
  private final ConnectorHeartbeatService connectorHeartbeatService;
  private final ConnectorRepository connectorRepository;
  private final Producer eventProducer;
  private final ExecutorService executorService;
  private final ConnectorErrorMessagesHelper connectorErrorMessagesHelper;
  private final HarnessManagedConnectorHelper harnessManagedConnectorHelper;
  private final NGErrorHelper ngErrorHelper;
  private final GitSyncSdkService gitSyncSdkService;

  @Inject
  public ConnectorServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService defaultConnectorService,
      @Named(SECRET_MANAGER_CONNECTOR_SERVICE) ConnectorService secretManagerConnectorService,
      ConnectorActivityService connectorActivityService, ConnectorHeartbeatService connectorHeartbeatService,
      ConnectorRepository connectorRepository, @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer,
      ExecutorService executorService, ConnectorErrorMessagesHelper connectorErrorMessagesHelper,
      HarnessManagedConnectorHelper harnessManagedConnectorHelper, NGErrorHelper ngErrorHelper,
      GitSyncSdkService gitSyncSdkService) {
    this.defaultConnectorService = defaultConnectorService;
    this.secretManagerConnectorService = secretManagerConnectorService;
    this.connectorActivityService = connectorActivityService;
    this.connectorHeartbeatService = connectorHeartbeatService;
    this.connectorRepository = connectorRepository;
    this.eventProducer = eventProducer;
    this.executorService = executorService;
    this.connectorErrorMessagesHelper = connectorErrorMessagesHelper;
    this.harnessManagedConnectorHelper = harnessManagedConnectorHelper;
    this.ngErrorHelper = ngErrorHelper;
    this.gitSyncSdkService = gitSyncSdkService;
  }

  private ConnectorService getConnectorService(ConnectorType connectorType) {
    if (ConnectorRegistryFactory.getConnectorCategory(connectorType).equals(SECRET_MANAGER)) {
      return secretManagerConnectorService;
    }
    return defaultConnectorService;
  }

  @Override
  public Optional<ConnectorResponseDTO> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    return defaultConnectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
  }

  @Override
  public ConnectorResponseDTO create(@NotNull ConnectorDTO connector, String accountIdentifier) {
    return createInternal(connector, accountIdentifier, ChangeType.ADD);
  }

  @Override
  public ConnectorResponseDTO create(ConnectorDTO connector, String accountIdentifier, ChangeType gitChangeType) {
    return createInternal(connector, accountIdentifier, gitChangeType);
  }

  private ConnectorResponseDTO createInternal(
      ConnectorDTO connector, String accountIdentifier, ChangeType gitChangeType) {
    PerpetualTaskId connectorHeartbeatTaskId = null;
    try (AutoLogContext ignore1 = new NgAutoLogContext(connector.getConnectorInfo().getProjectIdentifier(),
             connector.getConnectorInfo().getOrgIdentifier(), accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 =
             new ConnectorLogContext(connector.getConnectorInfo().getIdentifier(), OVERRIDE_ERROR)) {
      ConnectorInfoDTO connectorInfo = connector.getConnectorInfo();
      connectorInfo.getConnectorConfig().validate();
      final boolean executeOnDelegate = defaultConnectorService.checkConnectorExecutableOnDelegate(connectorInfo);
      boolean isHarnessManagedSecretManager =
          harnessManagedConnectorHelper.isHarnessManagedSecretManager(connectorInfo);
      boolean isDefaultBranchConnector = gitSyncSdkService.isDefaultBranch(accountIdentifier,
          connector.getConnectorInfo().getOrgIdentifier(), connector.getConnectorInfo().getProjectIdentifier());
      if (!isHarnessManagedSecretManager && isDefaultBranchConnector && executeOnDelegate) {
        connectorHeartbeatTaskId = connectorHeartbeatService.createConnectorHeatbeatTask(accountIdentifier,
            connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier());
      }
      if (connectorHeartbeatTaskId != null || isHarnessManagedSecretManager || !isDefaultBranchConnector
          || !executeOnDelegate) {
        ConnectorResponseDTO connectorResponse;
        if (GitContextHelper.isUpdateToNewBranch()) {
          connectorResponse = getConnectorService(connectorInfo.getConnectorType())
                                  .create(connector, accountIdentifier, ChangeType.MODIFY);
        } else {
          connectorResponse =
              getConnectorService(connectorInfo.getConnectorType()).create(connector, accountIdentifier);
        }
        if (connectorResponse != null && isDefaultBranchConnector) {
          ConnectorInfoDTO savedConnector = connectorResponse.getConnector();
          createConnectorCreationActivity(accountIdentifier, savedConnector);
          publishEvent(accountIdentifier, savedConnector.getOrgIdentifier(), savedConnector.getProjectIdentifier(),
              savedConnector.getIdentifier(), savedConnector.getConnectorType(),
              EventsFrameworkMetadataConstants.CREATE_ACTION);
          runTestConnectionAsync(connector, accountIdentifier);
          if (connectorHeartbeatTaskId != null) {
            defaultConnectorService.updateConnectorEntityWithPerpetualtaskId(accountIdentifier,
                connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier(),
                connectorHeartbeatTaskId.getId());
          }
        }
        return connectorResponse;
      } else {
        throw new InvalidRequestException("Connector could not be created because we could not create the heartbeat");
      }
    } catch (Exception ex) {
      if (connectorHeartbeatTaskId != null) {
        ConnectorInfoDTO connectorInfo = connector.getConnectorInfo();
        String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
            connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier());
        deleteConnectorHeartbeatTask(accountIdentifier, fullyQualifiedIdentifier, connectorHeartbeatTaskId.getId());
      }
      throw ex;
    }
  }

  private void runTestConnectionAsync(ConnectorDTO connectorRequestDTO, String accountIdentifier) {
    // User can create a connector without test connection, in this flow we won't have
    // a status for the connector, to solve this issue we will do one test connection
    // asynchronously
    executorService.submit(() -> validate(connectorRequestDTO, accountIdentifier));
  }

  private void createConnectorCreationActivity(String accountIdentifier, ConnectorInfoDTO connector) {
    try (AutoLogContext ignore1 = new NgAutoLogContext(
             connector.getProjectIdentifier(), connector.getOrgIdentifier(), accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ConnectorLogContext(connector.getIdentifier(), OVERRIDE_ERROR);) {
      connectorActivityService.create(accountIdentifier, connector, NGActivityType.ENTITY_CREATION);
    } catch (Exception ex) {
      log.info("Error while creating connector creation activity", ex);
    }
  }

  /***
   * The usual update logic involves getting the entity, updating it and saving it
   * In the case when user selected commit to a new branch while updating a connector
   * We need to
   *   1. Update the connector in git
   *   2. Create a new record for this branch in our mongo
   * We are handling this case using the below if statement
   ***/
  @Override
  public ConnectorResponseDTO update(@NotNull ConnectorDTO connector, String accountIdentifier) {
    return update(connector, accountIdentifier, ChangeType.MODIFY);
  }

  @Override
  public ConnectorResponseDTO update(ConnectorDTO connector, String accountIdentifier, ChangeType gitChangeType) {
    try (AutoLogContext ignore1 = new NgAutoLogContext(connector.getConnectorInfo().getProjectIdentifier(),
             connector.getConnectorInfo().getOrgIdentifier(), accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 =
             new ConnectorLogContext(connector.getConnectorInfo().getIdentifier(), OVERRIDE_ERROR)) {
      boolean isDefaultBranchConnector = gitSyncSdkService.isDefaultBranch(accountIdentifier,
          connector.getConnectorInfo().getOrgIdentifier(), connector.getConnectorInfo().getProjectIdentifier());
      ConnectorInfoDTO connectorInfo = connector.getConnectorInfo();

      connectorInfo.getConnectorConfig().validate();
      validateTheUpdateRequestIsValid(connectorInfo, accountIdentifier);
      if (GitContextHelper.isUpdateToNewBranch()) {
        return create(connector, accountIdentifier, ChangeType.MODIFY);
      }

      ConnectorResponseDTO connectorResponse =
          getConnectorService(connectorInfo.getConnectorType()).update(connector, accountIdentifier, gitChangeType);
      if (isDefaultBranchConnector) {
        ConnectorInfoDTO savedConnector = connectorResponse.getConnector();
        createConnectorUpdateActivity(accountIdentifier, savedConnector);
        publishEvent(accountIdentifier, savedConnector.getOrgIdentifier(), savedConnector.getProjectIdentifier(),
            savedConnector.getIdentifier(), savedConnector.getConnectorType(),
            EventsFrameworkMetadataConstants.UPDATE_ACTION);
      }
      return connectorResponse;
    }
  }

  private Optional<ConnectorResponseDTO> findExistingConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
    if (gitEntityInfo != null) {
      String repo = gitEntityInfo.getYamlGitConfigId();
      if (GitContextHelper.isUpdateToNewBranch()) {
        return getFromBranch(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier, repo, gitEntityInfo.getBaseBranch());
      }
    }
    return get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  private void validateTheUpdateRequestIsValid(ConnectorInfoDTO connectorInfo, String accountIdentifier) {
    final Optional<ConnectorResponseDTO> connectorDTO = findExistingConnector(accountIdentifier,
        connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new ConnectorNotFoundException(
          connectorErrorMessagesHelper.createConnectorNotFoundMessage(accountIdentifier,
              connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier()),
          USER);
    }
    ConnectorInfoDTO existingConnector = connectorDTO.get().getConnector();
    validateTheConnectorTypeIsNotChanged(existingConnector.getConnectorType(), connectorInfo.getConnectorType(),
        accountIdentifier, connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(),
        connectorInfo.getIdentifier());
    validateNameIsUnique(connectorInfo, accountIdentifier, existingConnector.getIdentifier());
  }

  private void validateNameIsUnique(ConnectorInfoDTO connectorInfo, String accountIdentifier, String identifier) {
    final Optional<ConnectorResponseDTO> existingConnector = getByName(accountIdentifier,
        connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getName(), true);
    if (!existingConnector.isPresent()) {
      return;
    }
    String existingConnectorIdentifier = existingConnector.get().getConnector().getIdentifier();
    if (!isTheSameConnector(existingConnectorIdentifier, identifier)) {
      throw new InvalidRequestException(format("Connector with name [%s] already exists", connectorInfo.getName()));
    }
  }

  private boolean isTheSameConnector(String existingConnectorIdentifier, String newConnectorIdentifier) {
    return newConnectorIdentifier.equals(existingConnectorIdentifier);
  }

  private void validateTheConnectorTypeIsNotChanged(ConnectorType existingConnectorType,
      ConnectorType typeInTheUpdateRequest, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier) {
    if (existingConnectorType != typeInTheUpdateRequest) {
      String noConnectorExistsWithTypeMessage = String.format("%s with type %s",
          connectorErrorMessagesHelper.createConnectorNotFoundMessage(
              accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier),
          typeInTheUpdateRequest);
      throw new InvalidRequestException(noConnectorExistsWithTypeMessage);
    }
  }

  private void publishEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier,
      ConnectorType connectorType, String action) {
    try {
      EntityChangeDTO.Builder connectorUpdateDTOBuilder = EntityChangeDTO.newBuilder()
                                                              .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                              .setIdentifier(StringValue.of(identifier));
      if (isNotBlank(orgIdentifier)) {
        connectorUpdateDTOBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
      }
      if (isNotBlank(projectIdentifier)) {
        connectorUpdateDTOBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
      }
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of(ACCOUNT_IDENTIFIER_METRICS_KEY, accountIdentifier,
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.CONNECTOR_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action,
                  EventsFrameworkMetadataConstants.CONNECTOR_ENTITY_TYPE, connectorType.getDisplayName()))
              .setData(connectorUpdateDTOBuilder.build().toByteString())
              .build());
    } catch (Exception ex) {
      log.info("Exception while publishing the event of connector update for {}",
          String.format(CONNECTOR_STRING, identifier, accountIdentifier, orgIdentifier, projectIdentifier));
    }
  }

  private void createConnectorUpdateActivity(String accountIdentifier, ConnectorInfoDTO connector) {
    try {
      connectorActivityService.create(accountIdentifier, connector, NGActivityType.ENTITY_UPDATE);
    } catch (Exception ex) {
      log.info("Error while creating connector update activity", ex);
    }
  }

  @Override
  public boolean delete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    try (AutoLogContext ignore1 =
             new NgAutoLogContext(projectIdentifier, orgIdentifier, accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ConnectorLogContext(connectorIdentifier, OVERRIDE_ERROR)) {
      String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      boolean isDefaultBranchConnector =
          gitSyncSdkService.isDefaultBranch(accountIdentifier, orgIdentifier, projectIdentifier);

      Optional<Connector> connectorOptional = connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
          fullyQualifiedIdentifier, projectIdentifier, orgIdentifier, accountIdentifier, true);
      if (connectorOptional.isPresent()) {
        Connector connector = connectorOptional.get();
        boolean isConnectorHeartbeatDeleted = deleteConnectorHeartbeatTask(
            accountIdentifier, fullyQualifiedIdentifier, connector.getHeartbeatPerpetualTaskId());
        if (isConnectorHeartbeatDeleted || connector.getHeartbeatPerpetualTaskId() == null) {
          boolean isConnectorDeleted =
              getConnectorService(connector.getType())
                  .delete(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
          if (!isDefaultBranchConnector) {
            return true;
          }
          if (isConnectorDeleted) {
            publishEvent(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, connector.getType(),
                EventsFrameworkMetadataConstants.DELETE_ACTION);
            return true;
          } else {
            PerpetualTaskId perpetualTaskId = connectorHeartbeatService.createConnectorHeatbeatTask(
                accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
            updateConnectorEntityWithPerpetualtaskId(
                accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, perpetualTaskId.getId());
            return false;
          }
        }
        throw new InvalidRequestException("Could not delete connector because heartbeat could not be deleted", USER);
      }
      throw new InvalidRequestException("No such connector found", USER);
    }
  }

  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, ChangeType changeType) {
    return delete(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
  }

  private void deleteConnectorActivities(String accountIdentifier, String connectorFQN) {
    try {
      connectorActivityService.deleteAllActivities(accountIdentifier, connectorFQN);
    } catch (Exception ex) {
      log.info("Error while deleting connector activity", ex);
    }
  }

  private boolean deleteConnectorHeartbeatTask(String accountIdentifier, String connectorFQN, String heartbeatTaskId) {
    if (isNotBlank(heartbeatTaskId)) {
      boolean perpetualTaskIsDeleted =
          connectorHeartbeatService.deletePerpetualTask(accountIdentifier, heartbeatTaskId, connectorFQN);
      if (perpetualTaskIsDeleted == false) {
        log.info("{} The perpetual task could not be deleted {}", CONNECTOR_HEARTBEAT_LOG_PREFIX, connectorFQN);
        return false;
      }
    } else {
      log.info("{} The perpetual task id is empty for the connector {}", CONNECTOR_HEARTBEAT_LOG_PREFIX, connectorFQN);
      return false;
    }
    log.info(
        "{} Deleted the heartbeat perpetual task for the connector {}", CONNECTOR_HEARTBEAT_LOG_PREFIX, connectorFQN);
    return true;
  }

  @Override
  public ConnectorValidationResult validate(@NotNull ConnectorDTO connector, String accountIdentifier) {
    ConnectorValidationResult validationResult = null;
    ConnectorInfoDTO connectorInfoDTO = null;
    try (AutoLogContext ignore1 = new NgAutoLogContext(connector.getConnectorInfo().getProjectIdentifier(),
             connector.getConnectorInfo().getOrgIdentifier(), accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 =
             new ConnectorLogContext(connector.getConnectorInfo().getIdentifier(), OVERRIDE_ERROR)) {
      connectorInfoDTO = connector.getConnectorInfo();
      validationResult = defaultConnectorService.validate(connector, accountIdentifier);
      return validationResult;
    } catch (WingsException ex) {
      // Special case handling for flows registered with error handling framework
      ConnectorValidationResultBuilder validationFailureBuilder = ConnectorValidationResult.builder();
      validationFailureBuilder.status(FAILURE).testedAt(System.currentTimeMillis());
      String errorMessage = ex.getMessage();
      if (isNotEmpty(errorMessage)) {
        String errorSummary = ngErrorHelper.getErrorSummary(errorMessage);
        List<ErrorDetail> errorDetail = Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage));
        validationFailureBuilder.errorSummary(errorSummary).errors(errorDetail);
      }
      validationResult = validationFailureBuilder.build();
      throw ex;
    } finally {
      updateTheConnectorValidationResultInTheEntity(validationResult, accountIdentifier,
          connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier(),
          connectorInfoDTO.getIdentifier());
    }
  }

  @Override
  public boolean validateTheIdentifierIsUnique(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    return defaultConnectorService.validateTheIdentifierIsUnique(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
  }

  @Override
  public long count(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return defaultConnectorService.count(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public ConnectorValidationResult testConnection(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    ConnectorValidationResult connectorValidationResult = null;
    try (AutoLogContext ignore1 =
             new NgAutoLogContext(projectIdentifier, orgIdentifier, accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ConnectorLogContext(connectorIdentifier, OVERRIDE_ERROR)) {
      Optional<ConnectorResponseDTO> connectorDTO =
          get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      if (connectorDTO.isPresent()) {
        ConnectorResponseDTO connectorResponse = connectorDTO.get();
        ConnectorInfoDTO connectorInfoDTO = connectorResponse.getConnector();
        connectorValidationResult =
            getConnectorService(connectorInfoDTO.getConnectorType())
                .testConnection(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
        return connectorValidationResult;
      } else {
        throw new ConnectorNotFoundException(
            connectorErrorMessagesHelper.createConnectorNotFoundMessage(
                accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier),
            USER);
      }
    } catch (ConnectorNotFoundException connectorNotFoundException) {
      // No handling required for this exception
      throw connectorNotFoundException;
    } catch (WingsException wingsException) {
      // Special case handling for flows registered with error handling framework
      ConnectorValidationResultBuilder validationFailureBuilder = ConnectorValidationResult.builder();
      validationFailureBuilder.status(FAILURE).testedAt(System.currentTimeMillis());
      String errorMessage = wingsException.getMessage();
      if (isNotEmpty(errorMessage)) {
        String errorSummary = ngErrorHelper.getErrorSummary(errorMessage);
        List<ErrorDetail> errorDetail = Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage));
        validationFailureBuilder.errorSummary(errorSummary).errors(errorDetail);
      }
      connectorValidationResult = validationFailureBuilder.build();
      throw wingsException;
    } finally {
      if (connectorValidationResult != null) {
        updateTheConnectorValidationResultInTheEntity(
            connectorValidationResult, accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      }
    }
  }

  private void updateTheConnectorValidationResultInTheEntity(ConnectorValidationResult connectorValidationResult,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    try {
      Connector connector =
          getConnectorWithIdentifier(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      setConnectivityStatusInConnector(connector, connectorValidationResult, connector.getConnectivityDetails());
      connectorRepository.save(connector, ChangeType.NONE);
    } catch (Exception ex) {
      log.error("Error saving the connector status for the connector {}",
          String.format(CONNECTOR_STRING, connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier),
          ex);
    }
  }

  private Connector getConnectorWithIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    String fullyQualifiedIdentifier = FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);

    Optional<Connector> connectorOptional = connectorRepository.findByFullyQualifiedIdentifierAndDeletedNot(
        fullyQualifiedIdentifier, projectIdentifier, orgIdentifier, accountIdentifier, true);

    return connectorOptional.orElseThrow(
        ()
            -> new InvalidRequestException(connectorErrorMessagesHelper.createConnectorNotFoundMessage(
                accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier)));
  }

  private void setConnectivityStatusInConnector(Connector connector,
      ConnectorValidationResult connectorValidationResult, ConnectorConnectivityDetails lastStatus) {
    setLastUpdatedTimeIfNotPresent(connector);
    if (connectorValidationResult != null) {
      long connectivityTestedAt = getCurrentTimeIfActivityTimeIsNull(connectorValidationResult.getTestedAt());
      ConnectorConnectivityDetailsBuilder connectorConnectivityDetailsBuilder =
          ConnectorConnectivityDetails.builder()
              .status(connectorValidationResult.getStatus())
              .testedAt(connectivityTestedAt);
      if (connectorValidationResult.getStatus() == SUCCESS) {
        connectorConnectivityDetailsBuilder.lastConnectedAt(connectivityTestedAt);
      } else {
        connectorConnectivityDetailsBuilder.lastConnectedAt(lastStatus == null ? 0 : lastStatus.getLastConnectedAt())
            .errorSummary(connectorValidationResult.getErrorSummary())
            .errors(connectorValidationResult.getErrors());
      }
      connector.setConnectivityDetails(connectorConnectivityDetailsBuilder.build());
    }
  }

  private void setLastUpdatedTimeIfNotPresent(Connector connector) {
    if (connector.getTimeWhenConnectorIsLastUpdated() == null) {
      connector.setTimeWhenConnectorIsLastUpdated(connector.getCreatedAt());
    }
  }

  private long getCurrentTimeIfActivityTimeIsNull(long activityTime) {
    if (activityTime == 0L) {
      return System.currentTimeMillis();
    }
    return activityTime;
  }

  @Override
  public void updateConnectorEntityWithPerpetualtaskId(String accountIdentifier, String connectorOrgIdentifier,
      String connectorProjectIdentifier, String connectorIdentifier, String perpetualTaskId) {
    try (AutoLogContext ignore1 = new NgAutoLogContext(
             connectorProjectIdentifier, connectorOrgIdentifier, accountIdentifier, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ConnectorLogContext(connectorIdentifier, OVERRIDE_ERROR);) {
      defaultConnectorService.updateConnectorEntityWithPerpetualtaskId(
          accountIdentifier, connectorOrgIdentifier, connectorProjectIdentifier, connectorIdentifier, perpetualTaskId);
    }
  }

  @Override
  public void updateActivityDetailsInTheConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, ConnectorValidationResult connectorValidationResult,
      Long activityTime) {
    Connector connector = getConnectorWithIdentifier(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (activityTime != null) {
      setActivityDetailInTheConnector(connector, activityTime);
    }
    if (connectorValidationResult != null) {
      setConnectivityStatusInConnector(connector, connectorValidationResult, connector.getConnectivityDetails());
    }
    connectorRepository.save(connector, ChangeType.NONE);
  }

  private void setActivityDetailInTheConnector(Connector connector, long activityTime) {
    long lastActivityTime = getCurrentTimeIfActivityTimeIsNull(activityTime);
    ConnectorActivityDetails connectorActivityDetails =
        ConnectorActivityDetails.builder().lastActivityTime(lastActivityTime).build();
    connector.setActivityDetails(connectorActivityDetails);
  }

  @Override
  public ConnectorCatalogueResponseDTO getConnectorCatalogue() {
    return defaultConnectorService.getConnectorCatalogue();
  }

  @Override
  public ConnectorValidationResult testGitRepoConnection(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String gitRepoURL) {
    return defaultConnectorService.testGitRepoConnection(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, gitRepoURL);
  }

  @Override
  public ConnectorStatistics getConnectorStatistics(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return defaultConnectorService.getConnectorStatistics(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public String getHeartbeatPerpetualTaskId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return defaultConnectorService.getHeartbeatPerpetualTaskId(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public void resetHeartbeatForReferringConnectors(List<Pair<String, String>> connectorPerpetualTaskInfoList) {
    defaultConnectorService.resetHeartbeatForReferringConnectors(connectorPerpetualTaskInfoList);
  }

  @Override
  public Page<ConnectorResponseDTO> list(int page, int size, String accountIdentifier,
      ConnectorFilterPropertiesDTO filterProperties, String orgIdentifier, String projectIdentifier,
      String filterIdentifier, String searchTerm, Boolean includeAllConnectorsAccessibleAtScope,
      Boolean getDistinctFromBranches) {
    return defaultConnectorService.list(page, size, accountIdentifier, filterProperties, orgIdentifier,
        projectIdentifier, filterIdentifier, searchTerm, includeAllConnectorsAccessibleAtScope,
        getDistinctFromBranches);
  }

  @Override
  public Page<ConnectorResponseDTO> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, ConnectorType type, ConnectorCategory category,
      ConnectorCategory sourceCategory) {
    return defaultConnectorService.list(
        page, size, accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, type, category, sourceCategory);
  }

  @Override
  public List<ConnectorResponseDTO> listbyFQN(String accountIdentifier, List<String> connectorFQN) {
    return defaultConnectorService.listbyFQN(accountIdentifier, connectorFQN);
  }

  @Override
  public void deleteBatch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifiersList) {
    defaultConnectorService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifiersList);
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String invalidYaml) {
    return defaultConnectorService.markEntityInvalid(accountIdentifier, entityReference, invalidYaml);
  }

  @Override
  public boolean checkConnectorExecutableOnDelegate(ConnectorInfoDTO connectorInfo) {
    return defaultConnectorService.checkConnectorExecutableOnDelegate(connectorInfo);
  }

  private ConnectorValidationResult createValidationResultWithGenericError(Exception ex) {
    List<ErrorDetail> errorDetails = Collections.singletonList(ngErrorHelper.getGenericErrorDetail());
    return ConnectorValidationResult.builder()
        .errors(errorDetails)
        .errorSummary(DEFAULT_ERROR_SUMMARY)
        .testedAt(System.currentTimeMillis())
        .status(FAILURE)
        .build();
  }

  @Override
  public Optional<ConnectorResponseDTO> getByName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String name, boolean isDeletedAllowed) {
    return defaultConnectorService.getByName(
        accountIdentifier, orgIdentifier, projectIdentifier, name, isDeletedAllowed);
  }

  @Override
  public Optional<ConnectorResponseDTO> getFromBranch(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String repo, String branch) {
    return defaultConnectorService.getFromBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, repo, branch);
  }

  @Override
  public ConnectorDTO fullSyncEntity(EntityDetailProtoDTO entityDetailProtoDTO, boolean isFullSyncingToDefaultBranch) {
    return defaultConnectorService.fullSyncEntity(entityDetailProtoDTO, isFullSyncingToDefaultBranch);
  }

  @Override
  public ConnectorResponseDTO updateGitFilePath(
      ConnectorDTO connectorDTO, String accountIdentifier, String newFilePath) {
    return defaultConnectorService.updateGitFilePath(connectorDTO, accountIdentifier, newFilePath);
  }
}
