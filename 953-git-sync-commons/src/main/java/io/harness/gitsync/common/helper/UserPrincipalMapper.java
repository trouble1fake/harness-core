package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.UserPrincipal;

import com.google.protobuf.StringValue;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class UserPrincipalMapper {
  public UserPrincipal toProto(io.harness.security.dto.UserPrincipal userPrincipal) {
    return UserPrincipal.newBuilder()
        .setEmail(StringValue.of(userPrincipal.getEmail()))
        .setUserId(StringValue.of(userPrincipal.getName()))
        .setUserName(StringValue.of(userPrincipal.getUsername()))
        .build();
  }

  public io.harness.security.dto.UserPrincipal toRest(UserPrincipal userPrincipal, String accountId) {
    return new io.harness.security.dto.UserPrincipal(userPrincipal.getUserId().getValue(),
        userPrincipal.getEmail().getValue(), userPrincipal.getUserName().getValue(), accountId);
  }
}
