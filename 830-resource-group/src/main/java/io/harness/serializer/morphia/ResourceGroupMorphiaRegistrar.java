package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.resourcegroup.commons.bootstrap.ConfigurationState;

import java.util.Set;

@OwnedBy(PL)
public class ResourceGroupMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(ConfigurationState.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes to register
  }
}