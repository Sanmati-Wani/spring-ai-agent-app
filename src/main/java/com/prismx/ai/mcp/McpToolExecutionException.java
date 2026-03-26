package com.prismx.ai.mcp;

public class McpToolExecutionException extends RuntimeException {

    private final String toolName;

    public McpToolExecutionException(String toolName, String message) {
        super("MCP tool '" + toolName + "' failed: " + message);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
