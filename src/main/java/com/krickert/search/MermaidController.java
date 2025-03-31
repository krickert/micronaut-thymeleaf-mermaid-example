package com.krickert.search;

import com.krickert.search.pipeline.PipelineConfig;
import com.krickert.search.pipeline.ServiceConfiguration;
import com.krickert.search.pipeline.ServiceConfigurationDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Inject;

import java.util.Map;

import io.micronaut.views.View;

@Controller
public class MermaidController {

    private final PipelineConfig pipelineConfig;

    public MermaidController(PipelineConfig pipelineConfig) {
        this.pipelineConfig = pipelineConfig;
    }

    @View("mermaid-editor")
    @Get("/")
    public Map<String, Object> index() {
        return Map.of("pipelineConfig", pipelineConfig.getService());
    }

    @Get(value = "/pipeline", produces = MediaType.APPLICATION_JSON)
    public Map<String, ServiceConfiguration> getPipelineJson() {
        return pipelineConfig.getService();
    }

    @Post(value = "/pipeline/add", consumes = MediaType.APPLICATION_JSON)
    public HttpStatus addService(@Body ServiceConfigurationDto dto) {
        pipelineConfig.addOrUpdateService(dto);
        return HttpStatus.CREATED;
    }
}