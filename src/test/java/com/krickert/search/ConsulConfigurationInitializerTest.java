package com.krickert.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krickert.search.pipeline.DefaultPipelineConfig;
import com.krickert.search.pipeline.PipelineConfig;
import com.krickert.search.pipeline.ServiceConfiguration;
import com.krickert.search.pipeline.consul.ConsulConfigurationInitializer;
import com.krickert.search.pipeline.consul.ConsulKeyValueClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import jakarta.inject.Inject;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ConsulConfigurationInitializerTest implements TestPropertyProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ConsulConfigurationInitializerTest.class);

    private ConsulTestContainer consulContainer;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private ConsulKeyValueClient consulClient;

    @Inject
    private DefaultPipelineConfig defaultPipelineConfig;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private ConsulConfigurationInitializer consulInitializer;

    private HttpClient directHttpClient;

    @BeforeAll
    void setup() throws MalformedURLException {
        consulContainer = new ConsulTestContainer();
        consulContainer.start();
        LOG.info("Consul started at: {}", consulContainer.getProperties().get("consul.url"));

        // Create a direct HTTP client for verification
        directHttpClient = HttpClient.create(URI.create(consulContainer.getProperties().get("consul.url")).toURL());
    }

    @AfterAll
    void cleanup() {
        if (directHttpClient != null) {
            directHttpClient.close();
        }
        if (consulContainer != null) {
            consulContainer.stop();
        }
    }

    @Override
    public Map<String, String> getProperties() {
        if (consulContainer != null) {
            return consulContainer.getProperties();
        } else {
            ConsulTestContainer consulContainer = new ConsulTestContainer();
            consulContainer.start();
            LOG.info("Consul started at: {}", consulContainer.getProperties().get("consul.url"));
            this.consulContainer = consulContainer;
            return consulContainer.getProperties();

        }
    }

    @Test
    void testConsulInitializerLoadsDefaultConfigsIntoConsul() throws Exception {
        // Verify Consul is running
        assertTrue(consulContainer.isRunning(), "Consul container should be running");

        // Wait for Consul to be ready
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> {
                    try {
                        directHttpClient.toBlocking().exchange("/v1/status/leader");
                        return true;
                    } catch (Exception e) {
                        LOG.info("Waiting for Consul to be ready...");
                        return false;
                    }
                });

        LOG.info("Consul is ready, proceeding with test");

        // Verify DefaultPipelineConfig is loaded
        assertNotNull(defaultPipelineConfig, "DefaultPipelineConfig should be injected");

        // Setup a test pipeline config
        Map<String, PipelineConfig> testPipelines = new HashMap<>();
        PipelineConfig testConfig = new PipelineConfig("default");
        Map<String, ServiceConfiguration> services = new HashMap<>();

        ServiceConfiguration serviceConfig = new ServiceConfiguration("testService");
        serviceConfig.setKafkaListenTopics(List.of("input-topic"));
        serviceConfig.setKafkaPublishTopics(List.of("output-topic"));
        serviceConfig.setGrpcForwardTo(List.of("downstream-service"));

        services.put("testService", serviceConfig);
        testConfig.setService(services);
        testPipelines.put("pipeline1", testConfig);

        // Set the test config on the defaultPipelineConfig
        defaultPipelineConfig.setPipelines(testPipelines);

        // Trigger the startup event manually
        consulInitializer.onStartup(new StartupEvent(applicationContext));

        // Wait for consul to be updated
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    try {
                        // Check if the value exists in Consul using direct client
                        HttpRequest<?> request = HttpRequest.GET("/v1/kv/pipeline/configs/pipeline1?raw=true");
                        HttpResponse<?> response = directHttpClient.toBlocking().exchange(request);
                        return response.getStatus() == HttpStatus.OK;
                    } catch (Exception e) {
                        return false;
                    }
                });

        // Verify the data was stored in Consul
        String storedJson = directHttpClient.toBlocking()
                .retrieve("/v1/kv/pipeline/configs/test-pipeline?raw=true");

        assertNotNull(storedJson, "Value should be present in Consul");
        LOG.info("Successfully retrieved config from Consul: {}", storedJson);

        PipelineConfig retrievedConfig = objectMapper.readValue(storedJson, PipelineConfig.class);

        assertNotNull(retrievedConfig.getService(), "Services should exist in retrieved config");
        assertTrue(retrievedConfig.getService().containsKey("testService"),
                "testService should exist in retrieved config");

        ServiceConfiguration retrievedService = retrievedConfig.getService().get("testService");
        assertEquals("input-topic", retrievedService.getKafkaListenTopics().get(0),
                "KafkaListenTopics should match");
        assertEquals("output-topic", retrievedService.getKafkaPublishTopics().get(0),
                "KafkaPublishTopics should match");
        assertEquals("downstream-service", retrievedService.getGrpcForwardTo().get(0),
                "GrpcForwardTo should match");
    }
}