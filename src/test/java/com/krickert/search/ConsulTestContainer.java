package com.krickert.search;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class ConsulTestContainer extends GenericContainer<ConsulTestContainer>
        implements TestPropertyProvider {
    public ConsulTestContainer() {
        super(DockerImageName.parse("hashicorp/consul:1.20"));
        // Expose Consul’s default port
        withExposedPorts(8500);
        // Run Consul in development mode, accessible on all interfaces.
        withCommand("agent", "-dev", "-client=0.0.0.0");
    }

    @Override
    public Map<String, String> getProperties() {
        // Provide the Consul URL property to your tests
        String address = "http://" + getHost() + ":" + getMappedPort(8500);
        return Map.of("consul.url", address, "consul.enabled", "true");
    }
}