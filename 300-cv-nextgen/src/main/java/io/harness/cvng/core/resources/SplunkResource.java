/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.core.services.api.SplunkService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.LinkedHashMap;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

@Api("splunk/")
@Path("splunk")
@Produces("application/json")
@NextGenManagerAuth
public class SplunkResource {
  @Inject private SplunkService splunkService;
  @GET
  @Path("saved-searches")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "gets saved searches in splunk", nickname = "getSplunkSavedSearches")
  public RestResponse<List<SplunkSavedSearch>> getSavedSearches(@QueryParam("accountId") @Valid final String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("connectorIdentifier") String connectorIdentifier,
      @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(
        splunkService.getSavedSearches(accountId, orgIdentifier, projectIdentifier, connectorIdentifier, requestGuid));
  }

  @GET
  @Path("sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "validates given setting for splunk data source", nickname = "getSplunkSampleData")
  public RestResponse<List<LinkedHashMap>> getSampleData(@NotNull @QueryParam("accountId") final String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @NotEmpty @QueryParam("query") String query, @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(splunkService.getSampleData(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, query, requestGuid));
  }

  @GET
  @Path("latest-histogram")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get latest histogram for the query", nickname = "getSplunkLatestHistogram")
  public RestResponse<List<LinkedHashMap>> getLatestHistogram(@NotNull @QueryParam("accountId") final String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @NotEmpty @QueryParam("query") String query, @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(splunkService.getLatestHistogram(
        accountId, orgIdentifier, projectIdentifier, connectorIdentifier, query, requestGuid));
  }
}
