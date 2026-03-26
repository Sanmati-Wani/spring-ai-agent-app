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
public class DbScriptAgentRequest {
    @NotBlank(message = "projectPath is required")
    private String projectPath;
    @NotBlank(message = "dbType is required")
    private String dbType;
    @NotBlank(message = "dbScript is required")
    private String dbScript;
    private String modelProvider;
    private Integer maxIterations;
}
