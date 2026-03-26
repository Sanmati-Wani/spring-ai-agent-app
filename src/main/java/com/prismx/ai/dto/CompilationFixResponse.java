package com.prismx.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationFixResponse {
    @Schema(description = "Whether compilation/build succeeded")
    private boolean fixed;
    @Schema(description = "Final status message")
    private String finalMessage;
    @Schema(description = "Number of iterations executed")
    private int iterationsRun;
    @Schema(description = "Per-iteration details")
    private List<CompilationFixIteration> iterations;
    @Schema(description = "Safety overrides enforced during request")
    private List<String> safetyOverridesApplied;

    public boolean fixed() { return fixed; }
    public String finalMessage() { return finalMessage; }
    public int iterationsRun() { return iterationsRun; }
    public List<CompilationFixIteration> iterations() { return iterations; }
    public List<String> safetyOverridesApplied() { return safetyOverridesApplied; }
}
