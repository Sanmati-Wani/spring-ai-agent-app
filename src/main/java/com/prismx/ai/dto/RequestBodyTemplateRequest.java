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
public class RequestBodyTemplateRequest {
    @NotBlank(message = "description is required")
    private String description;
    private String agentName;
    private String projectType;
    private Boolean includeTests;
    private String outputStyle;
    private Boolean applyToProject;
    private String outputRootDir;
    private Boolean overwriteExisting;
    private Boolean restrictToMavenPaths;
    private Boolean mergeIfNeeded;
    private String modelProvider;

    public String description() { return description; }
    public String agentName() { return agentName; }
    public String projectType() { return projectType; }
    public Boolean includeTests() { return includeTests; }
    public String outputStyle() { return outputStyle; }
    public Boolean applyToProject() { return applyToProject; }
    public String outputRootDir() { return outputRootDir; }
    public Boolean overwriteExisting() { return overwriteExisting; }
    public Boolean restrictToMavenPaths() { return restrictToMavenPaths; }
    public Boolean mergeIfNeeded() { return mergeIfNeeded; }
    public String modelProvider() { return modelProvider; }
}
