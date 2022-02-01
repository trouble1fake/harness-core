package io.harness.datahandler.services;

import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class AdminRingService {
  private final HPersistence persistence;

  public boolean updateDelegateImageTag(final String imageTag, final String ringName) {
    return updateRingKey(imageTag, ringName, DelegateRingKeys.delegateImageTag);
  }

  public boolean updateUpgraderImageTag(final String imageTag, final String ringName) {
    return updateRingKey(imageTag, ringName, DelegateRingKeys.upgraderImageTag);
  }

  public boolean updateDelegateVersion(final String version, final String ringName) {
    return updateRingKey(version, ringName, DelegateRingKeys.delegateVersions);
  }

  private boolean updateRingKey(final String ringKeyValue, final String ringName, final String ringKey) {
    final Query<DelegateRing> filter =
        persistence.createQuery(DelegateRing.class).filter(DelegateRingKeys.ringName, ringName);
    final UpdateOperations<DelegateRing> updateOperation =
        persistence.createUpdateOperations(DelegateRing.class).set(ringKey, ringKeyValue);
    final UpdateResults updateResults = persistence.update(filter, updateOperation);
    return updateResults.getUpdatedExisting();
  }
}
