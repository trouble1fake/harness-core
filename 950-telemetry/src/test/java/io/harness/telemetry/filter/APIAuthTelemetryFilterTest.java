/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.filter;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.telemetry.Destination.AMPLITUDE;
import static io.harness.telemetry.filter.APIAuthTelemetryFilter.*;

import static org.mockito.Mockito.when;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.rule.Owner;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class APIAuthTelemetryFilterTest {
  public static final String SOME_API_KEY = "some-api-key";
  public static final String SOME_AUTH_TOKEN = "some-auth-token";
  public static final String SOME_ACCOUNT_ID = "some-account-id";
  public static final String SOME_API_ENDPOINT = "/some-api-endpoint";
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private ContainerRequestContext containerRequestContext;
  @Mock private UriRoutingContext uriRoutingContext;
  @Mock private MultivaluedMap<String, String> parametersMap;

  private APIAuthTelemetryFilter filter;
  private final ExecutorService executorService = MoreExecutors.newDirectExecutorService();
  private final HashMap<String, Object> properties = new HashMap<>();

  @Before
  public void setup() {
    when(containerRequestContext.getUriInfo()).thenReturn(uriRoutingContext);
    when(uriRoutingContext.getPath()).thenReturn(SOME_API_ENDPOINT);
    when(uriRoutingContext.getQueryParameters()).thenReturn(parametersMap);
    when(uriRoutingContext.getPathParameters()).thenReturn(parametersMap);
    when(parametersMap.getFirst(NGCommonEntityConstants.ACCOUNT_KEY)).thenReturn(SOME_ACCOUNT_ID);

    filter = new APIAuthTelemetryFilter(telemetryReporter, executorService);

    properties.put(ACCOUNT_ID, SOME_ACCOUNT_ID);
    properties.put(API_ENDPOINT, SOME_API_ENDPOINT);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  public void shouldNotSendTelemetryDataIfAccountIdentifierNotPresentInUri() {
    when(parametersMap.getFirst(NGCommonEntityConstants.ACCOUNT_KEY)).thenReturn(null);

    filter.filter(containerRequestContext);

    Mockito.verifyZeroInteractions(telemetryReporter);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  public void shouldSendTelemetryForApiKey() {
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(SOME_API_KEY);
    properties.put(AUTH_TYPE, X_API_KEY);

    filter.filter(containerRequestContext);

    Mockito.verify(telemetryReporter)
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  public void shouldSendTelemetryForAuthorization() {
    when(containerRequestContext.getHeaderString(APIAuthTelemetryFilter.AUTHORIZATION_HEADER))
        .thenReturn(SOME_AUTH_TOKEN);
    properties.put(AUTH_TYPE, BEARER);

    filter.filter(containerRequestContext);

    Mockito.verify(telemetryReporter)
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  public void shouldSendTelemetryForUnknownAuthorization() {
    properties.put(AUTH_TYPE, UNKNOWN);

    filter.filter(containerRequestContext);

    Mockito.verify(telemetryReporter)
        .sendTrackEvent(API_ENDPOINTS_AUTH_SCHEMES, null, SOME_ACCOUNT_ID, properties,
            Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
            TelemetryOption.builder().sendForCommunity(false).build());
  }
}
