package com.prismx.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.platform")
public class AgentPlatformProperties {

    private String apiKey = "";
    private String defaultModelProvider = "openai";
    private int rateLimitPerMinute = 60;
    private String auditDir = ".agent-audit";
    private int compilerOutputMaxChars = 50000;
    private int aiPromptMaxChars = 50000;
    private int aiOutputMaxChars = 50000;
    private int errorChunkMaxChars = 15000;
    private int maxChunksPerIteration = 4;
    private int maxFilesPerRun = 120;
    private String contextProfile = "balanced";
    private boolean productionSafetyAlwaysOn = false;

    public void setApiKey(String apiKey) {
        this.apiKey = sanitizeHeaderValue(apiKey);
    }

    public int effectiveCompilerOutputMaxChars() {
        if (compilerOutputMaxChars > 0) {
            return compilerOutputMaxChars;
        }
        return "aggressive".equalsIgnoreCase(contextProfile) ? 100000 : 50000;
    }

    public int effectiveAiPromptMaxChars() {
        if (aiPromptMaxChars > 0) {
            return aiPromptMaxChars;
        }
        return "aggressive".equalsIgnoreCase(contextProfile) ? 80000 : 50000;
    }

    public int effectiveAiOutputMaxChars() {
        if (aiOutputMaxChars > 0) {
            return aiOutputMaxChars;
        }
        return "aggressive".equalsIgnoreCase(contextProfile) ? 80000 : 50000;
    }

    public int effectiveErrorChunkMaxChars() {
        if (errorChunkMaxChars > 0) {
            return errorChunkMaxChars;
        }
        return "aggressive".equalsIgnoreCase(contextProfile) ? 25000 : 15000;
    }

    public int effectiveMaxChunksPerIteration() {
        if (maxChunksPerIteration > 0) {
            return maxChunksPerIteration;
        }
        return "aggressive".equalsIgnoreCase(contextProfile) ? 8 : 4;
    }

    public int effectiveMaxFilesPerRun() {
        if (maxFilesPerRun > 0) {
            return maxFilesPerRun;
        }
        return 120;
    }

    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "").replace("\n", "").trim();
    }
}
