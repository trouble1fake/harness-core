/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.LAZAR;
import static io.harness.rule.OwnerRule.MEHUL;
import static io.harness.rule.OwnerRule.MOHIT;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.NATHAN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS;
import static io.harness.rule.OwnerRule.VOJIN;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.common.VerificationConstants.SERVICE_GUAARD_LIMIT;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.ILLEGAL_ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.PORTAL_URL;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.ServiceGuardLimitDTO;
import io.harness.data.structure.UUIDGenerator;
import io.harness.datahandler.models.AccountDetails;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Role;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.StringValue;
import software.wings.beans.StringValue.Builder;
import software.wings.beans.SubdomainUrl;
import software.wings.beans.TechStack;
import software.wings.beans.UrlInfo;
import software.wings.beans.User;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.dl.WingsPersistence;
import software.wings.features.GovernanceFeature;
import software.wings.features.api.PremiumFeature;
import software.wings.helpers.ext.mail.EmailData;
import software.wings.licensing.LicenseService;
import software.wings.resources.AccountResource;
import software.wings.resources.UserResource;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.AccountServiceImpl;
import software.wings.service.impl.analysis.CVEnabledService;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.account.AccountCrudObserver;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.template.TemplateGalleryService;
import software.wings.sm.StateType;
import software.wings.utils.AccountPermissionUtils;
import software.wings.verification.CVConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
public class AccountServiceTest extends WingsBaseTest {
  private static final SecureRandom random = new SecureRandom();

  @Mock private AppService appService;
  @Mock private UserService userService;
  @Mock private SettingsService settingsService;
  @Mock private TemplateGalleryService templateGalleryService;
  @Mock private DelegateProfileService profileService;
  @Mock private UserPermissionInfo mockUserPermissionInfo;
  @Mock private AuthService authService;
  @Mock private EmailNotificationService emailNotificationService;
  @Mock private HarnessUserGroupService harnessUserGroupService;
  @Mock private AccountPermissionUtils accountPermissionUtils;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private MainConfiguration configuration;

  @InjectMocks @Inject private LicenseService licenseService;
  @InjectMocks @Inject private AccountService accountService;
  @InjectMocks @Inject private UserResource userResource;
  @InjectMocks @Inject private AccountResource accountResource;
  @Mock private AuthHandler authHandler;

  @Mock private GovernanceConfigService governanceConfigService;
  @Inject @Named(GovernanceFeature.FEATURE_NAME) private PremiumFeature governanceFeature;

  @Inject private WingsPersistence wingsPersistence;

  @Rule public ExpectedException thrown = ExpectedException.none();
  private static final String HARNESS_NAME = "Harness";
  private static final String CLUSTER_NAME = "Paid";
  private final String serviceId = UUID.randomUUID().toString();
  private final String envId = UUID.randomUUID().toString();
  private final String accountId = UUID.randomUUID().toString();
  private final String appId = UUID.randomUUID().toString();
  private final String workflowId = UUID.randomUUID().toString();
  private final String cvConfigId = UUID.randomUUID().toString();
  private final User user = new User();

  @Before
  public void setup() throws IllegalAccessException {
    FieldUtils.writeField(licenseService, "accountService", accountService, true);
    FieldUtils.writeField(accountService, "licenseService", licenseService, true);
    FieldUtils.writeField(accountResource, "accountPermissionUtils", accountPermissionUtils, true);
  }

  private Account saveAccount(String companyName) {
    return accountService.save(anAccount()
                                   .withCompanyName(companyName)
                                   .withAccountName("Account Name 1")
                                   .withAccountKey("ACCOUNT_KEY")
                                   .withLicenseInfo(getLicenseInfo())
                                   .withWhitelistedDomains(new HashSet<>())
                                   .build(),
        false);
  }

  private Account setUpDataForTestingSetAccountStatusInternal(String accountType) {
    return accountService.save(anAccount()
                                   .withCompanyName(HARNESS_NAME)
                                   .withAccountName(HARNESS_NAME)
                                   .withAccountKey("ACCOUNT_KEY")
                                   .withDefaultExperience(DefaultExperience.NG)
                                   .withCreatedFromNG(false)
                                   .withLicenseInfo(getLicenseInfo(AccountStatus.ACTIVE, accountType))
                                   .build(),
        false);
  }

