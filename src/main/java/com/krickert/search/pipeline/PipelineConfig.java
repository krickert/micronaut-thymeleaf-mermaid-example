package com.krickert.search.pipeline;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@EachProperty("pipeline.configs")
@Singleton
@Serdeable
@Introspected
public class PipelineConfig {

    // The property key from the configuration (pipeline1, pipeline2, etc.)
    private final String name;

    // The existing map of service configurations.
    private Map<String, ServiceConfiguration> service;

    public PipelineConfig(@Parameter String name) {
        this.name = name;
    }

    public boolean containsService(String serviceName) {
        return service.containsKey(serviceName);
    }

    public void addOrUpdateService(ServiceConfigurationDto dto) {
        ServiceConfiguration config = new ServiceConfiguration(dto.getName());
        config.setKafkaListenTopics(dto.getKafkaListenTopics());
        config.setKafkaPublishTopics(dto.getKafkaPublishTopics());
        config.setGrpcForwardTo(dto.getGrpcForwardTo());
        service.put(dto.getName(), config);
    }
}