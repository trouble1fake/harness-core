/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.setting.artifactserver;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.service.impl.yaml.handler.setting.SettingValueYamlHandler;
import software.wings.settings.SettingValue;
import software.wings.yaml.setting.HelmRepoYaml;

@OwnedBy(CDP)
public abstract class HelmRepoYamlHandler<Y extends HelmRepoYaml, B extends SettingValue>
    extends SettingValueYamlHandler<Y, B> {
  @Override
  public SettingAttribute get(String accountId, String yamlFilePath) {
    return yamlHelper.getArtifactServer(accountId, yamlFilePath);
  }

  protected SettingAttribute buildSettingAttribute(String accountId, String yamlFilePath, String uuid, B config) {
    return buildSettingAttribute(accountId, yamlFilePath, uuid, config, SettingCategory.HELM_REPO);
  }

  protected String getCloudProviderName(String appId, String cloudProviderId) {
    SettingAttribute settingAttribute = settingsService.get(appId, cloudProviderId);
    notNullCheck(format("Cloud Provider with id %s does not exist", cloudProviderId), settingAttribute);

    return settingAttribute.getName();
  }

  protected String getCloudProviderIdByName(String accountId, String cloudProviderName) {
    SettingAttribute settingAttributeByName = settingsService.getSettingAttributeByName(accountId, cloudProviderName);
    notNullCheck(format("Cloud Provider with name %s does not exist", cloudProviderName), settingAttributeByName);

    return settingAttributeByName.getUuid();
  }
}
