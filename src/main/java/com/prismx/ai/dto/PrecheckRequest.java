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
public class PrecheckRequest {
    @NotBlank(message = "projectPath is required")
    private String projectPath;
    private String projectType;

    public String projectPath() { return projectPath; }
    public String projectType() { return projectType; }
}
