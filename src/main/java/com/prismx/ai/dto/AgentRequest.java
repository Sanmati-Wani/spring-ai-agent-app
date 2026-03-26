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
public class AgentRequest {
    @NotBlank(message = "description is required")
    private String description;
    @NotBlank(message = "task is required")
    private String task;
    private String modelProvider;

    public String description() { return description; }
    public String task() { return task; }
    public String modelProvider() { return modelProvider; }
}
