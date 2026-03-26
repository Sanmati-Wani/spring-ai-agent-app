package com.prismx.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenResponse {
    @Schema(description = "Unique generation id")
    private String generationId;
    @Schema(description = "Generation timestamp in ISO-8601")
    private String generatedAt;
    @Schema(description = "Agent name associated with scaffold request")
    private String agentName;
    @Schema(description = "Raw model output")
    private String output;
    @Schema(description = "Files written relative to output root")
    private List<String> filesCreated;
    @Schema(description = "Endpoints detected from generated controller mappings")
    private List<EndpointDetails> createdEndpoints;
    @Schema(description = "Rendered folder tree after write")
    private String folderStructure;
    @Schema(description = "Non-blocking warnings")
    private List<String> warnings;
    @Schema(description = "Blocking errors")
    private List<String> errors;
    @Schema(description = "Safety overrides enforced during request")
    private List<String> safetyOverridesApplied;

    public String generationId() { return generationId; }
    public String generatedAt() { return generatedAt; }
    public String agentName() { return agentName; }
    public String output() { return output; }
    public List<String> filesCreated() { return filesCreated; }
    public List<EndpointDetails> createdEndpoints() { return createdEndpoints; }
    public String folderStructure() { return folderStructure; }
    public List<String> warnings() { return warnings; }
    public List<String> errors() { return errors; }
    public List<String> safetyOverridesApplied() { return safetyOverridesApplied; }
}
