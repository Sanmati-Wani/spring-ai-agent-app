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
public class CompilationFixRequest {
    @NotBlank(message = "projectPath is required")
    private String projectPath;
    private String projectType;
    private Integer maxIterations;
    private Boolean applyChanges;
    private Boolean overwriteExisting;
    private Boolean mergeIfNeeded;
    private String modelProvider;

    public String projectPath() { return projectPath; }
    public String projectType() { return projectType; }
    public Integer maxIterations() { return maxIterations; }
    public Boolean applyChanges() { return applyChanges; }
    public Boolean overwriteExisting() { return overwriteExisting; }
    public Boolean mergeIfNeeded() { return mergeIfNeeded; }
    public String modelProvider() { return modelProvider; }
}
