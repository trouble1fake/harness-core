package io.harness.generator;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.utils.DelegateRingConstants.LATEST_DELEGATE_IMAGE;
import static io.harness.delegate.utils.DelegateRingConstants.LATEST_UPGRADER_IMAGE;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_1;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_2;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_3;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateRing;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;

@OwnedBy(DEL)
public class DelegateRingGenerator {
  private final HPersistence persistence;

  @Inject
  public DelegateRingGenerator(HPersistence persistence) {
    this.persistence = persistence;
  }

  public void createAllRings() {
    persistence.save(DelegateRing.builder()
                         .ringName(RING_NAME_1)
                         .delegateImageTag(LATEST_DELEGATE_IMAGE)
                         .upgraderImageTag(LATEST_UPGRADER_IMAGE)
                         .build());
    persistence.save(DelegateRing.builder()
                         .ringName(RING_NAME_2)
                         .delegateImageTag(LATEST_DELEGATE_IMAGE)
                         .upgraderImageTag(LATEST_UPGRADER_IMAGE)
                         .build());
    persistence.save(DelegateRing.builder()
                         .ringName(RING_NAME_3)
                         .delegateImageTag(LATEST_DELEGATE_IMAGE)
                         .upgraderImageTag(LATEST_UPGRADER_IMAGE)
                         .build());
  }
}
