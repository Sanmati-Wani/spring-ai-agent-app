package com.prismx.ai.mcp;

import com.prismx.ai.config.AgentPlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class McpServerResources {

    private static final Logger log = LoggerFactory.getLogger(McpServerResources.class);

    private final AgentPlatformProperties platformProperties;

    public McpServerResources(AgentPlatformProperties platformProperties) {
        this.platformProperties = platformProperties;
    }

    @McpResource(
            uri = "agent://server-info",
            name = "Server Info",
            description = "Returns MCP server metadata: name, version, supported model providers, and capabilities"
    )
    public String getServerInfo() {
        log.debug("MCP resource accessed: server-info");
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "spring-ai-agent-mcp-server");
        info.put("version", "1.0.0");
        info.put("defaultModelProvider", platformProperties.getDefaultModelProvider());
        info.put("capabilities", Map.of(
                "tools", true,
                "resources", true,
                "prompts", false
        ));
        info.put("supportedTools", new String[]{
                "run_agent", "generate_code", "scaffold_agent",
                "fix_compilation", "scaffold_from_db_script", "precheck_project"
        });
        return toJson(info);
    }

    @McpResource(
            uri = "agent://platform-config",
            name = "Platform Configuration",
            description = "Returns current agent platform configuration (non-sensitive values only)"
    )
    public String getPlatformConfig() {
        log.debug("MCP resource accessed: platform-config");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("defaultModelProvider", platformProperties.getDefaultModelProvider());
        config.put("contextProfile", platformProperties.getContextProfile());
        config.put("rateLimitPerMinute", platformProperties.getRateLimitPerMinute());
        config.put("compilerOutputMaxChars", platformProperties.effectiveCompilerOutputMaxChars());
        config.put("aiPromptMaxChars", platformProperties.effectiveAiPromptMaxChars());
        config.put("aiOutputMaxChars", platformProperties.effectiveAiOutputMaxChars());
        config.put("maxFilesPerRun", platformProperties.effectiveMaxFilesPerRun());
        config.put("productionSafetyAlwaysOn", platformProperties.isProductionSafetyAlwaysOn());
        return toJson(config);
    }

    @McpResource(
            uri = "agent://supported-providers",
            name = "Supported Providers",
            description = "Returns list of supported AI model providers and their enabled status"
    )
    public String getSupportedProviders() {
        log.debug("MCP resource accessed: supported-providers");
        return """
                [
                  {"provider":"openai","supportedModels":["gpt-4o","gpt-4o-mini","gpt-5.3-chat-latest"]},
                  {"provider":"gemini","supportedModels":["gemini-1.5-flash","gemini-1.5-pro"]},
                  {"provider":"anthropic","supportedModels":["claude-3-5-sonnet-20241022"]},
                  {"provider":"azure-openai","supportedModels":["gpt-4o"]},
                  {"provider":"ollama","supportedModels":["llama3.1"]},
                  {"provider":"mistral","supportedModels":["mistral-large-latest"]},
                  {"provider":"openrouter","supportedModels":["openai/gpt-4o-mini"]}
                ]""";
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String s) {
                sb.append("\"").append(s).append("\"");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else if (val instanceof String[] arr) {
                sb.append("[");
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(arr[i]).append("\"");
                }
                sb.append("]");
            } else if (val instanceof Map<?, ?> nested) {
                sb.append(toJson(castMap(nested)));
            } else {
                sb.append("\"").append(val).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
