package com.prismx.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentScaffoldRequest {
    @NotBlank(message = "agentName is required")
    private String agentName;
    @NotBlank(message = "projectPath is required")
    private String projectPath;
    @NotBlank(message = "description is required")
    private String description;
    private Boolean includeRepository;
    private Boolean includeTests;
    private Boolean overwriteExisting;
    private Boolean mergeIfNeeded;
    private String modelProvider;

    public String agentName() { return agentName; }
    public String projectPath() { return projectPath; }
    public String description() { return description; }
    public Boolean includeRepository() { return includeRepository; }
    public Boolean includeTests() { return includeTests; }
    public Boolean overwriteExisting() { return overwriteExisting; }
    public Boolean mergeIfNeeded() { return mergeIfNeeded; }
    public String modelProvider() { return modelProvider; }
}
