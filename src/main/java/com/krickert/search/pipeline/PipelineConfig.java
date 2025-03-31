package com.krickert.search.pipeline;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.Map;

@Getter
@Singleton
@ConfigurationProperties("pipeline")
public class PipelineConfig {

    private final Map<String, ServiceConfiguration> service;

    public PipelineConfig(Map<String, ServiceConfiguration> service) {
        this.service = service;
    }

    public ServiceConfiguration getRouteForService(String serviceName) {
        return service.get(serviceName);
    }
}
