package io.harness.delegate.beans;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import lombok.Data;

@Data
@OwnedBy(DEL)
public class DelegateTokenCacheKey {
    private final String accountId;
    private final String delegateHostName;
}
