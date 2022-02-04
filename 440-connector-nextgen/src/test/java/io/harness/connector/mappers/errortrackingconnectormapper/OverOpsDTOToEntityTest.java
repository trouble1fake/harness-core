/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.errortrackingconnectormapper;

import static io.harness.rule.OwnerRule.ANGELO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.errortrackingconnector.ErrorTrackingConnector;
import io.harness.connector.mappers.errortrackingmapper.ErrorTrackingDTOToEntity;
import io.harness.delegate.beans.connector.errortracking.ErrorTrackingConnectorDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class OverOpsDTOToEntityTest extends CategoryTest {
  @InjectMocks ErrorTrackingDTOToEntity overOpsDTOToEntity;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANGELO)
  @Category(UnitTests.class)
  public void testToOverOpsConnector() {
    String overOpsUrl = "overOpsUrl";

    ErrorTrackingConnectorDTO overOpsConnectorDTO = ErrorTrackingConnectorDTO.builder().url(overOpsUrl).build();

    ErrorTrackingConnector overOpsConnector = overOpsDTOToEntity.toConnectorEntity(overOpsConnectorDTO);
    assertThat(overOpsConnector).isNotNull();
    assertThat(overOpsConnector.getUrl()).isEqualTo(overOpsConnectorDTO.getUrl());
  }
}
