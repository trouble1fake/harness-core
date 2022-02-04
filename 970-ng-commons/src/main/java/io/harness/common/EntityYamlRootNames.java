/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

/**
 * Name of the top element in yaml.
 * For example:
 * <p>connector:
 * name: testname
 * projectIdentifier: projectId
 * </p>
 * In this the top element is <b>connector</b>
 */
@OwnedBy(PL)
public class EntityYamlRootNames {
  public static String PROJECT = "project";
  public static String PIPELINE = "pipeline";
  public static String PIPELINE_STEP = "pipelineStep";
  public static String CONNECTOR = "connector";
  public static String SECRET = "secret";
  public static String SERVICE = "service";
  public static String ENVIRONMENT = "environment";
  public static String INPUT_SET = "inputSet";
  public static String OVERLAY_INPUT_SET = "overlayInputSet";
  public static String CV_CONFIG = "cvConfig";
  public static String VERIFY = "Verify";
  public static String DELEGATE = "delegate";
  public static String DELEGATE_CONFIGURATION = "delegateConfigurations";
  public static String CV_VERIFICATION_JOB = "cvVerificationJob";
  public static String INTEGRATION_STAGE = "integrationStage";
  public static String INTEGRATION_STEP = "integrationSteps";
  public static String CV_KUBERNETES_ACTIVITY_SOURCE = "cvKubernetesActivitySource";
  public static String DEPLOYMENT_STEP = "deploymentSteps";
  public static String DEPLOYMENT_STAGE = "deploymentStage";
  public static String FEATURE_FLAG_STAGE = "featureFlagStage";
  public static String APPROVAL_STAGE = "approvalStage";
  public static String TRIGGERS = "trigger";
  public static String MONITORED_SERVICE = "monitoredService";
  public static String TEMPLATE = "template";
  public static String GIT_REPOSITORY = "gitRepository";
  public static String FEATURE_FLAGS = "featureFlags";
  public static String HTTP = "Http";
  public static String JIRA_CREATE = "JiraCreate";
  public static String JIRA_UPDATE = "JiraUpdate";
  public static String SHELL_SCRIPT = "ShellScript";
  public static String K8S_CANARY_DEPLOY = "K8sCanaryDeploy";
  public static String SERVICENOW_APPROVAL = "ServiceNowApproval";
  public static String JIRA_APPROVAL = "JiraApproval";
  public static String HARNESS_APPROVAL = "HarnessApproval";
  public static String BARRIER = "Barrier";
  public static String OPAPOLICY = "governancePolicy";
}
