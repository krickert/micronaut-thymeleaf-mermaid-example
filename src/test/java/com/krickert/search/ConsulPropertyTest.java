package com.krickert.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krickert.search.pipeline.DefaultPipelineConfig;
import com.krickert.search.pipeline.PipelineConfig;
import com.krickert.search.pipeline.consul.ConsulConfigurationInitializer;
import com.krickert.search.pipeline.consul.ConsulKeyValueClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import jakarta.inject.Inject;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConsulPropertyTest implements TestPropertyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulPropertyTest.class);
    private static final int CONSUL_READY_TIMEOUT_SECONDS = 15;
    private static final int CONFIG_WAIT_TIMEOUT_SECONDS = 15;

    private ConsulTestContainer consulContainer;

    @Inject
    private ApplicationContext applicationContext;

    private HttpClient httpClient;

    @BeforeAll
    void setup() throws MalformedURLException {
        consulContainer = new ConsulTestContainer();
        consulContainer.start();
        String consulUrl = consulContainer.getProperties().get("consul.url");
        LOG.info("Consul started at: {}", consulUrl);

        // Create an HTTP client for direct Consul API access
        httpClient = HttpClient.create(URI.create(consulUrl).toURL());

        // Wait for Consul to be fully ready before running any tests
        waitForConsulReadiness();
    }

    private void waitForConsulReadiness() {
        LOG.info("Waiting for Consul to be ready...");
        Awaitility.await()
                .atMost(CONSUL_READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> {
                    try {
                        HttpResponse<?> response = httpClient.toBlocking().exchange(
                                HttpRequest.GET("/v1/status/leader"));
                        String leader = response.getBody(String.class).orElse("");
                        LOG.debug("Consul leader check: status={}, leader={}",
                                response.getStatus(), leader);
                        return response.getStatus() == HttpStatus.OK && !leader.isEmpty();
                    } catch (Exception e) {
                        LOG.debug("Consul not ready yet: {}", e.getMessage());
                        return false;
                    }
                });
        LOG.info("Consul is ready for testing");

        // Verify KV store is working by putting and getting a test value
        try {
            String testKey = "test/readiness-check";
            String testValue = "ready-" + System.currentTimeMillis();

            HttpRequest<?> putRequest = HttpRequest.PUT("/v1/kv/" + testKey, testValue)
                    .contentType(MediaType.TEXT_PLAIN);
            HttpResponse<?> putResponse = httpClient.toBlocking().exchange(putRequest);

            if (putResponse.getStatus() == HttpStatus.OK) {
                String retrievedValue = httpClient.toBlocking().retrieve(
                        "/v1/kv/" + testKey + "?raw=true", String.class);
                LOG.info("Consul KV store readiness verified: put={}, get={}",
                        putResponse.getStatus(), retrievedValue);
            } else {
                LOG.warn("Consul KV store might not be fully ready: {}", putResponse.getStatus());
            }
        } catch (Exception e) {
            LOG.warn("Failed to verify Consul KV store readiness: {}", e.getMessage());
        }
    }

    @AfterAll
    void cleanup() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (consulContainer != null) {
            consulContainer.stop();
        }
    }

    @Override
    public Map<String, String> getProperties() {
        if (consulContainer != null) {
            Map<String, String> properties = new HashMap<>(consulContainer.getProperties());

            // Ensure the consul.enabled property is set to true
            properties.put("consul.enabled", "true");

            // Add additional properties if needed
            properties.put("consul.client.connect-timeout", "5s");
            properties.put("consul.client.read-timeout", "5s");

            return properties;
        }
        return Map.of();
    }

    // Original test methods remain the same...

    @Test
    void testConsulConfigurationInitializerStartsAndLoadsDefaultConfig() {
        // Get the Consul URL from the container
        String consulUrl = consulContainer.getProperties().get("consul.url");
        LOG.info("Consul URL from container: {}", consulUrl);

        // Create a properly configured HTTP client for Consul
        HttpClient client = null;
        try {
            // Create a client with the correct URL format
            if (!consulUrl.startsWith("http://") && !consulUrl.startsWith("https://")) {
                consulUrl = "http://" + consulUrl;
            }
            LOG.info("Creating HttpClient with URL: {}", consulUrl);
            client = HttpClient.create(new URL(consulUrl));

            // Register the client with the application context
            applicationContext.registerSingleton(HttpClient.class, client);
            LOG.info("Successfully registered HttpClient with URL: {}", consulUrl);

            // Verify the client works with a simple health check
            String healthResponse = client.toBlocking().retrieve("/v1/status/leader");
            LOG.info("Consul health check response: {}", healthResponse);
        } catch (Exception e) {
            fail("Failed to create or verify HTTP client: " + e.getMessage());
        }

        // Get the ConsulKeyValueClient - should use our injected HttpClient
        ConsulKeyValueClient consulClient = applicationContext.getBean(ConsulKeyValueClient.class);
        assertNotNull(consulClient, "ConsulKeyValueClient should be available");

        // Test direct KV operations using our client to verify it's working
        String testKey = "test/client-verification";
        String testValue = "test-" + System.currentTimeMillis();

        try {
            // Test put operation
            HttpResponse<Boolean> putResponse = consulClient.putValue(testKey, testValue);
            assertTrue(putResponse.getBody().orElse(false),
                    "Put operation should return true");

            // Test get operation to verify value was stored
            HttpResponse<?> getResponse = consulClient.getValue(testKey);
            assertEquals(HttpStatus.OK, getResponse.getStatus(),
                    "Get operation should return OK status");
            String retrievedValue = getResponse.getBody(String.class).orElse("");
            assertEquals(testValue, retrievedValue,
                    "Retrieved value should match stored value");

            LOG.info("ConsulKeyValueClient verification successful");
        } catch (Exception e) {
            fail("ConsulKeyValueClient verification failed: " + e.getMessage());
        }

        // Get the DefaultPipelineConfig
        DefaultPipelineConfig pipelineConfig = applicationContext.getBean(DefaultPipelineConfig.class);
        assertNotNull(pipelineConfig, "DefaultPipelineConfig should be available");

        // Get the ConsulConfigurationInitializer
        ConsulConfigurationInitializer initializer = applicationContext.getBean(ConsulConfigurationInitializer.class);
        assertNotNull(initializer, "ConsulConfigurationInitializer should be available");

        // Get the ObjectMapper for verification
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);

        // Create a simple pipeline config
        PipelineConfig testConfig = new PipelineConfig("test-pipeline");
        Map<String, PipelineConfig> pipelines = Map.of("test-pipeline", testConfig);
        pipelineConfig.setPipelines(pipelines);

        // Clear any existing values for clean test
        try {
            HttpRequest<?> deleteRequest = HttpRequest.DELETE("/v1/kv/pipeline/configs/test-pipeline");
            httpClient.toBlocking().exchange(deleteRequest);
            LOG.info("Cleared existing test pipeline config");
        } catch (Exception e) {
            LOG.debug("No existing config to delete: {}", e.getMessage());
        }

        // Manually trigger the startup event
        initializer.onStartup(new StartupEvent(applicationContext));

        // Wait for the initialization to complete
        LOG.info("Waiting for pipeline config to appear in Consul...");
        Awaitility.await()
                .atMost(CONFIG_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    try {
                        // Check directly using the HTTP client for verification
                        HttpRequest<?> request = HttpRequest.GET("/v1/kv/pipeline/configs/test-pipeline?raw=true");
                        HttpResponse<?> response = httpClient.toBlocking().exchange(request, String.class);
                        return response.getStatus() == HttpStatus.OK &&
                                response.getBody(String.class).isPresent();
                    } catch (Exception e) {
                        LOG.debug("Pipeline config not yet available: {}", e.getMessage());
                        return false;
                    }
                });

        // Verify that the pipeline config was stored in Consul
        try {
            String storedConfig = httpClient.toBlocking()
                    .retrieve("/v1/kv/pipeline/configs/test-pipeline?raw=true", String.class);
            assertNotNull(storedConfig, "Pipeline config should be stored in Consul");

            // Further verify the content
            PipelineConfig retrievedConfig = objectMapper.readValue(storedConfig, PipelineConfig.class);
            assertEquals("test-pipeline", retrievedConfig.getName(), "Pipeline name should match");
            LOG.info("Pipeline config content verified successfully");
        } catch (Exception e) {
            fail("Failed to retrieve or verify pipeline config from Consul: " + e.getMessage());
        } finally {
//            if (clientBean != null) {
//                clientBean.close();
//            }
        }
    }
}