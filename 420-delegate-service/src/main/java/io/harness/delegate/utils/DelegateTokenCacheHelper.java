package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateTokenCacheKey;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(DEL)
public class DelegateTokenCacheHelper {
  @Inject private Cache<DelegateTokenCacheKey, DelegateToken> delegateTokenCache;

  public DelegateToken getDelegateToken(DelegateTokenCacheKey delegateTokenCacheKey) {
    return delegateTokenCache != null ? delegateTokenCache.get(delegateTokenCacheKey) : null;
  }

  public void addDelegateTokenToCache(DelegateTokenCacheKey delegateTokenCacheKey, DelegateToken delegateToken) {
    if (delegateToken != null && delegateTokenCache != null) {
      delegateTokenCache.put(delegateTokenCacheKey, delegateToken);
    }
  }

  public void invalidateCacheUsingAccountId(String accountId) {}

  // TODO: Question, is it able to insert for the very first time
  public void putIfTokenIsAbsent(DelegateTokenCacheKey delegateTokenCacheKey, DelegateToken delegateToken) {
    if (delegateTokenCache != null) {
      delegateTokenCache.putIfAbsent(delegateTokenCacheKey, delegateToken);
    }
  }

  public void invalidateCacheUsingKey(DelegateTokenCacheKey delegateTokenCacheKey) {
    log.info("Invalidating cache for accountId {} and delegateHostName {}", delegateTokenCacheKey.getAccountId(),
        delegateTokenCacheKey.getDelegateHostName());
    delegateTokenCache.remove(delegateTokenCacheKey);
  }
}
