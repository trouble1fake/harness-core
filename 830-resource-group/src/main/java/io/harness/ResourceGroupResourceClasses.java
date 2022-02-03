package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.util.Collection;
import javax.ws.rs.Path;
import org.reflections.Reflections;

@OwnedBy(PL)
public class ResourceGroupResourceClasses {
  public static final String RESOURCE_PACKAGES = "io.harness.resourcegroup.framework.remote.resource";

  public static Collection<Class<?>> getResourceClasses() {
    final Reflections reflections = new Reflections(RESOURCE_PACKAGES);
    return reflections.getTypesAnnotatedWith(Path.class);
  }
}
