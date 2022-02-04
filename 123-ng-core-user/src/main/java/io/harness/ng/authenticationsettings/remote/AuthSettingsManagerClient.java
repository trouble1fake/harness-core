/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.user.TwoFactorAdminOverrideSettings;
import io.harness.rest.RestResponse;

import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.PasswordStrengthPolicy;
import software.wings.beans.sso.OauthSettings;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.SSOConfig;

import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface AuthSettingsManagerClient {
  String API_PREFIX = "ng/";

  @GET(API_PREFIX + "sso/get-access-management")
  Call<RestResponse<SSOConfig>> getAccountAccessManagementSettings(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "accounts/get-whitelisted-domains")
  Call<RestResponse<Set<String>>> getWhitelistedDomains(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "login-settings/username-password")
  Call<RestResponse<LoginSettings>> getUserNamePasswordSettings(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "accounts/two-factor-enabled")
  Call<RestResponse<Boolean>> twoFactorEnabled(@Query("accountId") @NotEmpty String accountId);

  @POST(API_PREFIX + "sso/oauth-settings-upload")
  Call<RestResponse<SSOConfig>> uploadOauthSettings(
      @Query("accountId") @NotEmpty String accountId, @Body OauthSettings oauthSettings);

  @DELETE(API_PREFIX + "sso/delete-oauth-settings")
  Call<RestResponse<SSOConfig>> deleteOauthSettings(@Query("accountId") String accountId);

  @PUT(API_PREFIX + "sso/assign-auth-mechanism")
  Call<RestResponse<SSOConfig>> updateAuthMechanism(@Query("accountId") @NotEmpty String accountId,
      @Query("authMechanism") AuthenticationMechanism authenticationMechanism);

  @PUT(API_PREFIX + "login-settings/{loginSettingsId}")
  Call<RestResponse<LoginSettings>> updateLoginSettings(@Path("loginSettingsId") String loginSettingsId,
      @Query("accountId") @NotEmpty String accountId, @Body @NotNull @Valid LoginSettings loginSettings);

  @PUT(API_PREFIX + "accounts/whitelisted-domains")
  Call<RestResponse<LoginSettings>> updateWhitelistedDomains(
      @Query("accountId") @NotEmpty String accountId, @Body Set<String> whitelistedDomains);

  @Multipart
  @POST(API_PREFIX + "sso/saml-idp-metadata-upload")
  Call<RestResponse<SSOConfig>> uploadSAMLMetadata(@Query("accountId") String accountId,
      @Part MultipartBody.Part uploadedInputStream, @Part("displayName") RequestBody displayName,
      @Part("groupMembershipAttr") RequestBody groupMembershipAttr,
      @Part("authorizationEnabled") RequestBody authorizationEnabled, @Part("logoutUrl") RequestBody logoutUrl,
      @Part("entityIdentifier") RequestBody entityIdentifier, @Part("samlProviderType") RequestBody samlProviderType,
      @Part("clientId") RequestBody clientId, @Part("clientSecret") RequestBody clientSecret);

  @Multipart
  @PUT(API_PREFIX + "sso/saml-idp-metadata-upload")
  Call<RestResponse<SSOConfig>> updateSAMLMetadata(@Query("accountId") String accountId,
      @Part MultipartBody.Part uploadedInputStream, @Part("displayName") RequestBody displayName,
      @Part("groupMembershipAttr") RequestBody groupMembershipAttr,
      @Part("authorizationEnabled") RequestBody authorizationEnabled, @Part("logoutUrl") RequestBody logoutUrl,
      @Part("entityIdentifier") RequestBody entityIdentifier, @Part("samlProviderType") RequestBody samlProviderType,
      @Part("clientId") RequestBody clientId, @Part("clientSecret") RequestBody clientSecret);

  @DELETE(API_PREFIX + "sso/delete-saml-idp-metadata")
  Call<RestResponse<SSOConfig>> deleteSAMLMetadata(@Query("accountId") String accountIdentifier);

  @GET(API_PREFIX + "sso/saml-login-test")
  Call<RestResponse<LoginTypeResponse>> getSAMLLoginTest(@Query("accountId") @NotEmpty String accountIdentifier);

  @PUT(API_PREFIX + "user/two-factor-admin-override-settings")
  Call<RestResponse<Boolean>> setTwoFactorAuthAtAccountLevel(@Query("accountId") @NotEmpty String accountId,
      @Body TwoFactorAdminOverrideSettings twoFactorAdminOverrideSettings);

  @GET(API_PREFIX + "login-settings/username-password/password-strength-policy")
  Call<RestResponse<PasswordStrengthPolicy>> getPasswordStrengthSettings(@Query("accountId") String accountIdentifier);
}
