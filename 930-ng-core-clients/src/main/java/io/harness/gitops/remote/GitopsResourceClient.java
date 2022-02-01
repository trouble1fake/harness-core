package io.harness.gitops.remote;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitops.models.Agent;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.Cluster;
import io.harness.gitops.models.Repository;
import io.harness.ng.beans.PageResponse;

import java.util.Map;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.GITOPS)
public interface GitopsResourceClient {
  @GET("agents")
  Call<PageResponse<Agent>> listAgents(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size);

  @POST("applications")
  Call<PageResponse<Application>> listApps(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      @Body Object filter);

  @POST("repositories")
  Call<PageResponse<Repository>> listRepositories(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      Map<String, Object> filter);

  @POST("clusters")
  Call<PageResponse<Cluster>> listClusters(
      @Query(value = NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Query(value = NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(value = NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query(value = NGResourceFilterConstants.PAGE_KEY) int page, @Query(NGResourceFilterConstants.SIZE_KEY) int size,
      Map<String, Object> filter);
}
