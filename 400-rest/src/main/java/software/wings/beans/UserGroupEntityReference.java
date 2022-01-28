package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(PL)
public class UserGroupEntityReference {
  private String id;
  private String appId;
  private String accountId;
  private String entityType;
}
