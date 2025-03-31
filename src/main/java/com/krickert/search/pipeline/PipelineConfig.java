package com.krickert.search.pipeline;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.Map;

@Getter
@Singleton
@ConfigurationProperties("pipeline")
@Serdeable
@Introspected
public class PipelineConfig {

    private final Map<String, ServiceConfiguration> service;

    public PipelineConfig(Map<String, ServiceConfiguration> service) {
        this.service = service;
    }

    public ServiceConfiguration getRouteForService(String serviceName) {
        return service.get(serviceName);
    }
}
