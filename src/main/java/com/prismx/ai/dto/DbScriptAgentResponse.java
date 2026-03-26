package com.prismx.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbScriptAgentResponse {
    private String dbType;
    private String normalizedDbScript;
    private CodeGenResponse codeGenResponse;
    private CompilationFixResponse compilationFixResponse;
}
