/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.mappers.artifact;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.DockerConfig;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DockerConfigToInternalMapperTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testToDockerInternalConfig() {
    DockerConfig dockerConfig = DockerConfig.builder()
                                    .dockerRegistryUrl("https://registry.hub.docker.com")
                                    .username("USERNAME")
                                    .password("PASSWORD".toCharArray())
                                    .build();
    DockerInternalConfig dockerInternalConfig = DockerConfigToInternalMapper.toDockerInternalConfig(dockerConfig);
    assertThat(dockerInternalConfig).isNotNull();
    assertThat(dockerInternalConfig.getDockerRegistryUrl()).isEqualTo("https://registry.hub.docker.com/");
    assertThat(dockerInternalConfig.getUsername()).isEqualTo(dockerConfig.getUsername());
    assertThat(dockerInternalConfig.getPassword()).isEqualTo(new String(dockerConfig.getPassword()));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testToDockerInternalConfigWithQueryParams() {
    DockerConfig dockerConfig = DockerConfig.builder()
                                    .dockerRegistryUrl("https://registry.hub.docker.com/v2?a=b&b=c")
                                    .username("USERNAME")
                                    .password("PASSWORD".toCharArray())
                                    .build();
    DockerInternalConfig dockerInternalConfig = DockerConfigToInternalMapper.toDockerInternalConfig(dockerConfig);
    assertThat(dockerInternalConfig).isNotNull();
    assertThat(dockerInternalConfig.getDockerRegistryUrl()).isEqualTo("https://registry.hub.docker.com/v2/?a=b&b=c");
    assertThat(dockerInternalConfig.getUsername()).isEqualTo(dockerConfig.getUsername());
    assertThat(dockerInternalConfig.getPassword()).isEqualTo(new String(dockerConfig.getPassword()));
  }
}
