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
public class CodeGenRequest {
    @NotBlank(message = "description is required")
    private String description;
    @NotBlank(message = "task is required")
    private String task;
    private String projectType;
    private boolean includeTests;
    private String outputStyle;
    private boolean applyToProject;
    private String outputRootDir;
    private boolean overwriteExisting;
    private boolean restrictToMavenPaths;
    private boolean mergeIfNeeded;
    private String modelProvider;

    public String description() { return description; }
    public String task() { return task; }
    public String projectType() { return projectType; }
    public boolean includeTests() { return includeTests; }
    public String outputStyle() { return outputStyle; }
    public boolean applyToProject() { return applyToProject; }
    public String outputRootDir() { return outputRootDir; }
    public boolean overwriteExisting() { return overwriteExisting; }
    public boolean restrictToMavenPaths() { return restrictToMavenPaths; }
    public boolean mergeIfNeeded() { return mergeIfNeeded; }
    public String modelProvider() { return modelProvider; }
}
