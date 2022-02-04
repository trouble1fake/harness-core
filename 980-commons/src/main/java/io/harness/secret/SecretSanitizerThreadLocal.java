/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.secret;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import java.util.HashSet;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretSanitizerThreadLocal {
  public static final ThreadLocal<Set<String>> secretsThreadLocal = new ThreadLocal<>();

  /**
   *
   * @param secrets
   */
  public static void set(final Set<String> secrets) {
    secretsThreadLocal.set(secrets);
  }

  public static void addAll(final Set<String> secrets) {
    if (isEmpty(secrets)) {
      return;
    }
    if (secretsThreadLocal.get() == null) {
      secretsThreadLocal.set(new HashSet<>());
    }
    secretsThreadLocal.get().addAll(secrets);
  }

  public static void add(final String secret) {
    if (isEmpty(secret)) {
      return;
    }
    if (secretsThreadLocal.get() == null) {
      secretsThreadLocal.set(new HashSet<>());
    }
    secretsThreadLocal.get().add(secret);
  }

  /**
   * Unset.
   */
  public static void unset() {
    secretsThreadLocal.remove();
  }

  /**
   *
   * @return
   */
  public static Set<String> get() {
    return secretsThreadLocal.get();
  }
}