  private GovernanceConfig getGovernanceConfig(String accountId, boolean deploymentFreezeFlag) {
    GovernanceConfig governanceConfig = GovernanceConfig.builder().deploymentFreeze(deploymentFreezeFlag).build();
    when(governanceConfigService.get(accountId)).thenReturn(governanceConfig);

    when(governanceConfigService.upsert(any(String.class), any(GovernanceConfig.class))).thenReturn(governanceConfig);
    return governanceConfig;
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSetAccountStatusInternalForPaidAccount() {
    Account account = setUpDataForTestingSetAccountStatusInternal(AccountType.PAID);
    GovernanceConfig governanceConfig = getGovernanceConfig(account.getUuid(), false);
    boolean result = accountService.disableAccount(account.getUuid(), null);
    assertThat(result).isTrue();
    assertThat(governanceConfig.isDeploymentFreeze()).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testGetAccountDetails() {
    when(configuration.getDeploymentClusterName()).thenReturn(CLUSTER_NAME);
    Account account = setUpDataForTestingSetAccountStatusInternal(AccountType.PAID);
    AccountDetails details = accountService.getAccountDetails(account.getUuid());
    assertThat(details.getCluster()).isEqualTo(CLUSTER_NAME);
    assertThat(details.getAccountName()).isEqualTo(HARNESS_NAME);
    assertThat(details.getDefaultExperience()).isEqualTo(DefaultExperience.NG);
    assertThat(details.isCreatedFromNG()).isEqualTo(false);
    assertThat(details.getLicenseInfo().getAccountType()).isEqualTo(AccountType.PAID);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testSetAccountStatusInternalForCommunityAccount() {
    Account account = setUpDataForTestingSetAccountStatusInternal(AccountType.COMMUNITY);
    GovernanceConfig governanceConfig = getGovernanceConfig(account.getUuid(), false);
    boolean result = accountService.disableAccount(account.getUuid(), null);
    assertThat(result).isTrue();
    assertThat(governanceConfig.isDeploymentFreeze()).isFalse();
  }

  private LicenseInfo getLicenseInfo(String accountStatus, String accountType) {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(accountStatus);
    licenseInfo.setAccountType(accountType);
    licenseInfo.setLicenseUnits(100);
    licenseInfo.setExpireAfterDays(15);
    return licenseInfo;
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSaveAccount() {
    AccountCrudObserver accountCrudObserver = mock(AccountCrudObserver.class);
    ((AccountServiceImpl) accountService).getAccountCrudSubject().register(accountCrudObserver);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey("ACCOUNT_KEY")
                                              .withLicenseInfo(getLicenseInfo())
                                              .build(),
        false);
    assertThat(wingsPersistence.get(Account.class, account.getUuid())).isEqualTo(account);
    verify(accountCrudObserver).onAccountCreated(account);
  }

  private Account getAccount() {
    Map<String, UrlInfo> techStacksLinkMap = new HashMap<>();
    techStacksLinkMap.put("Deployment-AWS",
        UrlInfo.builder()
            .title("deployment-aws")
            .url("https://docs.harness.io/article/whwnovprrb-cloud-providers#amazon_web_services_aws_cloud")
            .build());
    techStacksLinkMap.put("Deployment-General",
        UrlInfo.builder()
            .title("deployment-general")
            .url("https://docs.harness.io/article/whwnovprrb-cloud-providers")
            .build());
    techStacksLinkMap.put("Artifact-General",
        UrlInfo.builder()
            .title("artifact-general")
            .url("https://docs.harness.io/article/7dghbx1dbl-configuring-artifact-server")
            .build());
    techStacksLinkMap.put("Monitoring-General",
        UrlInfo.builder()
            .title("monitoring-general")
            .url("https://docs.harness.io/article/r6ut6tldy0-verification-providers")
            .build());
    when(configuration.getTechStackLinks()).thenReturn(techStacksLinkMap);
    return accountService.save(anAccount()
                                   .withCompanyName(HARNESS_NAME)
                                   .withAccountName(HARNESS_NAME)
                                   .withAccountKey("ACCOUNT_KEY")
                                   .withLicenseInfo(getLicenseInfo())
                                   .build(),
        false);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateTechStacks() {
    final User currentUser = User.Builder.anUser().email("user1@harness.io").build();
    UserThreadLocal.set(currentUser);
    Account account = getAccount();
    currentUser.setAccounts(Arrays.asList(account));
    Set<TechStack> techStackSet = new HashSet<>();
    when(userService.getUsersOfAccount(any())).thenReturn(Arrays.asList(currentUser));
    TechStack techStack = TechStack.builder().category("Deployment Platforms").technology("AWS").build();
    techStackSet.add(techStack);
    boolean success = accountService.updateTechStacks(account.getUuid(), techStackSet);
    assertThat(success).isTrue();
    ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
    verify(emailNotificationService).send(captor.capture());
    EmailData emailData = captor.getValue();
    assertThat(emailData.getTo()).hasSize(1);
    assertThat(emailData.getTo()).contains("user1@harness.io");

    Account accountAfterUpdate = accountService.get(account.getUuid());
    assertThat(accountAfterUpdate.getTechStacks()).hasSize(1);
    TechStack techStack1 = accountAfterUpdate.getTechStacks().toArray(new TechStack[0])[0];
    assertThat(techStack1.getCategory()).isEqualTo("Deployment Platforms");
    assertThat(techStack1.getTechnology()).isEqualTo("AWS");
    Map<String, Object> templateModel = (Map<String, Object>) emailData.getTemplateModel();
    List<String> techStackLinks = (List<String>) templateModel.get("deploymentPlatforms");
    assertThat(techStackLinks).hasSize(1);
    String link = techStackLinks.get(0);
    assertThat(link).endsWith(
        "https://docs.harness.io/article/whwnovprrb-cloud-providers#amazon_web_services_aws_cloud");
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateTechStacksWithNullTechStack() {
    final User currentUser = User.Builder.anUser().email("user1@harness.io").build();
    Account account = getAccount();
    UserThreadLocal.set(currentUser);
    currentUser.setAccounts(Arrays.asList(account));
    when(userService.getUsersOfAccount(any())).thenReturn(Arrays.asList(currentUser));
    boolean success = accountService.updateTechStacks(account.getUuid(), null);
    assertThat(success).isTrue();
    ArgumentCaptor<EmailData> captor = ArgumentCaptor.forClass(EmailData.class);
    verify(emailNotificationService).send(captor.capture());
    EmailData emailData = captor.getValue();
    assertThat(emailData.getTo()).hasSize(1);
    assertThat(emailData.getTo()).contains("user1@harness.io");
    Map<String, Object> templateModel = (Map<String, Object>) emailData.getTemplateModel();
    List<String> techStackLinks = (List<String>) templateModel.get("deploymentPlatforms");
    assertThat(techStackLinks).hasSize(1);
    String link = techStackLinks.get(0);
    assertThat(link).endsWith("https://docs.harness.io/article/whwnovprrb-cloud-providers");
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void shouldUpdateTechStacksButSendNoEmail() {
    Account account = getAccount();
    UserThreadLocal.set(new User());
    boolean success = accountService.updateTechStacks(account.getUuid(), null);
    assertThat(success).isTrue();
    when(userService.getUsersOfAccount(any()))
        .thenReturn(
            Arrays.asList(User.Builder.anUser().uuid("userId1").name("name1").email("user1@harness.io").build()));
    verify(emailNotificationService, never()).send(any());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testGetAccountType() {
    LicenseInfo licenseInfo = getLicenseInfo();
    licenseInfo.setAccountType(AccountType.COMMUNITY);

    Account account = accountService.save(anAccount()
                                              .withCompanyName(HARNESS_NAME)
                                              .withAccountName(HARNESS_NAME)
                                              .withAccountKey("ACCOUNT_KEY")
                                              .withLicenseInfo(licenseInfo)
                                              .build(),
        false);

    assertThat(accountService.getAccountType(account.getUuid())).isEqualTo(Optional.of(AccountType.COMMUNITY));
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRegisterNewUser_invalidAccountName_shouldFail() {
    Account account = anAccount()
                          .withCompanyName(COMPANY_NAME)
                          .withAccountName(ILLEGAL_ACCOUNT_NAME)
                          .withLicenseInfo(getLicenseInfo())
                          .build();

    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    assertThatExceptionOfType(WingsException.class).isThrownBy(() -> accountService.save(account, false));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFailSavingAccountWithoutLicense() {
    thrown.expect(WingsException.class);
    thrown.expectMessage("Invalid / Null license info");
    accountService.save(
        anAccount().withCompanyName(HARNESS_NAME).withAccountName(HARNESS_NAME).withAccountKey("ACCOUNT_KEY").build(),
        false);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDeleteAccount() {
    String accountId = wingsPersistence.save(anAccount().withCompanyName(HARNESS_NAME).build());
    accountService.delete(accountId);
    assertThat(wingsPersistence.get(Account.class, accountId)).isNull();
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldUpdateUserAfterDeletingAccount() {
    String accountId = generateUuid();
    Account account = anAccount().withUuid(accountId).withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account);

    String accountId2 = generateUuid();
    Account account2 = anAccount().withUuid(accountId2).withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account2);

    String userId = generateUuid();
    User user = User.Builder.anUser().uuid(userId).name("name1").email("user1@harness.io").build();
    user.setAccounts(Arrays.asList(account, account2));
    Role role1 = new Role();
    role1.setName("testRole1");
    role1.setUuid(generateUuid());
    role1.setAccountId(account.getUuid());
    Role role2 = new Role();
    role2.setName("testRole2");
    role2.setUuid(generateUuid());
    role2.setAccountId(account2.getUuid());
    wingsPersistence.save(role1);
    wingsPersistence.save(role2);
    user.setRoles(Arrays.asList(role1, role2));
    wingsPersistence.save(user);

    assertThat(user.getAccounts().contains(account)).isTrue();
    assertThat(user.getRoles().contains(role1)).isTrue();
    assertThat(user.getAccounts().contains(account2)).isTrue();
    assertThat(user.getRoles().contains(role2)).isTrue();

    accountService.delete(account.getUuid());
    User updatedUser = wingsPersistence.get(User.class, userId);

    assertThat(updatedUser.getAccounts().contains(account)).isFalse();
    assertThat(updatedUser.getRoles().contains(role1)).isFalse();
    assertThat(updatedUser.getAccounts().contains(account2)).isTrue();
    assertThat(updatedUser.getRoles().contains(role2)).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldUpdateCompanyName() {
    Account account = anAccount()
                          .withCompanyName("Wings")
                          .withAccountName("Wings")
                          .withWhitelistedDomains(Collections.singleton("mike@harness.io"))
                          .build();
    wingsPersistence.save(account);
    account.setCompanyName(HARNESS_NAME);
    accountService.update(account);
    assertThat(wingsPersistence.get(Account.class, account.getUuid())).isEqualTo(account);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateDefaultExperience() {
    Account account = anAccount()
                          .withCompanyName("Wings")
                          .withAccountName("Wings")
                          .withWhitelistedDomains(Collections.singleton("mike@harness.io"))
                          .withDefaultExperience(DefaultExperience.CG)
                          .build();
    wingsPersistence.save(account);
    account.setDefaultExperience(DefaultExperience.NG);
    accountService.update(account);
    assertThat(wingsPersistence.get(Account.class, account.getUuid())).isEqualTo(account);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetAccountByCompanyName() {
    Account account = anAccount().withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account);
    assertThat(accountService.getByName(HARNESS_NAME)).isEqualTo(account);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetAccountByAccountName() {
    Account account = anAccount().withAccountName(HARNESS_NAME).withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account);
    assertThat(accountService.getByAccountName(HARNESS_NAME)).isEqualTo(account);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetAccount() {
    Account account = anAccount().withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account);
    assertThat(accountService.get(account.getUuid())).isEqualTo(account);
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void shouldGetDelegateConfiguration() {
    wingsPersistence.save(anAccount()
                              .withUuid(GLOBAL_ACCOUNT_ID)
                              .withCompanyName(HARNESS_NAME)
                              .withDelegateConfiguration(
                                  DelegateConfiguration.builder().delegateVersions(asList("globalVersion")).build())
                              .build());

    String accountId = wingsPersistence.save(anAccount().withCompanyName(HARNESS_NAME).build());

    assertThat(accountService.getDelegateConfiguration(accountId))
        .hasFieldOrPropertyWithValue("delegateVersions", asList("globalVersion"));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldListAllAccounts() {
    Account account = anAccount().withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account);
    assertThat(accountService.get(account.getUuid())).isEqualTo(account);
    assertThat(accountService.listAllAccounts()).isNotEmpty();
    assertThat(accountService.listAccounts(Collections.emptySet())).isNotEmpty();
    assertThat(accountService.listAllAccounts().get(0)).isNotNull();
    assertThat(accountService.listAllAccountWithDefaultsWithoutLicenseInfo()).isNotEmpty();
    assertThat(accountService.listAllAccountWithDefaultsWithoutLicenseInfo().get(0)).isNotNull();
    assertThat(accountService.listAllAccountWithDefaultsWithoutLicenseInfo().get(0).getUuid())
        .isEqualTo(account.getUuid());
    assertThat(accountService.listAllActiveAccounts()).isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldListNonExpiredAndNonDeletedAccounts() {
    Account account = anAccount().withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account);
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(AccountStatus.EXPIRED);
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setLicenseUnits(100);

    Account account1 = anAccount().withCompanyName(HARNESS_NAME + "1").withLicenseInfo(licenseInfo).build();
    wingsPersistence.save(account1);

    assertThat(accountService.listAllActiveAccounts().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetAccountWithDefaults() {
    Account account = anAccount().withCompanyName(HARNESS_NAME).build();
    wingsPersistence.save(account);
    assertThat(account).isNotNull();

    List<SettingAttribute> settingAttributes = asList(aSettingAttribute()
                                                          .withName("NAME")
                                                          .withAccountId(account.getUuid())
                                                          .withValue(Builder.aStringValue().build())
                                                          .build(),
        aSettingAttribute()
            .withName("NAME2")
            .withAccountId(account.getUuid())
            .withValue(Builder.aStringValue().withValue("VALUE").build())
            .build());

    when(settingsService.listAccountDefaults(account.getUuid()))
        .thenReturn(settingAttributes.stream().collect(Collectors.toMap(SettingAttribute::getName,
            settingAttribute
            -> Optional.ofNullable(((StringValue) settingAttribute.getValue()).getValue()).orElse(""),
            (a, b) -> b)));

    account = accountService.getAccountWithDefaults(account.getUuid());
    assertThat(account).isNotNull();
    assertThat(account.getDefaults()).isNotEmpty().containsKeys("NAME", "NAME2");
    assertThat(account.getDefaults()).isNotEmpty().containsValues("", "VALUE");
    verify(settingsService).listAccountDefaults(account.getUuid());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServicesForAccountBreadcrumb() {
    // setup
    setupCvServicesTests(accountId, serviceId + "-test", envId, appId, cvConfigId + "-test", workflowId, user);
    setupCvServicesTests(accountId, serviceId, envId, appId, cvConfigId, workflowId, user);
    PageRequest<String> request = PageRequestBuilder.aPageRequest().withOffset("0").build();

    // test behavior
    List<Service> cvConfigs = accountService.getServicesBreadCrumb(accountId, user);

    // verify results
    assertThat(cvConfigs.size() == 2).isTrue();
    assertThat(serviceId.equals(cvConfigs.get(0).getUuid()) || serviceId.equals(cvConfigs.get(1).getUuid())).isTrue();
    assertThat(cvConfigs.get(0).getName()).isEqualTo("serviceTest");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServicesForAccount() {
    // setup
    setupCvServicesTests(accountId, serviceId, envId, appId, cvConfigId, workflowId, user);
    PageRequest<String> request = PageRequestBuilder.aPageRequest().withOffset("0").build();

    // test behavior
    PageResponse<CVEnabledService> cvConfigs = accountService.getServices(accountId, user, request, null);

    // verify results
    assertThat(cvConfigs.getResponse().size() > 0).isTrue();
    assertThat(cvConfigs.getResponse().get(0).getService().getUuid()).isEqualTo(serviceId);
    assertThat("1").isEqualTo(cvConfigs.getOffset());
    assertThat(cvConfigs.getResponse().get(0).getService().getName()).isEqualTo("serviceTest");
    assertThat(cvConfigs.getResponse().get(0).getCvConfig().get(0).getName()).isEqualTo("NewRelic");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServicesForAccountDisabledCVConfig() {
    // setup
    setupCvServicesTests(accountId, serviceId, envId, appId, cvConfigId, workflowId, user);
    // Save one with isEnabled set to false
    CVConfiguration config = NewRelicCVServiceConfiguration.builder().build();
    config.setAccountId(accountId);
    config.setServiceId(serviceId);
    config.setEnvId(envId);
    config.setAppId(appId);
    config.setEnabled24x7(false);
    config.setUuid(cvConfigId + "-disabled");
    config.setName("NewRelic-disabled");
    config.setStateType(StateType.NEW_RELIC);
    wingsPersistence.save(config);

    PageRequest<String> request = PageRequestBuilder.aPageRequest().withOffset("0").build();

    // test behavior
    PageResponse<CVEnabledService> cvConfigs = accountService.getServices(accountId, user, request, null);

    // verify results
    assertThat(cvConfigs.getResponse().size() == 1).isTrue();
    assertThat(cvConfigs.getResponse().get(0).getCvConfig().size() == 1).isTrue();
    assertThat(cvConfigs.getResponse().get(0).getService().getUuid()).isEqualTo(serviceId);
    assertThat("1").isEqualTo(cvConfigs.getOffset());
    assertThat(cvConfigs.getResponse().get(0).getService().getName()).isEqualTo("serviceTest");
    assertThat(cvConfigs.getResponse().get(0).getCvConfig().get(0).getName()).isEqualTo("NewRelic");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServicesForAccountSpecificService() {
    // setup
    setupCvServicesTests(accountId, serviceId + "-test", envId, appId, cvConfigId + "-test", workflowId, user);
    setupCvServicesTests(accountId, serviceId, envId, appId, cvConfigId, workflowId, user);
    PageRequest<String> request = PageRequestBuilder.aPageRequest().withOffset("0").build();

    // test behavior
    PageResponse<CVEnabledService> cvConfigs = accountService.getServices(accountId, user, request, serviceId);

    // verify results
    assertThat(cvConfigs.getResponse().size() == 1).isTrue();
    assertThat(cvConfigs.getResponse().get(0).getService().getUuid()).isEqualTo(serviceId);
    assertThat("1").isEqualTo(cvConfigs.getOffset());
    assertThat(cvConfigs.getResponse().get(0).getService().getName()).isEqualTo("serviceTest");
    assertThat(cvConfigs.getResponse().get(0).getCvConfig().get(0).getName()).isEqualTo("NewRelic");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetServicesForAccountLastOffset() {
    // setup
    setupCvServicesTests(accountId, serviceId, envId, appId, cvConfigId, workflowId, user);
    PageRequest<String> request = PageRequestBuilder.aPageRequest().withOffset("1").build();

    // test behavior
    PageResponse<CVEnabledService> services = accountService.getServices(accountId, user, request, null);

    // verify results
    assertThat(services.getResponse().size() == 0).isTrue();
  }

  private void setupCvServicesTests(
      String accountId, String serviceId, String envId, String appId, String cvConfigId, String workflowId, User user) {
    CVConfiguration config = NewRelicCVServiceConfiguration.builder().build();
    config.setAccountId(accountId);
    config.setServiceId(serviceId);
    config.setEnvId(envId);
    config.setAppId(appId);
    config.setEnabled24x7(true);
    config.setUuid(cvConfigId);
    config.setName("NewRelic");
    config.setStateType(StateType.NEW_RELIC);

    wingsPersistence.save(Environment.Builder.anEnvironment().appId(appId).uuid(envId).build());
    wingsPersistence.save(Service.builder().name("serviceTest").appId(appId).uuid(serviceId).build());
    wingsPersistence.save(Application.Builder.anApplication().uuid(appId).name("appName").build());
    wingsPersistence.save(config);
    when(mockUserPermissionInfo.getAppPermissionMapInternal()).thenReturn(new HashMap<String, AppPermissionSummary>() {
      { put(appId, buildAppPermissionSummary(serviceId, workflowId, envId)); }
    });

    when(authService.getUserPermissionInfo(accountId, user, false)).thenReturn(mockUserPermissionInfo);
  }

  private AppPermissionSummary buildAppPermissionSummary(String serviceId, String workflowId, String envId) {
    Map<Action, Set<String>> servicePermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet(serviceId, serviceId + "-test")); }
    };
    Map<Action, Set<EnvInfo>> envPermissions = new HashMap<Action, Set<EnvInfo>>() {
      {
        put(Action.READ, Sets.newHashSet(EnvInfo.builder().envId(envId).envType(EnvironmentType.PROD.name()).build()));
      }
    };
    Map<Action, Set<String>> pipelinePermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet()); }
    };
    Map<Action, Set<String>> workflowPermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet(workflowId)); }
    };

    return AppPermissionSummary.builder()
        .servicePermissions(servicePermissions)
        .envPermissions(envPermissions)
        .workflowPermissions(workflowPermissions)
        .pipelinePermissions(pipelinePermissions)
        .build();
  }

  /**
   * Tests if function generates unique unique account names after checking for duplicates in db
   */
  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testSuggestedAccountName() {
    // Add account
    wingsPersistence.save(anAccount()
                              .withUuid(UUID.randomUUID().toString())
                              .withCompanyName(HARNESS_NAME)
                              .withAccountName(HARNESS_NAME)
                              .build());

    // Check unique suggested account name
    String suggestion1 = accountService.suggestAccountName(HARNESS_NAME);
    assertThat(suggestion1).isNotEqualTo(HARNESS_NAME);

    // Add suggested acccount name
    wingsPersistence.save(anAccount()
                              .withUuid(UUID.randomUUID().toString())
                              .withCompanyName(HARNESS_NAME)
                              .withAccountName(suggestion1)
                              .build());

    // Check for unique suggestions
    String suggestion2 = accountService.suggestAccountName(HARNESS_NAME);
    assertThat(suggestion2).isNotEqualTo(HARNESS_NAME);
    assertThat(suggestion2).isNotEqualTo(suggestion1);
  }

  @Test
  @Owner(developers = LAZAR)
  @Category(UnitTests.class)
  public void testSuggestedAccountName_alreadyUnique() {
    final String UNIQUE_NAME = HARNESS_NAME + "_UNIQUE";
    // Add account
    wingsPersistence.save(anAccount()
                              .withUuid(UUID.randomUUID().toString())
                              .withCompanyName(HARNESS_NAME)
                              .withAccountName(HARNESS_NAME)
                              .build());

    // Check unique suggested account name
    String suggestion1 = accountService.suggestAccountName(UNIQUE_NAME);
    assertThat(suggestion1).isEqualTo(UNIQUE_NAME);
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void test_updateWhitelistedDomains_invalidDomainNames() {
    Account account = saveAccount("Company 1");

    try {
      accountService.updateWhitelistedDomains(account.getUuid(), Sets.newHashSet("test", "123", "999.99", " "));
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo("Invalid domain name");
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void test_updateWhitelistedDomains_shouldTrimStringsAndIgnoreWhiteSpace() {
    String companyName = "CompanyName 1";
    Account account = saveAccount(companyName);

    accountService.updateWhitelistedDomains(
        account.getUuid(), Sets.newHashSet(" harness.io", "harness.io ", " harness.io \t\t\t \t \t"));
    account = accountService.get(account.getUuid());
    assertThat(account.getWhitelistedDomains()).isEqualTo(Sets.newHashSet("harness.io"));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testAuthCheckUpdationOfWhitelistDomainNegative() {
    String companyName = "CompanyName 1";
    Account account = saveAccount(companyName);

    UserRequestContext userRequestContext = UserRequestContext.builder().build();

    User user = User.Builder.anUser()
                    .userRequestContext(userRequestContext)
                    .uuid(generateUuid())
                    .accounts(Arrays.asList(account))
                    .build();

    try {
      UserThreadLocal.set(user);
      assertThat(UserThreadLocal.get()).isNotNull();
      assertThat(UserThreadLocal.get()).isEqualTo(user);

      accountService.updateWhitelistedDomains(
          account.getUuid(), Sets.newHashSet(" harness.io", "harness.io ", " harness.io \t\t\t \t \t"));
      verify(authHandler, times(0)).authorizeAccountPermission(any(), any());
    } catch (InvalidRequestException ire) {
      assertThat(ire).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testAuthCheckUpdationOfWhitelistDomainNegative() {
    String companyName = "CompanyName 1";
    Account account = saveAccount(companyName);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder()
            .accountPermissionSummary(
                AccountPermissionSummary.builder()
                    .permissions(Sets.newHashSet(PermissionAttribute.PermissionType.USER_PERMISSION_READ))
                    .build())
            .build();

    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = User.Builder.anUser()
                    .userRequestContext(userRequestContext)
                    .uuid(generateUuid())
                    .accounts(Arrays.asList(account))
                    .build();

    try {
      UserThreadLocal.set(user);
      assertThat(UserThreadLocal.get()).isNotNull();
      assertThat(UserThreadLocal.get()).isEqualTo(user);
      accountService.updateWhitelistedDomains(
          account.getUuid(), Sets.newHashSet(" harness.io", "harness.io ", " harness.io \t\t\t \t \t"));
      verify(authHandler, times(0)).authorizeAccountPermission(any(), any());
    } catch (InvalidRequestException ire) {
      assertThat(ire).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthCheckUpdationOfWhitelistDomainPositive() {
    String companyName = "CompanyName 1";
    Account account = saveAccount(companyName);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder()
            .accountPermissionSummary(
                AccountPermissionSummary.builder()
                    .permissions(Sets.newHashSet(PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT))
                    .build())
            .build();

    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = User.Builder.anUser()
                    .userRequestContext(userRequestContext)
                    .uuid(generateUuid())
                    .accounts(Arrays.asList(account))
                    .build();

    try {
      UserThreadLocal.set(user);
      assertThat(UserThreadLocal.get()).isNotNull();
      assertThat(UserThreadLocal.get()).isEqualTo(user);

      accountService.updateWhitelistedDomains(
          account.getUuid(), Sets.newHashSet(" harness.io", "harness.io ", " harness.io \t\t\t \t \t"));
      account = accountService.get(account.getUuid());
      assertThat(account).isNotNull();
      assertThat(account.getWhitelistedDomains()).isNotNull();
      assertThat(account.getWhitelistedDomains()).isEqualTo(Sets.newHashSet("harness.io"));
    } catch (InvalidRequestException ire) {
      assertThat(ire).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_updateAccountName() {
    String companyName = "CompanyName 1";
    Account account = saveAccount(companyName);
    String newAccountName = "New Account Name";
    accountService.updateAccountName(account.getUuid(), newAccountName, null);
    account = accountService.get(account.getUuid());
    assertThat(account.getAccountName()).isEqualTo(newAccountName);
    assertThat(account.getCompanyName()).isEqualTo(companyName);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_updateAccountNameWithNoCompanyNameParam() {
    String companyName = "CompanyName 1";
    Account account = saveAccount(companyName);
    String newAccountName = "New Account Name";
    boolean accountUpdated = accountService.updateAccountName(account.getUuid(), newAccountName);
    assertThat(accountUpdated).isTrue();
    account = accountService.get(account.getUuid());
    assertThat(account.getAccountName()).isEqualTo(newAccountName);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_updateCompanyName() {
    String companyName = "CompanyName 1";
    Account account = saveAccount(companyName);
    String newCompanyName = "CompanyName 2";
    boolean accountUpdated = accountService.updateCompanyName(account.getUuid(), newCompanyName);
    assertThat(accountUpdated).isTrue();
    account = accountService.get(account.getUuid());
    assertThat(account.getCompanyName()).isEqualTo(newCompanyName);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_enableHarnessUserGroupAccess() {
    String accountId = UUIDGenerator.generateUuid();
    Account account = anAccount()
                          .withUuid(accountId)
                          .withCompanyName("CompanyName")
                          .withAccountName("Account Name 1")
                          .withAccountKey("ACCOUNT_KEY")
                          .withLicenseInfo(getLicenseInfo())
                          .withWhitelistedDomains(new HashSet<>())
                          .build();
    account.setHarnessSupportAccessAllowed(false);
    Account savedAccount = accountService.save(account, false);

    assertThat(savedAccount.isHarnessSupportAccessAllowed()).isFalse();

    boolean accountUpdated = accountService.enableHarnessUserGroupAccess(account.getUuid());
    assertThat(accountUpdated).isTrue();
    savedAccount = accountService.get(savedAccount.getUuid());
    assertThat(savedAccount.isHarnessSupportAccessAllowed()).isTrue();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_disableHarnessUserGroupAccess() {
    String accountId = UUIDGenerator.generateUuid();
    Account account = anAccount()
                          .withUuid(accountId)
                          .withCompanyName("CompanyName")
                          .withAccountName("Account Name 1")
                          .withAccountKey("ACCOUNT_KEY")
                          .withLicenseInfo(getLicenseInfo())
                          .withWhitelistedDomains(new HashSet<>())
                          .build();
    Account savedAccount = accountService.save(account, false);

    assertThat(savedAccount.isHarnessSupportAccessAllowed()).isTrue();

    boolean accountUpdated = accountService.disableHarnessUserGroupAccess(account.getUuid());
    assertThat(accountUpdated).isTrue();
    savedAccount = accountService.get(savedAccount.getUuid());
    assertThat(savedAccount.isHarnessSupportAccessAllowed()).isFalse();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void test_isRestrictedAccessEnabled() {
    String accountId = UUIDGenerator.generateUuid();
    Account account = anAccount()
                          .withUuid(accountId)
                          .withCompanyName("CompanyName")
                          .withAccountName("Account Name 1")
                          .withAccountKey("ACCOUNT_KEY")
                          .withLicenseInfo(getLicenseInfo())
                          .withWhitelistedDomains(new HashSet<>())
                          .build();
    account.setHarnessSupportAccessAllowed(false);
    Account savedAccount = accountService.save(account, false);

    assertThat(accountService.isRestrictedAccessEnabled(savedAccount.getUuid())).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAccountIteration() throws IllegalAccessException {
    final Account account = anAccount().withCompanyName(generateUuid()).build();
    long serviceGuardDataCollectionIteration = random.nextLong();
    FieldUtils.writeField(
        account, AccountKeys.serviceGuardDataCollectionIteration, serviceGuardDataCollectionIteration, true);
    long serviceGuardDataAnalysisIteration = random.nextLong();
    FieldUtils.writeField(
        account, AccountKeys.serviceGuardDataAnalysisIteration, serviceGuardDataAnalysisIteration, true);
    long workflowDataCollectionIteration = random.nextLong();
    FieldUtils.writeField(account, AccountKeys.workflowDataCollectionIteration, workflowDataCollectionIteration, true);

    assertThat(account.obtainNextIteration(AccountKeys.serviceGuardDataCollectionIteration))
        .isEqualTo(serviceGuardDataCollectionIteration);
    assertThat(account.obtainNextIteration(AccountKeys.serviceGuardDataAnalysisIteration))
        .isEqualTo(serviceGuardDataAnalysisIteration);
    assertThat(account.obtainNextIteration(AccountKeys.workflowDataCollectionIteration))
        .isEqualTo(workflowDataCollectionIteration);

    serviceGuardDataCollectionIteration = random.nextLong();
    account.updateNextIteration(AccountKeys.serviceGuardDataCollectionIteration, serviceGuardDataCollectionIteration);
    assertThat(account.obtainNextIteration(AccountKeys.serviceGuardDataCollectionIteration))
        .isEqualTo(serviceGuardDataCollectionIteration);

    serviceGuardDataAnalysisIteration = random.nextLong();
    account.updateNextIteration(AccountKeys.serviceGuardDataAnalysisIteration, serviceGuardDataAnalysisIteration);
    assertThat(account.obtainNextIteration(AccountKeys.serviceGuardDataAnalysisIteration))
        .isEqualTo(serviceGuardDataAnalysisIteration);

    workflowDataCollectionIteration = random.nextLong();
    account.updateNextIteration(AccountKeys.workflowDataCollectionIteration, workflowDataCollectionIteration);
    assertThat(account.obtainNextIteration(AccountKeys.workflowDataCollectionIteration))
        .isEqualTo(workflowDataCollectionIteration);

    try {
      account.updateNextIteration(generateUuid(), random.nextLong());
      fail("Did not throw exception");
    } catch (IllegalArgumentException e) {
      // expected
    }

    try {
      account.obtainNextIteration(generateUuid());
      fail("Did not throw exception");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testUpdateCloudCostEnabled() {
    Account account = accountService.save(anAccount()
                                              .withCompanyName("CompanyName 1")
                                              .withAccountName("Account Name 1")
                                              .withAccountKey("ACCOUNT_KEY")
                                              .withLicenseInfo(getLicenseInfo())
                                              .withWhitelistedDomains(new HashSet<>())
                                              .build(),
        false);
    Boolean result = accountService.updateCloudCostEnabled(account.getUuid(), true);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testIsSSOEnabled() {
    Account account = accountService.save(anAccount()
                                              .withCompanyName("CompanyName 1")
                                              .withAccountName("Account Name 1")
                                              .withAccountKey("ACCOUNT_KEY")
                                              .withAuthenticationMechanism(AuthenticationMechanism.LDAP)
                                              .withLicenseInfo(getLicenseInfo())
                                              .withWhitelistedDomains(new HashSet<>())
                                              .build(),
        false);
    Boolean result = accountService.isSSOEnabled(account);
    assertThat(result).isTrue();

    Account userPassAccount =
        accountService.save(anAccount()
                                .withCompanyName("CompanyName 1")
                                .withAccountName("Account Name 2")
                                .withAccountKey("ACCOUNT_KEY")
                                .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                                .withLicenseInfo(getLicenseInfo())
                                .withWhitelistedDomains(new HashSet<>())
                                .build(),
            false);
    Boolean isSSO = accountService.isSSOEnabled(userPassAccount);
    assertThat(isSSO).isFalse();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testValidateSubdomainUrl() {
    SubdomainUrl validUrl = new SubdomainUrl("https://domain.io");
    SubdomainUrl invalidUrl1 = new SubdomainUrl("domain.com");
    SubdomainUrl invalidUrl2 = new SubdomainUrl("http://domain.com");
    SubdomainUrl invalidUrl3 = new SubdomainUrl("https:// domain.com");

    Boolean result1 = accountService.validateSubdomainUrl(validUrl);
    assertThat(result1).isTrue();

    Boolean result2 = accountService.validateSubdomainUrl(invalidUrl1);
    assertThat(result2).isFalse();

    Boolean result3 = accountService.validateSubdomainUrl(invalidUrl2);
    assertThat(result3).isFalse();

    Boolean result4 = accountService.validateSubdomainUrl(invalidUrl3);
    assertThat(result4).isFalse();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testSetSubdomainUrl() {
    Account account = accountService.save(anAccount()
                                              .withCompanyName("CompanyName 1")
                                              .withAccountName("Account Name 1")
                                              .withSubdomainUrl("https://initialDomain.com")
                                              .withLicenseInfo(getLicenseInfo())
                                              .build(),
        false);
    SubdomainUrl subdomainUrl = new SubdomainUrl("https://domain.com");
    accountService.setSubdomainUrl(account, subdomainUrl);
    assertThat(wingsPersistence.get(Account.class, account.getUuid()).getSubdomainUrl())
        .isEqualTo(subdomainUrl.getUrl());
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testAddSubdomainUrl() {
    Account account1 = accountService.save(anAccount()
                                               .withCompanyName("CompanyName 1")
                                               .withAccountName("Account Name 1")
                                               .withSubdomainUrl("https://initialDomain.com")
                                               .withLicenseInfo(getLicenseInfo())
                                               .build(),
        false);
    Account account2 = accountService.save(anAccount()
                                               .withCompanyName("CompanyName 2")
                                               .withAccountName("Account Name 2")
                                               .withLicenseInfo(getLicenseInfo())
                                               .build(),
        false);
    SubdomainUrl validUrl = new SubdomainUrl("https://domain.io");
    SubdomainUrl invalidUrl = new SubdomainUrl("domain.com");
    SubdomainUrl duplicateUrl = new SubdomainUrl("https://initialDomain.com");
    User user1 = User.Builder.anUser().uuid("userId1").name("name1").email("user1@harness.io").build();
    User user2 = User.Builder.anUser().uuid("userId2").name("name2").email("user2@harness.io").build();
    when(harnessUserGroupService.isHarnessSupportUser("userId1")).thenReturn(Boolean.FALSE);
    when(harnessUserGroupService.isHarnessSupportUser("userId2")).thenReturn(Boolean.TRUE);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> accountService.addSubdomainUrl(user1.getUuid(), account1.getUuid(), validUrl));
    assertThatExceptionOfType(UnauthorizedException.class)
        .isThrownBy(() -> accountService.addSubdomainUrl(user1.getUuid(), account2.getUuid(), validUrl));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> accountService.addSubdomainUrl(user2.getUuid(), account2.getUuid(), invalidUrl));
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> accountService.addSubdomainUrl(user2.getUuid(), account2.getUuid(), duplicateUrl));

    Boolean result1 = accountService.addSubdomainUrl(user2.getUuid(), account2.getUuid(), validUrl);
    assertThat(wingsPersistence.get(Account.class, account2.getUuid()).getSubdomainUrl()).isEqualTo(validUrl.getUrl());
    assertThat(result1).isTrue();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testUpdatePovFlag() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setAccountType(AccountType.TRIAL);
    licenseInfo.setLicenseUnits(100);
    licenseInfo.setExpireAfterDays(15);

    Account account = anAccount()
                          .withCompanyName("CompanyName 1")
                          .withAccountName("Account Name 1")
                          .withLicenseInfo(licenseInfo)
                          .withUuid(accountId)
                          .build();

    wingsPersistence.save(account);

    boolean updatePovFlag = accountService.updatePovFlag(accountId, true);
    account = wingsPersistence.get(Account.class, accountId);
    assertThat(updatePovFlag).isTrue();
    assertThat(account).isNotNull();
    assertThat(account.isPovAccount()).isTrue();

    updatePovFlag = accountService.updatePovFlag(accountId, false);
    assertThat(updatePovFlag).isTrue();
    account = wingsPersistence.get(Account.class, accountId);
    assertThat(updatePovFlag).isTrue();
    assertThat(account).isNotNull();
    assertThat(account.isPovAccount()).isFalse();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testUpdatePovFlag_ForPaidAccount() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setLicenseUnits(100);
    licenseInfo.setExpireAfterDays(15);

    Account account = anAccount()
                          .withCompanyName("CompanyName 1")
                          .withAccountName("Account Name 1")
                          .withLicenseInfo(licenseInfo)
                          .withUuid(accountId)
                          .build();

    wingsPersistence.save(account);

    boolean updatePovFlag = accountService.updatePovFlag(accountId, true);
    account = wingsPersistence.get(Account.class, accountId);
    assertThat(updatePovFlag).isFalse();
    assertThat(account).isNotNull();
    assertThat(account.isPovAccount()).isFalse();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testUpdatePovFlag_ForNullLicenseInfo() {
    Account account =
        anAccount().withCompanyName("CompanyName 1").withAccountName("Account Name 1").withUuid(accountId).build();

    wingsPersistence.save(account);

    boolean updatePovFlag = accountService.updatePovFlag(accountId, true);
    account = wingsPersistence.get(Account.class, accountId);
    assertThat(updatePovFlag).isFalse();
    assertThat(account).isNotNull();
    assertThat(account.isPovAccount()).isFalse();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testUpdatePovFlag_WhenAccountNotPresent() {
    boolean updatePovFlag = accountService.updatePovFlag(accountId, false);
    assertThat(updatePovFlag).isFalse();
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void testGetAccountsWithDisabledHarnessUserGroupAccess() {
    Account account1 = anAccount()
                           .withCompanyName("CompanyName 1")
                           .withAccountName("Account Name 1")
                           .withLicenseInfo(getLicenseInfo())
                           .withUuid("111")
                           .withHarnessGroupAccessAllowed(true)
                           .build();

    wingsPersistence.save(account1);

    Account account2 = anAccount()
                           .withCompanyName("CompanyName 2")
                           .withAccountName("Account Name 2")
                           .withLicenseInfo(getLicenseInfo())
                           .withUuid("222")
                           .withHarnessGroupAccessAllowed(false)
                           .build();

    wingsPersistence.save(account2);

    // account without isHarnessSupportAccessAllowed flag
    Account account3 = anAccount()
                           .withCompanyName("CompanyName 3")
                           .withAccountName("Account Name 3")
                           .withLicenseInfo(getLicenseInfo())
                           .withUuid("333")
                           .build();

    wingsPersistence.save(account3);

    Set<String> restrictedAccounts = accountService.getAccountsWithDisabledHarnessUserGroupAccess();

    assertThat(restrictedAccounts.size()).isEqualTo(1);
    assertThat(restrictedAccounts.contains("222")).isTrue();
    assertThat(restrictedAccounts.contains("333")).isFalse();
    assertThat(restrictedAccounts.contains("111")).isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpdateServiceGuardAccountLimit_WhenNotHarnessUser() {
    when(accountPermissionUtils.checkIfHarnessUser(anyString()))
        .thenReturn(new RestResponse<>("user is not authorized"));
    RestResponse<String> restResponse = accountResource.setServiceGuardAccountLimit(
        accountId, ServiceGuardLimitDTO.builder().serviceGuardLimit(10).build());
    assertThat(restResponse.getResponseMessages()).isEmpty();
    assertThat(restResponse.getResource()).isEqualTo("user is not authorized");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpdateServiceGuardAccountLimit() {
    when(accountPermissionUtils.checkIfHarnessUser(anyString())).thenReturn(null);
    Account account = saveAccount(generateUuid());
    Account savedAccount = wingsPersistence.get(Account.class, account.getUuid());
    assertThat(savedAccount.getServiceGuardLimit()).isEqualTo(SERVICE_GUAARD_LIMIT);
    RestResponse<String> restResponse = accountResource.setServiceGuardAccountLimit(
        account.getUuid(), ServiceGuardLimitDTO.builder().serviceGuardLimit(25).build());
    assertThat(restResponse.getResource()).isEqualTo("success");
    savedAccount = wingsPersistence.get(Account.class, account.getUuid());
    assertThat(savedAccount.getServiceGuardLimit()).isEqualTo(25L);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateUpdateAccountPreference() {
    Account account = saveAccount(generateUuid());
    boolean updated =
        accountService.updateAccountPreference(account.getUuid(), "delegateSecretsCacheTTLInHours", new Integer(2));
    Account savedAccount = wingsPersistence.get(Account.class, account.getUuid());
    assertThat(updated).isTrue();
    assertThat(savedAccount.getAccountPreferences()).isNotNull();
    assertThat(savedAccount.getAccountPreferences().getDelegateSecretsCacheTTLInHours()).isNotNull();
    assertThat(savedAccount.getAccountPreferences().getDelegateSecretsCacheTTLInHours()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testShouldNotUpdateAccountPreferenceIfPreferenceKeyDoesntExist() {
    Account account = saveAccount(generateUuid());
    boolean updated = accountService.updateAccountPreference(account.getUuid(), "thisKeyDoesntExist", new Integer(2));
    assertThat(updated).isFalse();
  }
}
