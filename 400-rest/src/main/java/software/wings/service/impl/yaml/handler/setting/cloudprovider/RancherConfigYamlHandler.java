/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting.cloudprovider;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.RancherConfig.Yaml;

import io.harness.beans.EncryptedData;
import io.harness.exception.HarnessException;

import software.wings.beans.RancherConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;

import java.util.List;

public class RancherConfigYamlHandler extends CloudProviderYamlHandler<Yaml, RancherConfig> {
  @Override
  public Yaml toYaml(SettingAttribute settingAttribute, String appId) {
    RancherConfig rancherConfig = (RancherConfig) settingAttribute.getValue();
    Yaml yaml = Yaml.builder().harnessApiVersion(getHarnessApiVersion()).build();
    yaml.setType(rancherConfig.getType());
    yaml.setRancherUrl(rancherConfig.getRancherUrl());
    if (rancherConfig.getEncryptedBearerToken() != null) {
      String encryptedYamlRef =
          secretManager.getEncryptedYamlRef(rancherConfig.getAccountId(), rancherConfig.getEncryptedBearerToken());
      yaml.setBearerToken(encryptedYamlRef);
    }
    toYaml(yaml, settingAttribute, appId);
    return yaml;
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  protected SettingAttribute toBean(SettingAttribute previous, ChangeContext<RancherConfig.Yaml> changeContext,
      List<ChangeContext> changeSetContext) throws HarnessException {
    String uuid = null;
    if (previous != null) {
      uuid = previous.getUuid();
    }

    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();

    RancherConfig rancherConfig = RancherConfig.builder().accountId(accountId).build();
    rancherConfig.setRancherUrl(yaml.getRancherUrl());
    if (isNotEmpty(yaml.getBearerToken())) {
      EncryptedData encryptedData = secretManager.getEncryptedDataFromYamlRef(yaml.getBearerToken(), accountId);
      rancherConfig.setEncryptedBearerToken(encryptedData.getUuid());
    }

    return buildSettingAttribute(accountId, changeContext.getChange().getFilePath(), uuid, rancherConfig);
  }
}
