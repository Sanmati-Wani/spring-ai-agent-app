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
public class PrecheckResponse {
    @Schema(description = "Whether project is ready for auto-fix")
    private boolean ready;
    @Schema(description = "Resolved absolute project path")
    private String projectPath;
    @Schema(description = "Effective project type", example = "springboot")
    private String projectType;
    @Schema(description = "Detected build command", example = "mvn -q -DskipTests compile")
    private String detectedBuildCommand;
    @Schema(description = "Detected build tool", example = "maven")
    private String detectedBuildTool;
    @Schema(description = "Checks that passed")
    private List<String> checksPassed;
    @Schema(description = "Checks that failed")
    private List<String> checksFailed;
    @Schema(description = "Human-readable recommendation")
    private String recommendation;

    public boolean ready() { return ready; }
    public String projectPath() { return projectPath; }
    public String projectType() { return projectType; }
    public String detectedBuildCommand() { return detectedBuildCommand; }
    public String detectedBuildTool() { return detectedBuildTool; }
    public List<String> checksPassed() { return checksPassed; }
    public List<String> checksFailed() { return checksFailed; }
    public String recommendation() { return recommendation; }
}
