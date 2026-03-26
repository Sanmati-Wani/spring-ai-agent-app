package com.prismx.ai.controller;

import com.prismx.ai.dto.AgentRequest;
import com.prismx.ai.dto.AgentResponse;
import com.prismx.ai.dto.AgentScaffoldRequest;
import com.prismx.ai.dto.CompilationFixRequest;
import com.prismx.ai.dto.CompilationFixResponse;
import com.prismx.ai.dto.CompilationRollbackRequest;
import com.prismx.ai.dto.CompilationRollbackResponse;
import com.prismx.ai.dto.CodeGenRequest;
import com.prismx.ai.dto.CodeGenResponse;
import com.prismx.ai.dto.DbScriptAgentRequest;
import com.prismx.ai.dto.DbScriptAgentResponse;
import com.prismx.ai.dto.PrecheckRequest;
import com.prismx.ai.dto.PrecheckResponse;
import com.prismx.ai.dto.RequestBodyTemplateRequest;
import com.prismx.ai.dto.RequestBodyTemplateResponse;
import com.prismx.ai.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public ResponseEntity<AgentResponse> run(@Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(agentService.runAgent(request));
    }

    @PostMapping("/generate-code")
    @Operation(summary = "Generate code from description/task")
    public ResponseEntity<CodeGenResponse> generateCode(@Valid @RequestBody CodeGenRequest request) {
        return ResponseEntity.ok(agentService.generateCode(request));
    }

    @PostMapping("/request-body-template")
    public ResponseEntity<RequestBodyTemplateResponse> requestBodyTemplate(
            @Valid @RequestBody RequestBodyTemplateRequest request
    ) {
        return ResponseEntity.ok(agentService.createRequestBodyTemplate(request));
    }

    @PostMapping("/scaffold")
    public ResponseEntity<CodeGenResponse> scaffold(@Valid @RequestBody AgentScaffoldRequest request) {
        return ResponseEntity.ok(agentService.scaffoldAgent(request));
    }

    @PostMapping("/scaffold-from-db-script")
    @Operation(summary = "Generate and fix project from DB script")
    public ResponseEntity<DbScriptAgentResponse> scaffoldFromDbScript(@Valid @RequestBody DbScriptAgentRequest request) {
        return ResponseEntity.ok(agentService.scaffoldFromDbScript(request));
    }

    @PostMapping("/fix-compilation")
    @Operation(summary = "Run iterative compile and fix loop")
    public ResponseEntity<CompilationFixResponse> fixCompilation(@Valid @RequestBody CompilationFixRequest request) {
        return ResponseEntity.ok(agentService.fixCompilation(request));
    }

    @PostMapping("/rollback")
    public ResponseEntity<CompilationRollbackResponse> rollback(@Valid @RequestBody CompilationRollbackRequest request) {
        return ResponseEntity.ok(agentService.rollbackOperation(request));
    }

    @PostMapping("/precheck")
    @Operation(summary = "Precheck project structure and detected build strategy")
    public ResponseEntity<PrecheckResponse> precheck(@Valid @RequestBody PrecheckRequest request) {
        return ResponseEntity.ok(agentService.precheck(request));
    }
}
