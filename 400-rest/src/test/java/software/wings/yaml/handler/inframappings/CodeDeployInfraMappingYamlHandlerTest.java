/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.inframappings;

import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.CodeDeployInfrastructureMapping.Yaml;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.inframapping.CodeDeployInfraMappingYamlHandler;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class CodeDeployInfraMappingYamlHandlerTest extends BaseInfraMappingYamlHandlerTestBase {
  private String validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: AWS_AWS_CODEDEPLOY\n"
      + "applicationName: CustomTodolist\n"
      + "computeProviderName: aws\n"
      + "computeProviderType: AWS\n"
      + "deploymentConfig: Custom-todolistDC\n"
      + "deploymentGroup: Custom-DeploymentGroup\n"
      + "deploymentType: AWS_CODEDEPLOY\n"
      + "hostNameConvention: '''harness-'' + ${host.ec2Instance.instanceId}'\n"
      + "infraMappingType: AWS_AWS_CODEDEPLOY\n"
      + "region: us-east-1\n"
      + "serviceName: SERVICE_NAME";

  @InjectMocks @Inject private CodeDeployInfraMappingYamlHandler yamlHandler;

  @InjectMocks @Inject private InfrastructureMappingService infrastructureMappingService;

  private String validYamlFilePath =
      "Setup/Applications/APP_NAME/Environments/ENV_NAME/Service Infrastructure/code-deploy.yaml";
  private String infraMappingName = "code-deploy";

  @Before
  public void runBeforeTest() {
    setup(validYamlFilePath, infraMappingName);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext(validYamlContent, validYamlFilePath, yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    CodeDeployInfrastructureMapping infrastructureMapping =
        yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(infrastructureMapping).isNotNull();
    assertThat(infraMappingName).isEqualTo(infrastructureMapping.getName());

    Yaml yaml = yamlHandler.toYaml(infrastructureMapping, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.name());

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    InfrastructureMapping infrastructureMapping2 =
        infrastructureMappingService.getInfraMappingByName(APP_ID, ENV_ID, infraMappingName);
    // TODO find out why this couldn't be called
    //    Workflow savedWorkflow = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(infrastructureMapping2).isNotNull().hasFieldOrPropertyWithValue("name", infraMappingName);

    yamlHandler.delete(changeContext);

    CodeDeployInfrastructureMapping afterDelete = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(afterDelete).isNull();
  }
}
