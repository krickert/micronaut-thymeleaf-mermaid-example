package com.krickert.search;

import com.krickert.search.pipeline.PipelineConfig;
import com.krickert.search.pipeline.ServiceConfiguration;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Inject;

import java.util.Map;

import io.micronaut.views.View;


@Controller
public class MermaidController {

    private final PipelineConfig pipelineConfig;

    @Inject
    public MermaidController(PipelineConfig pipelineConfig) {
        this.pipelineConfig = pipelineConfig;
    }

    // Render the Thymeleaf view named "mermaid.html" automatically.
    @View("mermaid")
    @Get("/")
    public void index() {
        // no explicit implementation required!
    }

    // JSON Endpoint clearly defined to provide Mermaid config dynamically.
    @Get(value = "/pipeline", produces = MediaType.APPLICATION_JSON)
    public Map<String, ServiceConfiguration> getPipelineJson() {
        return pipelineConfig.getService();
    }
}
