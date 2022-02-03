/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.vault.VaultAppRoleLoginRequest;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResponse;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.helpers.ext.vault.VaultAwsIamAuthLoginRequest;
import io.harness.helpers.ext.vault.VaultRestClientFactory;
import io.harness.helpers.ext.vault.VaultSysAuthRestClient;

import software.wings.beans.BaseVaultConfig;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.HttpMethodName;
import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@UtilityClass
@OwnedBy(PL)
@Slf4j
public class NGVaultTaskHelper {
  public static final String AWS_IAM_REQUEST_BODY = "Action=GetCallerIdentity&Version=2011-06-15";
  public static final String AWS_IAM_ENDPOINT_FORMAT = "https://sts.%s.amazonaws.com";

  public static VaultAppRoleLoginResult getVaultAppRoleLoginResult(BaseVaultConfig vaultConfig) {
    try {
      VaultSysAuthRestClient restClient =
          VaultRestClientFactory.getVaultRetrofit(vaultConfig.getVaultUrl(), vaultConfig.isCertValidationRequired())
              .create(VaultSysAuthRestClient.class);

      VaultAppRoleLoginRequest loginRequest = VaultAppRoleLoginRequest.builder()
                                                  .roleId(vaultConfig.getAppRoleId())
                                                  .secretId(vaultConfig.getSecretId())
                                                  .build();
      Response<VaultAppRoleLoginResponse> response =
          restClient.appRoleLogin(vaultConfig.getNamespace(), loginRequest).execute();

      VaultAppRoleLoginResult result = null;
      if (response.isSuccessful()) {
        result = response.body().getAuth();
      } else {
        logAndThrowVaultError(vaultConfig, response, "AppRole Based Login");
      }
      return result;
    } catch (IOException e) {
      String message = "NG: Failed to perform AppRole based login for secret manager " + vaultConfig.getName() + " at "
          + vaultConfig.getVaultUrl();
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
    }
  }

  public static VaultAppRoleLoginResult getVaultAwmIamAuthLoginResult(BaseVaultConfig vaultConfig) {
    try {
      VaultSysAuthRestClient restClient =
          VaultRestClientFactory.getVaultRetrofit(vaultConfig.getVaultUrl(), vaultConfig.isCertValidationRequired())
              .create(VaultSysAuthRestClient.class);

      String base64EncodedRequestBody = getBase64EncodedRequestBody();
      String base64EncodedRequestUrl = getBase64EncodedRequestUrl(vaultConfig.getAwsRegion());
      String iamRequestHeaders = getBase64EncodedRequestHeaders(vaultConfig);

      VaultAwsIamAuthLoginRequest loginRequest = VaultAwsIamAuthLoginRequest.builder()
                                                     .roleId(vaultConfig.getVaultAwsIamRole())
                                                     .iamHttpRequestMethod("POST")
                                                     .iamRequestHeaders(iamRequestHeaders)
                                                     .iamRequestBody(base64EncodedRequestBody)
                                                     .iamRequestUrl(base64EncodedRequestUrl)
                                                     .build();
      Response<VaultAppRoleLoginResponse> response =
          restClient.awsIamAuthLogin(vaultConfig.getNamespace(), loginRequest).execute();

      VaultAppRoleLoginResult result = null;
      if (response.isSuccessful()) {
        result = response.body().getAuth();
      } else {
        logAndThrowVaultError(vaultConfig, response, "Aws IAM Auth based login");
      }
      return result;
    } catch (IOException | URISyntaxException e) {
      String message = "NG: Failed to perform Aws IAM Auth based login for secret manager " + vaultConfig.getName()
          + " at " + vaultConfig.getVaultUrl();
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
    }
  }

  public static void logAndThrowVaultError(BaseVaultConfig baseVaultConfig, Response response, String operation)
      throws IOException {
    if (baseVaultConfig == null || response == null) {
      return;
    }
    String errorMsg = "";
    if (response.errorBody() != null) {
      errorMsg =
          String.format("Failed to %s for Vault: %s And Namespace: %s due to the following error from vault: \"%s\".",
              operation, baseVaultConfig.getName(), baseVaultConfig.getNamespace(), response.errorBody().string());
    } else {
      errorMsg = String.format(
          "Failed to %s for Vault: %s And Namespace: %s due to the following error from vault: \"%s\".", operation,
          baseVaultConfig.getName(), baseVaultConfig.getNamespace(), response.message() + response.body());
    }
    log.error(errorMsg);
    throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, errorMsg, USER);
  }

  public static String getToken(BaseVaultConfig vaultConfig) {
    if (vaultConfig.isUseVaultAgent()) {
      try {
        byte[] content = Files.readAllBytes(Paths.get(URI.create("file://" + vaultConfig.getSinkPath())));
        String token = new String(content);
        vaultConfig.setAuthToken(token);
      } catch (IOException e) {
        throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR,
            "Using Vault Agent Cannot read Token From Sink Path:" + vaultConfig.getSinkPath(), e, USER);
      }
    } else if (vaultConfig.isUseAwsIam()) {
      VaultAppRoleLoginResult vaultAwmIamAuthLoginResult = getVaultAwmIamAuthLoginResult(vaultConfig);
      vaultConfig.setAuthToken(vaultAwmIamAuthLoginResult.getClientToken());
    }
    return vaultConfig.getAuthToken();
  }

  private String getBase64EncodedRequestBody() {
    return Base64.getEncoder().encodeToString(AWS_IAM_REQUEST_BODY.getBytes(StandardCharsets.UTF_8));
  }

  private String getBase64EncodedRequestUrl(String region) {
    return Base64.getEncoder().encodeToString(getEndPoint(region).getBytes(StandardCharsets.UTF_8));
  }

  private static String getEndPoint(String region) {
    return String.format(AWS_IAM_ENDPOINT_FORMAT, region);
  }

  String getBase64EncodedRequestHeaders(BaseVaultConfig vaultConfig) throws URISyntaxException {
    final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
    headers.put("X-Vault-AWS-IAM-Server-ID", vaultConfig.getXVaultAwsIamServerId());
    headers.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

    final DefaultRequest defaultRequest = new DefaultRequest("sts");
    defaultRequest.setContent(new ByteArrayInputStream(AWS_IAM_REQUEST_BODY.getBytes(StandardCharsets.UTF_8)));
    defaultRequest.setHeaders(headers);
    defaultRequest.setHttpMethod(HttpMethodName.POST);
    defaultRequest.setEndpoint(new URI(getEndPoint(vaultConfig.getAwsRegion())));

    final AWS4Signer aws4Signer = new AWS4Signer();
    aws4Signer.setServiceName(defaultRequest.getServiceName());
    aws4Signer.setRegionName(vaultConfig.getAwsRegion());
    AWSCredentials awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();
    aws4Signer.sign(defaultRequest, awsCredentials);

    final LinkedHashMultimap<String, String> signedHeaders = LinkedHashMultimap.create();
    final Map<String, String> defaultRequestHeaders = defaultRequest.getHeaders();
    defaultRequestHeaders.forEach((k, v) -> signedHeaders.put(k, v));

    final JsonObject jsonObject = new JsonObject();
    signedHeaders.asMap().forEach((k, v) -> {
      final JsonArray array = new JsonArray();
      v.forEach(array::add);
      jsonObject.add(k, array);
    });

    final String signedHeaderString = jsonObject.toString();
    return Base64.getEncoder().encodeToString(signedHeaderString.getBytes(StandardCharsets.UTF_8));
  }
}
