/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PHOENIKX;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.connector.services.NGVaultService;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataSpecDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.security.encryption.EncryptionType;

import software.wings.service.impl.security.NGEncryptorService;

import java.io.IOException;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class NGSecretManagerServiceImplTest extends CategoryTest {
  SecretManagerClient secretManagerClient;
  NGConnectorSecretManagerService ngConnectorSecretManagerService;
  KmsEncryptorsRegistry kmsEncryptorsRegistry;
  VaultEncryptorsRegistry vaultEncryptorsRegistry;
  NGEncryptedDataService ngEncryptedDataService;
  NGEncryptorService ngEncryptorService;
  NGVaultService ngVaultService;
  ConnectorService connectorService;

  private final String ACCOUNT_IDENTIFIER = "ACCOUNT_ID";
  private final String ORG_IDENTIFIER = "ACCOUNT_ID";
  private final String PROJECT_IDENTIFIER = "ACCOUNT_ID";
  private final String KMS_IDENTIFIER = "KMS_ID";

  private NGSecretManagerServiceImpl ngSecretManagerService;

  @Before
  public void doSetup() {
    secretManagerClient = mock(SecretManagerClient.class, RETURNS_DEEP_STUBS);
    ngVaultService = mock(NGVaultService.class);
    ngConnectorSecretManagerService = mock(NGConnectorSecretManagerService.class);
    ngEncryptedDataService = mock(NGEncryptedDataService.class);
    ngEncryptorService = mock(NGEncryptorService.class);
    connectorService = mock(ConnectorService.class);
    ngSecretManagerService = new NGSecretManagerServiceImpl(secretManagerClient, ngConnectorSecretManagerService,
        kmsEncryptorsRegistry, vaultEncryptorsRegistry, ngVaultService);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreateSecretManager() throws IOException {
    SecretManagerConfigDTO dto = random(VaultConfigDTO.class);
    Call<RestResponse<SecretManagerConfigDTO>> request = mock(Call.class);

    when(secretManagerClient.createSecretManager(any())).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(dto)));

    SecretManagerConfigDTO savedDTO = ngSecretManagerService.createSecretManager(random(VaultConfigDTO.class));

    assertThat(savedDTO).isEqualTo(dto);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDeleteSecretManager() throws IOException {
    Call<RestResponse<Boolean>> request = mock(Call.class);

    when(secretManagerClient.deleteSecretManager(any(), any(), any(), any())).thenReturn(request);
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    boolean success = ngSecretManagerService.deleteSecretManager(
        KMS_IDENTIFIER, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(success).isTrue();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testValidateSecretManager() throws IOException {
    ConnectorValidationResult result = ngSecretManagerService.validate("account", null, null, "identifier");
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetMetadata() throws IOException {
    when(ngVaultService.getListOfEngines(any(), any()))
        .thenReturn(SecretManagerMetadataDTO.builder()
                        .encryptionType(EncryptionType.VAULT)
                        .spec(VaultMetadataSpecDTO.builder().secretEngines(new ArrayList<>()).build())
                        .build());
    SecretManagerMetadataDTO metadataDTO = ngSecretManagerService.getMetadata("Account", null);

    assertThat(metadataDTO).isNotNull();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetGlobalSecretManager() throws IOException {
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(LocalConfigDTO.builder().build());
    SecretManagerConfigDTO responseDTO = ngSecretManagerService.getGlobalSecretManager("Account");

    assertThat(responseDTO).isNotNull();
    verify(secretManagerClient, never()).getGlobalSecretManager(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetSecretManager() throws IOException {
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(LocalConfigDTO.builder().build());
    SecretManagerConfigDTO secretManagerConfigDTO =
        ngSecretManagerService.getSecretManager("account", null, null, "identifier", true);
    assertThat(secretManagerConfigDTO).isNotNull();
    verify(secretManagerClient, never()).getSecretManager(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdateSecretManager() throws IOException {
    when(secretManagerClient.updateSecretManager(any(), any(), any(), any(), any()).execute())
        .thenReturn(Response.success(new RestResponse<>(LocalConfigDTO.builder().build())));
    SecretManagerConfigDTO secretManagerConfigDTO = ngSecretManagerService.updateSecretManager(
        "account", null, null, "identifier", VaultConfigUpdateDTO.builder().build());
    assertThat(secretManagerConfigDTO).isNotNull();
    verify(secretManagerClient, atLeastOnce()).updateSecretManager(any(), any(), any(), any(), any());
  }
}
