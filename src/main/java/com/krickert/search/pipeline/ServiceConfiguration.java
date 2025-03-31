package com.krickert.search.pipeline;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EachProperty("pipeline.service")
@Singleton
@Serdeable
@Introspected
public class ServiceConfiguration {

    private final String name;
    private List<String> kafkaListenTopics;
    private List<String> kafkaPublishTopics;
    private List<String> grpcForwardTo;

    public ServiceConfiguration(@Parameter String name) {
        this.name = name;
    }
}
