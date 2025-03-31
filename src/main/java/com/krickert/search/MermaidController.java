package com.krickert.search;

import com.krickert.search.pipeline.PipelineConfig;
import com.krickert.search.pipeline.ServiceConfiguration;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.views.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Controller
public class MermaidController {

    private static final Logger log = LoggerFactory.getLogger(MermaidController.class);
    private final PipelineConfig pipelineConfig;

    public MermaidController(PipelineConfig pipelineConfig) {
        this.pipelineConfig = pipelineConfig;
        log.info("Loaded PipelineConfig: {}", pipelineConfig);
    }

    @Get("/")
    @View("mermaid")
    public Map<String, Object> index() {
        StringBuilder mermaidGraph = new StringBuilder("graph TD\n");
        Set<String> declaredNodes = new HashSet<>();

        for (ServiceConfiguration serviceConfig : pipelineConfig.getService().values()) {

            String serviceNodeId = nodeId("grpc", serviceConfig.getName());
            declareGrpcNodeIfNeeded(mermaidGraph, declaredNodes, serviceNodeId, serviceConfig.getName());

            // Connect Kafka listen (input) topics -> Service
            if (serviceConfig.getKafkaListenTopics() != null) {
                serviceConfig.getKafkaListenTopics().forEach(topic -> {
                    String kafkaNodeId = nodeId("kafka", topic);
                    declareKafkaNodeIfNeeded(mermaidGraph, declaredNodes, kafkaNodeId, topic);

                    mermaidGraph.append(String.format("    %s --> %s%n", kafkaNodeId, serviceNodeId));
                });
            }

            // Connect Service -> Kafka publish topics (output)
            if (serviceConfig.getKafkaPublishTopics() != null) {
                serviceConfig.getKafkaPublishTopics().forEach(topic -> {
                    String kafkaNodeId = nodeId("kafka", topic);
                    declareKafkaNodeIfNeeded(mermaidGraph, declaredNodes, kafkaNodeId, topic);

                    mermaidGraph.append(String.format("    %s --> %s%n", serviceNodeId, kafkaNodeId));
                });
            }

            // Connect Service -> gRPC forward services (output)
            if (serviceConfig.getGrpcForwardTo() != null) {
                serviceConfig.getGrpcForwardTo().stream()
                        .filter(grpc -> !"null".equalsIgnoreCase(grpc))
                        .forEach(grpcService -> {
                            String targetNodeId = nodeId("grpc", grpcService);
                            declareGrpcNodeIfNeeded(mermaidGraph, declaredNodes, targetNodeId, grpcService);

                            mermaidGraph.append(String.format("    %s --> %s%n", serviceNodeId, targetNodeId));
                        });
            }
        }

        // Set color styles once at end
        declaredNodes.stream()
                .filter(node -> node.startsWith("kafka_"))
                .forEach(kafkaNode ->
                        mermaidGraph.append(String.format("    style %s fill:#FFA500,stroke:#333,color:#000%n", kafkaNode))
                );
        declaredNodes.stream()
                .filter(node -> node.startsWith("grpc_"))
                .forEach(grpcNode ->
                        mermaidGraph.append(String.format("    style %s fill:#ADD8E6,stroke:#333,color:#000%n", grpcNode))
                );

        log.debug("Mermaid Graph with Colors:\n{}", mermaidGraph);
        return Map.of("mermaidGraph", mermaidGraph.toString());
    }

// Node declaration helpers (unchanged from previous solution)

    private void declareKafkaNodeIfNeeded(StringBuilder mermaid, Set<String> declared, String nodeId, String label) {
        if (declared.add(nodeId)) {
            mermaid.append(String.format("    %s[%s]%n", nodeId, label));
        }
    }

    private void declareGrpcNodeIfNeeded(StringBuilder mermaid, Set<String> declared, String nodeId, String label) {
        if (declared.add(nodeId)) {
            mermaid.append(String.format("    %s([%s])%n", nodeId, label));
        }
    }

    private String nodeId(String prefix, String name) {
        return prefix + "_" + name.replaceAll("[^A-Za-z0-9]", "_");
    }
}