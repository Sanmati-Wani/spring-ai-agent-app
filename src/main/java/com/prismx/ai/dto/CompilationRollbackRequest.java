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
public class CompilationRollbackRequest {
    @NotBlank(message = "operationId is required")
    private String operationId;
    @NotBlank(message = "projectPath is required")
    private String projectPath;

    public String operationId() { return operationId; }
    public String projectPath() { return projectPath; }
}
