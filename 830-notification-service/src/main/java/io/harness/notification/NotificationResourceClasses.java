package io.harness.notification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Collection;
import javax.ws.rs.Path;
import org.reflections.Reflections;

@OwnedBy(HarnessTeam.PL)
public class NotificationResourceClasses {
  public static final String RESOURCE_PACKAGES = "io.harness.notification.remote.resources";

  public static Collection<Class<?>> getResourceClasses() {
    final Reflections reflections = new Reflections(RESOURCE_PACKAGES);
    return reflections.getTypesAnnotatedWith(Path.class);
  }
}
