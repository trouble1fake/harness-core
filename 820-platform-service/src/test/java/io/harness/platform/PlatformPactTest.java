package io.harness.platform;

import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.PactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import io.harness.CategoryTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Slf4j
public class PlatformPactTest {
    @Rule
    public PactProviderRule mockProvider = new PactProviderRule("Gateway", this);



    @Pact(consumer = "Admin-Portal")
    public RequestResponsePact createPactVersion(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

      return builder
                .given("version is 1.1")
                .uponReceiving("a request for the version")
                .path("/api/gateway-version-pact")
                .method("GET")
                .willRespondWith()
                .headers(headers)
                .status(200)
//                .body(new PactDslJsonBody()
//                        .decimalType("system.properties.version", 1.1))
                .body("{\"metaData\":null,\"resource\":{\"system.properties.version\":{\"string\":\"1.1\",\"chars\":\"1.1\",\"valueType\":\"STRING\"}},\"responseMessages\":null}")
                .toPact();
    }
    //
    @Pact(consumer = "Admin-Portal")
    public RequestResponsePact createPactInvalid(PactDslWithProvider builder) {
        return builder
                .given("invalid property")
                .uponReceiving("a request with an invalid property")
                .path("/invalidurl")
                .method("GET")
                .willRespondWith()
                .status(500)
                .toPact();
    }

    @Test
    @PactVerification(value = "Gateway", fragment = "createPactVersion")
    public void runVersionTest() {
        String version = new PlatformPactURL(mockProvider.getUrl()).getVersion();
        assertEquals("Expected version does not match",
                "{\"metaData\":null,\"resource\":{\"system.properties.version\":{\"string\":\"1.1\",\"chars\":\"1.1\",\"valueType\":\"STRING\"}},\"responseMessages\":null}", version);
    }
    //
    @Test
    @PactVerification(value = "Gateway", fragment = "createPactInvalid")
    public void runInvalidTest() {
        String invalid = new PlatformPactURL(mockProvider.getUrl()).getInvalidProperty();
        assertEquals("Expected invalid property response does not match",
                "", invalid);
    }
}
