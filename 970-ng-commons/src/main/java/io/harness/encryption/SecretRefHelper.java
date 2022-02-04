/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretRefHelper {
  public SecretRefData createSecretRef(String secretConfigString) {
    return new SecretRefData(secretConfigString);
  }

  public SecretRefData createSecretRef(String secretConfigString, Scope scope, char[] decryptedValue) {
    return new SecretRefData(secretConfigString, scope, decryptedValue);
  }

  public String getSecretConfigString(SecretRefData secretRefData) {
    if (secretRefData == null) {
      return null;
    }
    return secretRefData.toSecretRefStringValue();
  }
}
