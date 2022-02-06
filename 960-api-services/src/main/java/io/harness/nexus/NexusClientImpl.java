/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;

import software.wings.utils.RepositoryFormat;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class NexusClientImpl {
  @Inject TimeLimiter timeLimiter;
  @Inject NexusThreeClientImpl nexusThreeService;
  @Inject NexusTwoClientImpl nexusTwoService;

  public Map<String, String> getRepositories(NexusRequest nexusConfig) {
    return getRepositories(nexusConfig, null);
  }

  public Map<String, String> getRepositories(NexusRequest nexusConfig, String repositoryFormat) {
    try {
      boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(20L), () -> {
        if (isNexusTwo) {
          if (RepositoryFormat.docker.name().equals(repositoryFormat)) {
            throw NestedExceptionUtils.hintWithExplanationException("Nexus 2.x does not support Docker artifacts",
                "The version for the connector is probably 3.x and not 2.x",
                new InvalidArtifactServerException("Nexus 2.x does not support Docker artifact type", USER));
          }
          return nexusTwoService.getRepositories(nexusConfig, repositoryFormat);
        } else {
          if (repositoryFormat == null) {
            throw NestedExceptionUtils.hintWithExplanationException(
                "Nexus 3.x requires that a repository format is correct",
                "Ensure that a right repository format is chosen",
                new InvalidRequestException("Not supported for nexus 3.x", USER));
          }
          return nexusThreeService.getRepositories(nexusConfig, repositoryFormat);
        }
      });
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error occurred while retrieving Repositories from Nexus server " + nexusConfig.getNexusUrl(), e);
      if (e.getCause() instanceof XMLStreamException) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the Nexus URL is reachable from your delegate(s)", "The given Nexus URL is not reachable",
            new InvalidArtifactServerException("Nexus may not be running", USER));
      }
      NexusHelper.checkSSLHandshakeException(e);
      return emptyMap();
    }
  }

  public boolean isRunning(NexusRequest nexusConfig) {
    if (nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x")) {
      return getRepositories(nexusConfig, null) != null;
    } else {
      try {
        return nexusThreeService.isServerValid(nexusConfig);
      } catch (UnknownHostException e) {
        throw NestedExceptionUtils.hintWithExplanationException("Check if the Nexus URL & version are correct",
            "The Nexus URL for the connector is incorrect or not reachable",
            new InvalidArtifactServerException("Unknown Nexus Host"));
      } catch (InvalidArtifactServerException e) {
        throw e;
      } catch (WingsException e) {
        if (e instanceof HintException) {
          throw e;
        }
        if (ExceptionUtils.getMessage(e).contains("Invalid Nexus credentials")) {
          throw e;
        }
        return true;
      } catch (Exception e) {
        log.warn("Failed to retrieve repositories. Ignoring validation for Nexus 3 for now. User can give custom path");
        NexusHelper.checkSSLHandshakeException(e);
        return true;
      }
    }
  }

  public List<BuildDetailsInternal> getArtifactsVersions(NexusRequest nexusConfig, String repositoryName, String port,
      String imageName, String repoFormat) throws IOException {
    boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");

    if (isNexusTwo) {
      throw NestedExceptionUtils.hintWithExplanationException("Nexus 2.x does not support Nexus artifacts",
          "The version for the connector is probably 3.x and not 2.x",
          new InvalidArtifactServerException("Nexus 2.x does not support Nexus artifact type", USER));
    } else {
      return nexusThreeService.getArtifactsVersions(nexusConfig, repositoryName, port, imageName, repoFormat);
    }
  }

  public List<BuildDetailsInternal> getBuildDetails(NexusRequest nexusConfig, String repository, String port,
      String imageName, String repositoryFormat, String tag) throws IOException {
    boolean isNexusTwo = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("2.x");

    if (isNexusTwo) {
      throw NestedExceptionUtils.hintWithExplanationException("Nexus 2.x does not support Nexus artifacts",
          "The version for the connector is probably 3.x and not 2.x",
          new InvalidArtifactServerException("Nexus 2.x does not support Nexus artifact type", USER));
    } else {
      return nexusThreeService.getBuildDetails(nexusConfig, repository, port, imageName, repositoryFormat, tag);
    }
  }
}
