package com.prismx.ai.mcp;

import com.prismx.ai.dto.AgentRequest;
import com.prismx.ai.dto.AgentResponse;
import com.prismx.ai.dto.AgentScaffoldRequest;
import com.prismx.ai.dto.CodeGenRequest;
import com.prismx.ai.dto.CodeGenResponse;
import com.prismx.ai.dto.CompilationFixRequest;
import com.prismx.ai.dto.CompilationFixResponse;
import com.prismx.ai.dto.DbScriptAgentRequest;
import com.prismx.ai.dto.DbScriptAgentResponse;
import com.prismx.ai.dto.PrecheckRequest;
import com.prismx.ai.dto.PrecheckResponse;
import com.prismx.ai.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class McpAgentTools {

    private static final Logger log = LoggerFactory.getLogger(McpAgentTools.class);

    private final AgentService agentService;

    public McpAgentTools(AgentService agentService) {
        this.agentService = agentService;
    }

    @McpTool(
            name = "run_agent",
            description = "Run a general-purpose AI agent with a description and task. Returns the agent's text output.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = false
            )
    )
    public AgentResponse runAgent(
            @McpToolParam(description = "High-level description of what the agent should do", required = true) String description,
            @McpToolParam(description = "Specific task for the agent to execute", required = true) String task,
            @McpToolParam(description = "AI model provider (e.g. openai, gemini, anthropic). Leave blank for default.") String modelProvider
    ) {
        validateRequired("description", description);
        validateRequired("task", task);
        log.info("MCP tool invoked: run_agent, description={}", description);
        try {
            AgentRequest request = AgentRequest.builder()
                    .description(description)
                    .task(task)
                    .modelProvider(modelProvider)
                    .build();
            return agentService.runAgent(request);
        } catch (Exception ex) {
            log.error("MCP run_agent failed: {}", ex.getMessage(), ex);
            throw new McpToolExecutionException("run_agent", ex.getMessage());
        }
    }

    @McpTool(
            name = "generate_code",
            description = "Generate production-ready code from a description and task. Supports Spring Boot and Angular projects. Returns generated files, warnings, and errors.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false
            )
    )
    public CodeGenResponse generateCode(
            @McpToolParam(description = "Description of the code to generate", required = true) String description,
            @McpToolParam(description = "Detailed task/requirements for code generation", required = true) String task,
            @McpToolParam(description = "Project type: api, web, library") String projectType,
            @McpToolParam(description = "Whether to include test files") Boolean includeTests,
            @McpToolParam(description = "Whether to write files to disk") Boolean applyToProject,
            @McpToolParam(description = "Output root directory for generated files") String outputRootDir,
            @McpToolParam(description = "AI model provider (e.g. openai, gemini, anthropic)") String modelProvider
    ) {
        validateRequired("description", description);
        validateRequired("task", task);
        log.info("MCP tool invoked: generate_code, description={}", description);
        try {
            CodeGenRequest request = CodeGenRequest.builder()
                    .description(description)
                    .task(task)
                    .projectType(projectType != null ? projectType : "api")
                    .includeTests(includeTests != null ? includeTests : true)
                    .outputStyle("multi-file")
                    .applyToProject(applyToProject != null ? applyToProject : false)
                    .outputRootDir(outputRootDir != null ? outputRootDir : "generated-code")
                    .overwriteExisting(false)
                    .restrictToMavenPaths(true)
                    .mergeIfNeeded(true)
                    .modelProvider(modelProvider)
                    .build();
            return agentService.generateCode(request);
        } catch (Exception ex) {
            log.error("MCP generate_code failed: {}", ex.getMessage(), ex);
            throw new McpToolExecutionException("generate_code", ex.getMessage());
        }
    }

    @McpTool(
            name = "scaffold_agent",
            description = "Scaffold a full Spring Boot agent with controller, service, DTO, and optional repository layers. Returns generated code with detected endpoint details.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false
            )
    )
    public CodeGenResponse scaffoldAgent(
            @McpToolParam(description = "Name for the agent being scaffolded", required = true) String agentName,
            @McpToolParam(description = "Functional description of the agent", required = true) String description,
            @McpToolParam(description = "Absolute path to the target project directory", required = true) String projectPath,
            @McpToolParam(description = "Whether to include JPA repository layer") Boolean includeRepository,
            @McpToolParam(description = "Whether to include test files") Boolean includeTests,
            @McpToolParam(description = "AI model provider (e.g. openai, gemini, anthropic)") String modelProvider
    ) {
        validateRequired("agentName", agentName);
        validateRequired("description", description);
        validateRequired("projectPath", projectPath);
        log.info("MCP tool invoked: scaffold_agent, agentName={}, projectPath={}", agentName, projectPath);
        try {
            AgentScaffoldRequest request = AgentScaffoldRequest.builder()
                    .agentName(agentName)
                    .description(description)
                    .projectPath(projectPath)
                    .includeRepository(includeRepository != null ? includeRepository : false)
                    .includeTests(includeTests != null ? includeTests : true)
                    .overwriteExisting(false)
                    .mergeIfNeeded(true)
                    .modelProvider(modelProvider)
                    .build();
            return agentService.scaffoldAgent(request);
        } catch (Exception ex) {
            log.error("MCP scaffold_agent failed: {}", ex.getMessage(), ex);
            throw new McpToolExecutionException("scaffold_agent", ex.getMessage());
        }
    }

    @McpTool(
            name = "fix_compilation",
            description = "Run an iterative compile-and-fix loop on a project. Automatically detects errors, asks AI for fixes, applies them, and recompiles until success or max iterations.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false
            )
    )
    public CompilationFixResponse fixCompilation(
            @McpToolParam(description = "Absolute path to the project root directory", required = true) String projectPath,
            @McpToolParam(description = "Project type: springboot or angular") String projectType,
            @McpToolParam(description = "Maximum fix iterations (1-100, default 50)") Integer maxIterations,
            @McpToolParam(description = "AI model provider (e.g. openai, gemini, anthropic)") String modelProvider
    ) {
        validateRequired("projectPath", projectPath);
        log.info("MCP tool invoked: fix_compilation, projectPath={}", projectPath);
        try {
            CompilationFixRequest request = CompilationFixRequest.builder()
                    .projectPath(projectPath)
                    .projectType(projectType)
                    .maxIterations(maxIterations != null ? maxIterations : 50)
                    .applyChanges(true)
                    .overwriteExisting(false)
                    .mergeIfNeeded(true)
                    .modelProvider(modelProvider)
                    .build();
            return agentService.fixCompilation(request);
        } catch (Exception ex) {
            log.error("MCP fix_compilation failed: {}", ex.getMessage(), ex);
            throw new McpToolExecutionException("fix_compilation", ex.getMessage());
        }
    }

    @McpTool(
            name = "scaffold_from_db_script",
            description = "Generate a full Spring Boot project from a database script. Creates entities, DTOs, repositories, services, controllers, and migration scripts, then auto-fixes compilation errors.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false
            )
    )
    public DbScriptAgentResponse scaffoldFromDbScript(
            @McpToolParam(description = "Absolute path to the target project directory", required = true) String projectPath,
            @McpToolParam(description = "Database type: postgres, sqlserver, mysql, oracle", required = true) String dbType,
            @McpToolParam(description = "SQL database creation script (CREATE TABLE statements etc.)", required = true) String dbScript,
            @McpToolParam(description = "AI model provider (e.g. openai, gemini, anthropic)") String modelProvider,
            @McpToolParam(description = "Maximum compilation fix iterations") Integer maxIterations
    ) {
        validateRequired("projectPath", projectPath);
        validateRequired("dbType", dbType);
        validateRequired("dbScript", dbScript);
        log.info("MCP tool invoked: scaffold_from_db_script, dbType={}, projectPath={}", dbType, projectPath);
        try {
            DbScriptAgentRequest request = DbScriptAgentRequest.builder()
                    .projectPath(projectPath)
                    .dbType(dbType)
                    .dbScript(dbScript)
                    .modelProvider(modelProvider)
                    .maxIterations(maxIterations != null ? maxIterations : 50)
                    .build();
            return agentService.scaffoldFromDbScript(request);
        } catch (Exception ex) {
            log.error("MCP scaffold_from_db_script failed: {}", ex.getMessage(), ex);
            throw new McpToolExecutionException("scaffold_from_db_script", ex.getMessage());
        }
    }

    @McpTool(
            name = "precheck_project",
            description = "Pre-check a project's structure and build configuration before running compilation fixes. Detects project type, build tool, and validates readiness.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true
            )
    )
    public PrecheckResponse precheckProject(
            @McpToolParam(description = "Absolute path to the project root directory", required = true) String projectPath,
            @McpToolParam(description = "Project type: springboot or angular (auto-detected if blank)") String projectType
    ) {
        validateRequired("projectPath", projectPath);
        log.info("MCP tool invoked: precheck_project, projectPath={}", projectPath);
        try {
            PrecheckRequest request = PrecheckRequest.builder()
                    .projectPath(projectPath)
                    .projectType(projectType)
                    .build();
            return agentService.precheck(request);
        } catch (Exception ex) {
            log.error("MCP precheck_project failed: {}", ex.getMessage(), ex);
            throw new McpToolExecutionException("precheck_project", ex.getMessage());
        }
    }

    private void validateRequired(String paramName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required MCP tool parameter '" + paramName + "' is missing or blank");
        }
    }
}
