package io.harness.ng.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rule.Owner;
import io.harness.scim.ScimUser;

import software.wings.beans.Account;
import software.wings.beans.UserInvite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class NGScimUserServiceImplTest extends NgManagerTestBase {
  private NgUserService ngUserService;
  private UserGroupService userGroupService;
  private InviteService inviteService;
  private NGScimUserServiceImpl scimUserService;

  @Before
  public void setup() throws IllegalAccessException {
    inviteService = mock(InviteService.class);
    ngUserService = mock(NgUserService.class);
    userGroupService = mock(UserGroupService.class);

    scimUserService = new NGScimUserServiceImpl(ngUserService, inviteService, userGroupService);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsPartOfAccountAlready_ItShouldUpdate() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    UserInfo userInfo = UserInfo.builder().admin(true).email("username@harness.io").name("display_name").build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    assertThat(((ScimUser) response.getEntity()).getUserName()).isEqualTo(userInfo.getEmail());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsNotPartOfAccountAlready_ItShouldAddToAccount_AlreadyPresent() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    UserInfo userInfo = UserInfo.builder().admin(true).email("username@harness.io").name("display_name").build();

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(null));
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsNotPartOfAccountAlready_ItShouldAddToAccount() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    List<GatewayAccountRequestDTO> accounts = new ArrayList<>();
    accounts.add(GatewayAccountRequestDTO.builder().uuid(account.getUuid()).build());
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").accounts(accounts).name("display_name").build();

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    assertThat(((ScimUser) response.getEntity()).getUserName()).isEqualTo(userInfo.getEmail());
  }
}
