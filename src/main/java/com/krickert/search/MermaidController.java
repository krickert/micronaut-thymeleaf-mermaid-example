package com.krickert.search;

import com.krickert.search.pipeline.PipelineConfig;
import com.krickert.search.pipeline.PipelineConfigService;
import com.krickert.search.pipeline.ServiceConfiguration;
import com.krickert.search.pipeline.ServiceConfigurationDto;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.views.View;
import jakarta.inject.Inject;

import java.util.Map;

@Controller
public class MermaidController {

    private final PipelineConfigService configService;

    @Inject
    public MermaidController(PipelineConfigService configService) {
        this.configService = configService;
    }

    @View("mermaid-editor")
    @Get("/")
    public Map<String, Object> index(@QueryValue(defaultValue = "") String pipeline) {
        // If a pipeline query parameter is provided, update the active pipeline.
        if (!pipeline.isEmpty()) {
            configService.setActivePipelineName(pipeline);
        }
        PipelineConfig activeConfig = configService.getActivePipelineConfig();
        return Map.of(
                "pipelineConfig", activeConfig.getService(),
                "pipelineNames", configService.getAllPipelineNames(),
                "activePipeline", activeConfig.getName(),
                "pipelines", configService.getAllPipelineConfigs()
        );
    }

    @Get(value = "/pipeline", produces = MediaType.APPLICATION_JSON)
    public Map<String, ServiceConfiguration> getPipelineJson() {
        return configService.getActivePipelineConfig().getService();
    }

    @Post(value = "/pipeline/add", consumes = MediaType.APPLICATION_JSON)
    public HttpStatus addService(@Body ServiceConfigurationDto dto) {
        // Validate: if any forward-to service isn't present in the active pipeline, add it.
        for (String grpcForward : dto.getGrpcForwardTo()) {
            if (!configService.getActivePipelineConfig().containsService(grpcForward)) {
                ServiceConfigurationDto newDto = new ServiceConfigurationDto();
                newDto.setName(grpcForward);
                configService.getActivePipelineConfig().addOrUpdateService(newDto);
            }
        }
        configService.getActivePipelineConfig().addOrUpdateService(dto);
        return HttpStatus.CREATED;
    }
}