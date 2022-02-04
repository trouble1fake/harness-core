/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.reflection.ReflectionUtils.getFieldByName;

import static org.apache.commons.lang3.StringUtils.replaceEach;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretSanitizerThreadLocal;
import io.harness.security.encryption.EncryptedDataDetail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ExceptionMessageSanitizer {
  public static Exception sanitizeException(Exception ex, Set<String> secrets) {
    if (isEmpty(secrets)) {
      return ex;
    }
    Exception exception = ex;
    while (exception != null) {
      sanitizeExceptionInternal(exception, secrets);
      exception = (Exception) exception.getCause();
    }
    return ex;
  }

  public static Exception sanitizeException(Exception ex) {
    Set<String> secrets = SecretSanitizerThreadLocal.get();
    return sanitizeException(ex, secrets);
  }

  @SneakyThrows
  protected static void sanitizeExceptionInternal(Exception exception, Set<String> secrets) {
    String message = exception.getMessage();
    String updatedMessage = sanitizeMessage(message, secrets);
    updateExceptionMessage(exception, updatedMessage);
  }

  protected static String sanitizeMessage(String message, Set<String> secrets) {
    ArrayList<String> secretMasks = new ArrayList<>();
    ArrayList<String> secretValues = new ArrayList<>();
    for (String secret : secrets) {
      secretMasks.add(SECRET_MASK);
      secretValues.add(secret);
    }
    return replaceEach(message, secretValues.toArray(new String[] {}), secretMasks.toArray(new String[] {}));
  }

  protected static void updateExceptionMessage(Throwable exception, String message)
      throws NoSuchFieldException, IllegalAccessException {
    Field detailMessageField = Throwable.class.getDeclaredField("detailMessage");
    try {
      if (!detailMessageField.isAccessible()) {
        detailMessageField.setAccessible(true);
      }
      detailMessageField.set(exception, message);
    } finally {
      detailMessageField.setAccessible(false);
    }
  }

  public static void storeAllSecretsForSanitizing(
      DecryptableEntity object, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isEmpty(encryptedDataDetails)) {
      return;
    }
    for (EncryptedDataDetail encryptedDataDetail : encryptedDataDetails) {
      Field f = getFieldByName(object.getClass(), encryptedDataDetail.getFieldName());
      if (f == null) {
        log.warn("Could not find field {} in class {}", encryptedDataDetail.getFieldName(), object.getClass());
        continue;
      }
      try {
        f.setAccessible(true);
        if (f.getType() == SecretRefData.class) {
          SecretRefData secretRefData = (SecretRefData) f.get(object);
          SecretSanitizerThreadLocal.add(String.valueOf(secretRefData.getDecryptedValue()));
        } else {
          SecretSanitizerThreadLocal.add(String.valueOf((char[]) f.get(object)));
        }

        f.setAccessible(false);
      } catch (IllegalAccessException ex) {
        log.error("Error while trying to store secret", ex);
      }
    }
  }
}
