package com.prismx.ai.mcp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP", description = "Model Context Protocol - discover and invoke external MCP server tools")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpClientService mcpClientService;

    public McpController(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }
 @GetMapping("/")
    public String home() {
        return "App is LIVE 🚀";
    }

    @GetMapping("/test")
    public String test() {
        return "Working";
    }
    @GetMapping("/status")
    @Operation(summary = "Get MCP client connection status")
    public ResponseEntity<Map<String, Object>> status() {
        log.info("MCP status requested");
        return ResponseEntity.ok(Map.of(
                "timestamp", Instant.now().toString(),
                "connectedServers", mcpClientService.connectedServerCount(),
                "toolsAvailable", mcpClientService.listAllTools().size(),
                "resourcesAvailable", mcpClientService.listAllResources().size()
        ));
    }

    @GetMapping("/tools")
    @Operation(summary = "List all tools available from connected MCP servers")
    public ResponseEntity<List<McpClientService.McpToolInfo>> listTools() {
        log.info("MCP tools list requested");
        return ResponseEntity.ok(mcpClientService.listAllTools());
    }

    @PostMapping("/tools/call")
    @Operation(summary = "Call a tool on a connected MCP server")
    public ResponseEntity<McpClientService.McpToolCallResult> callTool(
            @Valid @RequestBody McpToolCallRequest request
    ) {
        log.info("MCP tool call requested: toolName={}", request.toolName());
        McpClientService.McpToolCallResult result = mcpClientService.callTool(
                request.toolName(), request.arguments()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/resources")
    @Operation(summary = "List all resources available from connected MCP servers")
    public ResponseEntity<List<McpClientService.McpResourceInfo>> listResources() {
        log.info("MCP resources list requested");
        return ResponseEntity.ok(mcpClientService.listAllResources());
    }

    @ExceptionHandler(McpToolExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleMcpToolError(McpToolExecutionException ex) {
        log.error("MCP tool execution error: {}", ex.getMessage());
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "MCP_TOOL_EXECUTION_FAILED",
                "tool", ex.getToolName(),
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidationError(IllegalArgumentException ex) {
        log.warn("MCP validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "error", "MCP_VALIDATION_FAILED",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    public record McpToolCallRequest(
            @NotBlank(message = "toolName is required")
            String toolName,
            Map<String, Object> arguments
    ) {}
}
