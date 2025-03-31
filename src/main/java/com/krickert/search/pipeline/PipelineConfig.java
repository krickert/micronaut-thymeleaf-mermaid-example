package com.krickert.search.pipeline;

import com.krickert.search.pipeline.ServiceConfiguration;
import com.krickert.search.pipeline.ServiceConfigurationDto;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Getter
@Singleton
@ConfigurationProperties("pipeline")
@Serdeable
@Introspected
@Context
public class PipelineConfig {

    private final Map<String, ServiceConfiguration> service;

    public PipelineConfig(Map<String, ServiceConfiguration> service) {
        this.service = new ConcurrentHashMap<>(service);
    }

    public void addOrUpdateService(ServiceConfigurationDto dto) {
        ServiceConfiguration config = new ServiceConfiguration(dto.getName());
        config.setKafkaListenTopics(dto.getKafkaListenTopics());
        config.setKafkaPublishTopics(dto.getKafkaPublishTopics());
        config.setGrpcForwardTo(dto.getGrpcForwardTo());
        service.put(dto.getName(), config);
    }
}