package com.krickert.search.pipeline;

import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
@Refreshable
@Getter
public class PipelineConfigService {

    // All pipeline configs are injected as a map, keyed by the configuration name.
    private final Map<String, PipelineConfig> pipelineConfigs;

    // The active pipeline name is set via configuration; default "pipeline1" for example.
    private String activePipelineName;

    public PipelineConfigService(Map<String, PipelineConfig> pipelineConfigs,
                                 @Value("${pipeline.active:pipeline1}") String activePipelineName) {
        // Using a ConcurrentHashMap for thread safety.
        this.pipelineConfigs = new ConcurrentHashMap<>(pipelineConfigs);
        this.activePipelineName = activePipelineName;
    }

    public PipelineConfig getActivePipelineConfig() {
        return pipelineConfigs.get(activePipelineName);
    }

    public Collection<PipelineConfig> getAllPipelineConfigs() {
        return pipelineConfigs.values();
    }

    public Set<String> getAllPipelineNames() {
        return pipelineConfigs.keySet();
    }

    public void setActivePipelineName(String activePipelineName) {
        if (pipelineConfigs.containsKey(activePipelineName)) {
            this.activePipelineName = activePipelineName;
        }
    }
}