/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.ModuleType;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MockedNextGenService implements NextGenService {
  @Override
  public ConnectorResponseDTO create(ConnectorDTO connectorRequestDTO, String accountIdentifier) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public Optional<ConnectorInfoDTO> get(
      String accountIdentifier, String connectorIdentifier, String orgIdentifier, String projectIdentifier) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public EnvironmentResponseDTO getEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier) {
    return EnvironmentResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(environmentIdentifier)
        .name("Mocked env name")
        .build();
  }

  @Override
  public ServiceResponseDTO getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    return ServiceResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(serviceIdentifier)
        .name("Mocked service name")
        .build();
  }

  @Override
  public List<ServiceResponse> listService(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public List<EnvironmentResponse> listEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> environmentIdentifier) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public int getServicesCount(String accountId, String orgIdentifier, String projectIdentifier) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public int getEnvironmentCount(String accountId, String orgIdentifier, String projectIdentifier) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public ProjectDTO getProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ProjectDTO.builder()
        .orgIdentifier(orgIdentifier)
        .identifier(projectIdentifier)
        .name("Mocked project name")
        .modules(Collections.singletonList(ModuleType.CV))
        .build();
  }

  @Override
  public ProjectDTO getCachedProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getProject(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public OrganizationDTO getOrganization(String accountIdentifier, String orgIdentifier) {
    return OrganizationDTO.builder().identifier(orgIdentifier).name("Mocked org name").build();
  }

  @Override
  public Map<String, String> getServiceIdNameMap(ProjectParams projectParams, List<String> serviceIdentifiers) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }

  @Override
  public Map<String, String> getEnvironmentIdNameMap(ProjectParams projectParams, List<String> environmentIdentifiers) {
    throw new UnsupportedOperationException("mocked method - TODO"); // TODO
  }
}
