package com.prismx.ai.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class McpClientService {

    private static final Logger log = LoggerFactory.getLogger(McpClientService.class);

    private final List<McpSyncClient> mcpClients;

    public McpClientService(ObjectProvider<List<McpSyncClient>> mcpClientsProvider) {
        List<McpSyncClient> resolved = mcpClientsProvider.getIfAvailable();
        this.mcpClients = resolved != null ? resolved : Collections.emptyList();
        if (this.mcpClients.isEmpty()) {
            log.info("McpClientService initialized with no external MCP servers configured");
        } else {
            log.info("McpClientService initialized with {} connected MCP server(s)", this.mcpClients.size());
        }
    }

    public List<McpToolInfo> listAllTools() {
        List<McpToolInfo> tools = new ArrayList<>();
        for (McpSyncClient client : mcpClients) {
            try {
                McpSchema.ListToolsResult result = client.listTools();
                if (result != null && result.tools() != null) {
                    String serverName = resolveServerName(client);
                    for (McpSchema.Tool tool : result.tools()) {
                        tools.add(McpToolInfo.builder()
                                .name(tool.name())
                                .description(tool.description())
                                .inputSchema(tool.inputSchema() != null ? tool.inputSchema().toString() : null)
                                .serverName(serverName)
                                .build());
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to list tools from MCP client: {}", ex.getMessage());
            }
        }
        return tools;
    }

    public McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isBlank()) {
            return McpToolCallResult.builder()
                    .toolName("")
                    .success(false)
                    .content(List.of("toolName is required"))
                    .build();
        }
        for (McpSyncClient client : mcpClients) {
            try {
                McpSchema.ListToolsResult listResult = client.listTools();
                if (listResult == null || listResult.tools() == null) {
                    continue;
                }
                boolean hasTool = listResult.tools().stream()
                        .anyMatch(t -> t.name().equals(toolName));
                if (!hasTool) {
                    continue;
                }

                String serverName = resolveServerName(client);
                log.info("Calling MCP tool '{}' on server '{}'", toolName, serverName);
                CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
                List<String> content = extractContent(result);
                return McpToolCallResult.builder()
                        .toolName(toolName)
                        .success(!Boolean.TRUE.equals(result.isError()))
                        .content(content)
                        .build();
            } catch (Exception ex) {
                log.error("Error calling MCP tool '{}': {}", toolName, ex.getMessage(), ex);
                return McpToolCallResult.builder()
                        .toolName(toolName)
                        .success(false)
                        .content(List.of("Error: " + ex.getMessage()))
                        .build();
            }
        }
        log.warn("MCP tool '{}' not found on any connected server", toolName);
        return McpToolCallResult.builder()
                .toolName(toolName)
                .success(false)
                .content(List.of("Tool not found on any connected MCP server: " + toolName))
                .build();
    }

    public List<McpResourceInfo> listAllResources() {
        List<McpResourceInfo> resources = new ArrayList<>();
        for (McpSyncClient client : mcpClients) {
            try {
                McpSchema.ListResourcesResult result = client.listResources();
                if (result != null && result.resources() != null) {
                    String serverName = resolveServerName(client);
                    for (McpSchema.Resource resource : result.resources()) {
                        resources.add(McpResourceInfo.builder()
                                .uri(resource.uri())
                                .name(resource.name())
                                .description(resource.description())
                                .mimeType(resource.mimeType())
                                .serverName(serverName)
                                .build());
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to list resources from MCP client: {}", ex.getMessage());
            }
        }
        return resources;
    }

    public int connectedServerCount() {
        return mcpClients.size();
    }

    private String resolveServerName(McpSyncClient client) {
        try {
            return client.getServerInfo() != null ? client.getServerInfo().name() : "unknown";
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private List<String> extractContent(CallToolResult result) {
        List<String> content = new ArrayList<>();
        if (result.content() != null) {
            for (McpSchema.Content c : result.content()) {
                if (c instanceof McpSchema.TextContent tc) {
                    content.add(tc.text());
                } else {
                    content.add(c.toString());
                }
            }
        }
        return content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpToolInfo {
        private String name;
        private String description;
        private String inputSchema;
        private String serverName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpToolCallResult {
        private String toolName;
        private boolean success;
        private List<String> content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpResourceInfo {
        private String uri;
        private String name;
        private String description;
        private String mimeType;
        private String serverName;
    }
}
