/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.EXPLANATION;
import static io.harness.eraro.ErrorCode.HINT;
import static io.harness.eraro.Level.INFO;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.EnumSet;

@OwnedBy(HarnessTeam.DX)
public class HintException extends WingsException {
  public static final String HINT_AWS_IRSA_CHECK = "Check IRSA role on delegate.";
  public static final String HINT_EMPTY_ACCESS_KEY = "Check if Access Key is empty";
  public static final String HINT_EMPTY_SECRET_KEY = "Check if Secret Key is empty";
  public static final String HINT_EMPTY_CROSS_ACCOUNT_ROLE_ARN_KEY = "Check if Cross account role ARN is empty";
  public static final String HINT_INCORRECT_ACCESS_KEY_SECRET_KEY_PERMISSIONS_KEY =
      "Check if access key, secret key are valid. Check if user has required permissions to perform the activity.";
  public static final String HINT_INVALID_CROSS_ACCOUNT_ROLE_ARN_EXTERNAL_ID_PERMISSIONS_KEY =
      "Check if Cross account role ARN, External Id are valid. Check if User / IAM Role on delegate / IRSA role has permission to perform sts:AssumeRole. Check if assumed Cross account role has permissions to perform the activity.";
  public static final String HINT_AWS_IAM_ROLE_CHECK = "Check IAM role on delegate ec2.";
  public static final String HINT_AWS_CLIENT_UNKNOWN_ISSUE = "Check AWS client on delegate";
  public static final String HINT_ECR_IMAGE_NAME = "Check if given ECR image is available in specified region";
  public static final String HINT_AWS_ACCESS_DENIED = "Please ensure AWS credentials are valid";
  public static final String HINT_UNEXPECTED_ERROR = "Please reach out to harness support team";
  public static final String HINT_GCP_ACCESS_DENIED = "Please ensure GCP credentials are valid";
  public static final String HINT_GCR_IMAGE_NAME = "Verify that the GCR image name is valid.";
  public static final String HINT_DOCKER_HUB_IMAGE_NAME =
      "Check if the Docker image you are looking for is in the Docker registry.";
  public static final String HINT_DOCKER_HUB_ACCESS_DENIED = "Please ensure DockerHub credentials are valid";
  public static final String HINT_NEXUS_ACCESS_DENIED = "Please ensure Nexus credentials are valid";
  public static final String HINT_ARTIFACTORY_ACCESS_DENIED = "Please ensure Artifactory credentials are valid";
  public static final String HINT_AWS_SM_ACCESS_DENIED = "Please ensure AWS Secret Manager credentials are valid";
  public static final String HINT_AWS_KMS_ACCESS_DENIED = "Please ensure AWS KMS secret key and accessKey are valid";
  public static final String HINT_AWS_SM_KMS_KEY = "Please ensure the provided KMS key is valid";
  public static final String HINT_AWS_SM_NEXTTOKEN = "Please ensure the provided NextToken value is valid";
  public static final String HINT_AWS_SM_PARAMETERS_NAME = "Please ensure the provided name of parameter is valid";
  public static final String HINT_AZURE_VAULT_SM_ACCESS_DENIED = "Please ensure Azure Vault credentials are valid";
  public static final String HINT_AZURE_VAULT_FETCH_FAILED =
      "Please ensure the client Id, Tenant Id, Subscription and Secret key is Valid.";
  public static final String HINT_AZURE_VAULT_SM_CRUD_DENIED =
      "Please ensure Azure Vault engine have valid permissions";
  public static final String HINT_AZURE_VAULT_SM_SUBSCRIPTION_ID_ERROR = "Please ensure Subscription ID is valid";
  public static final String HINT_HASHICORP_VAULT_SM_ACCESS_DENIED = "Please ensure Azure Vault credentials are valid";
  public static final String HINT_INVALID_TAG_REFER_LINK_GCR =
      "Please check if tag is available. Refer https://cloud.google.com/sdk/gcloud/reference/container/images/list-tags for more information";
  public static final String HINT_INVALID_IMAGE_REFER_LINK_GCR =
      "Please check if image is available. Refer https://cloud.google.com/sdk/gcloud/reference/container/images/list-tags for more information";
  public static final String HINT_INVALID_TAG_REFER_LINK_ECR =
      "Please check if tag is available. Refer https://docs.aws.amazon.com/cli/latest/reference/ecr/list-images.html for more information";
  public static final String HINT_INVALID_IMAGE_REFER_LINK_ECR =
      "Please check if image is available. Refer https://docs.aws.amazon.com/cli/latest/reference/ecr/list-images.html for more information";
  public static final String HINT_INVALID_TAG_REFER_LINK_DOCKER_HUB =
      "Please check if tag is available. Refer https://docs.docker.com/engine/reference/commandline/images/#list-images-by-name-and-tag for more information";
  public static final String HINT_INVALID_IMAGE_REFER_LINK_DOCKER_HUB =
      "Please check if image is available. Refer https://docs.docker.com/engine/reference/commandline/images/#list-images-by-name-and-tag for more information";
  public static final String HINT_INVALID_CONNECTOR =
      "Please ensure that connector %s is valid and using the correct Credentials.";
  public static final String DELEGATE_NOT_AVAILABLE =
      "Please make sure that your delegates are connected. Refer %s for more information on delegate Installation";
  public static final String HINT_ILLEGAL_IMAGE_PATH = "Please provide valid image path";
  public static final String HINT_HOST_UNREACHABLE = "Please ensure that registry host [%s] is reachable";

  public static final String HINT_INVALID_GIT_REPO = "Please provide valid git repository url";
  public static final String HINT_INVALID_GIT_HOST =
      "Please provide valid git repository url and ensure delegate to git provider connectivity";
  public static final String HINT_INVALID_GIT_AUTHORIZATION = "Please ensure that the credentials are correct.";
  public static final String HINT_INVALID_GIT_AUTHENTICATION =
      "Please ensure that the authentication is supported by git provider.";

  public static final String HINT_INVALID_GIT_API_AUTHORIZATION =
      "Please ensure that the api access credentials are correct.";

  public static final String HINT_MALFORMED_GIT_SSH_KEY = "Please provide valid git ssh private key in PEM format.";
  public static final String HINT_INVALID_GIT_SSH_KEY =
      "Please provide correct git ssh private key with 'git' username.";

  public static final String HINT_MISSING_BRANCH = "Please provide valid git branch";
  public static final String HINT_MISSING_REFERENCE = "Please provide valid git commit id or git tag";
  public static final String HINT_GIT_FILE_NOT_FOUND =
      "Please ensure that provided file path exists in git repository or in reference %s";

  public static final String HINT_CHECK_URL_DETAILS = "Please Check URL/Account details.";
  public static final String HINT_CHECK_AUTHORIZATION_DETAILS = "Check Authorization credentials.";

  public static final HintException MOVE_TO_THE_PARENT_OBJECT =
      new HintException("Navigate back to the parent object page and continue from there.");
  public static final HintException REFRESH_THE_PAGE = new HintException("Refresh the web page to update the data.");

  public HintException(String message) {
    super(message, null, HINT, INFO, null, null);
    super.excludeReportTarget(EXPLANATION, EnumSet.of(ReportTarget.LOG_SYSTEM));
    super.param("message", message);
  }

  public HintException(String message, Throwable cause) {
    super(message, cause, HINT, INFO, null, null);
    super.excludeReportTarget(EXPLANATION, EnumSet.of(ReportTarget.LOG_SYSTEM));
    super.param("message", message);
  }
}
