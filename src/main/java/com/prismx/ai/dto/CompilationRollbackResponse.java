package com.prismx.ai.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationRollbackResponse {
    private boolean rolledBack;
    private String operationId;
    private List<String> restoredFiles;
    private List<String> errors;

    public boolean rolledBack() { return rolledBack; }
    public String operationId() { return operationId; }
    public List<String> restoredFiles() { return restoredFiles; }
    public List<String> errors() { return errors; }
}
