/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.secret.SecretSanitizerThreadLocal;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ExceptionMessageSanitizerTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExceptionMessageSanitizer() {
    IOException ex1 = new IOException("hello there is an error");
    Exception ex = new Exception("hello error", ex1);
    Set<String> secrets = new HashSet<>();
    secrets.add("error");
    secrets.add("hello");
    ExceptionMessageSanitizer.sanitizeException(ex, secrets);
    assertThat(ex.getCause().getMessage()).isEqualTo("************** there is an **************");
    assertThat(ex.getMessage()).isEqualTo("************** **************");
    assertThat(ex.getClass()).isEqualTo(Exception.class);
    assertThat(ex.getCause().getClass()).isEqualTo(IOException.class);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExceptionMessageSanitizerThreadLocalAdd() {
    IOException ex1 = new IOException("hello there is an error");
    Exception ex = new Exception("hello error", ex1);
    SecretSanitizerThreadLocal.add("error");
    SecretSanitizerThreadLocal.add("hello");
    ExceptionMessageSanitizer.sanitizeException(ex);
    assertThat(ex.getCause().getMessage()).isEqualTo("************** there is an **************");
    assertThat(ex.getMessage()).isEqualTo("************** **************");
    assertThat(ex.getClass()).isEqualTo(Exception.class);
    assertThat(ex.getCause().getClass()).isEqualTo(IOException.class);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExceptionMessageSanitizerThreadLocalAddAll() {
    IOException ex1 = new IOException("hello there is an error");
    Exception ex = new Exception("hello error", ex1);
    Set<String> secrets = new HashSet<>();
    secrets.add("error");
    secrets.add("hello");
    SecretSanitizerThreadLocal.addAll(secrets);
    ExceptionMessageSanitizer.sanitizeException(ex);
    assertThat(ex.getCause().getMessage()).isEqualTo("************** there is an **************");
    assertThat(ex.getMessage()).isEqualTo("************** **************");
    assertThat(ex.getClass()).isEqualTo(Exception.class);
    assertThat(ex.getCause().getClass()).isEqualTo(IOException.class);
  }
}
