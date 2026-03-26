package com.prismx.ai.service;

import com.prismx.ai.dto.AgentRequest;
import com.prismx.ai.dto.AgentResponse;
import com.prismx.ai.dto.AgentScaffoldRequest;
import com.prismx.ai.dto.CodeGenRequest;
import com.prismx.ai.dto.CodeGenResponse;
import com.prismx.ai.dto.DbScriptAgentRequest;
import com.prismx.ai.dto.DbScriptAgentResponse;
import com.prismx.ai.dto.EndpointDetails;
import com.prismx.ai.dto.CompilationFixIteration;
import com.prismx.ai.dto.CompilationFixRequest;
import com.prismx.ai.dto.CompilationFixResponse;
import com.prismx.ai.dto.CompilationRollbackRequest;
import com.prismx.ai.dto.CompilationRollbackResponse;
import com.prismx.ai.dto.PrecheckRequest;
import com.prismx.ai.dto.PrecheckResponse;
import com.prismx.ai.config.AgentPlatformProperties;
import com.prismx.ai.dto.RequestBodyTemplateRequest;
import com.prismx.ai.dto.RequestBodyTemplateResponse;
import com.prismx.ai.tool.AgentTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final Pattern TEMPLATE_HEADER = Pattern.compile("^(\\w+)\\(([^)]*)\\)\\s*::=\\s*<<\\s*$");
    private static final List<String> MAVEN_ALLOWED_PREFIXES = List.of(
            "src/main/java/",
            "src/main/resources/",
            "src/test/java/"
    );

    private final ChatLanguageModel chatLanguageModel;
    private final AgentTools agentTools;
    private final ApplicationContext applicationContext;
    private final MeterRegistry meterRegistry;
    private final AgentPlatformProperties platformProperties;
    private final Map<String, ReentrantLock> projectLocks = new ConcurrentHashMap<>();
    private final Map<String, PromptTemplateDef> promptTemplates;

    public AgentService(
            ChatLanguageModel chatLanguageModel,
            AgentTools agentTools,
            ApplicationContext applicationContext,
            MeterRegistry meterRegistry,
            AgentPlatformProperties platformProperties
    ) {
        this.chatLanguageModel = chatLanguageModel;
        this.agentTools = agentTools;
        this.applicationContext = applicationContext;
        this.meterRegistry = meterRegistry;
        this.platformProperties = platformProperties;
        this.promptTemplates = loadPromptTemplates();
    }

    public AgentResponse runAgent(AgentRequest request) {
        String systemPrompt = renderPrompt("agentSystem", Map.of(
                "description", request.description()
        ));
        String userPrompt = renderPrompt("agentUser", Map.of(
                "task", request.task()
        ));
        ChatLanguageModel model = resolveModel(request.modelProvider());
        String output = buildAgentAssistant(model).execute(systemPrompt, userPrompt);
        return new AgentResponse(output);
    }

    public CodeGenResponse generateCode(CodeGenRequest request) {
        String generationId = UUID.randomUUID().toString();
        String generatedAt = Instant.now().toString();
        String projectType = valueOrDefault(request.projectType(), "api");
        String outputStyle = valueOrDefault(request.outputStyle(), "multi-file");
        String includeTests = request.includeTests() ? "yes" : "no";
        String outputRootDir = valueOrDefault(request.outputRootDir(), "generated-code");
        boolean overwriteExisting = request.overwriteExisting();
        boolean restrictToMavenPaths = request.restrictToMavenPaths();
        boolean mergeIfNeeded = request.mergeIfNeeded();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> safetyOverridesApplied = new ArrayList<>();
        boolean productionSafety = platformProperties.isProductionSafetyAlwaysOn();

        if (productionSafety) {
            if (overwriteExisting) {
                warnings.add("productionSafetyAlwaysOn enforced overwriteExisting=false.");
                safetyOverridesApplied.add("overwriteExisting=false");
            }
            if (!restrictToMavenPaths) {
                warnings.add("productionSafetyAlwaysOn enforced restrictToMavenPaths=true.");
                safetyOverridesApplied.add("restrictToMavenPaths=true");
            }
            if (!mergeIfNeeded) {
                warnings.add("productionSafetyAlwaysOn enforced mergeIfNeeded=true.");
                safetyOverridesApplied.add("mergeIfNeeded=true");
            }
            overwriteExisting = false;
            restrictToMavenPaths = true;
            mergeIfNeeded = true;
        }

        String systemPrompt = renderPrompt("codeGenSystem", Map.of(
                "description", request.description()
        ));
        String userPrompt = renderPrompt("codeGenUser", Map.of(
                "task", request.task(),
                "projectType", projectType,
                "includeTests", includeTests,
                "outputStyle", outputStyle
        ));

        ChatLanguageModel model = resolveModel(request.modelProvider());
        String output = generateWithRetry(
                model,
                systemPrompt,
                userPrompt,
                3,
                platformProperties.effectiveAiPromptMaxChars(),
                platformProperties.effectiveAiOutputMaxChars()
        );
        List<GeneratedFile> parsedFiles = parseGeneratedFiles(output);
        if (parsedFiles.isEmpty()) {
            errors.add("No valid FILE blocks found. Expected: FILE: <path> followed by fenced code block.");
        }
        warnings.addAll(validateGeneratedStandards(parsedFiles));
        int maxFilesPerRun = platformProperties.effectiveMaxFilesPerRun();
        if (productionSafety && parsedFiles.size() > maxFilesPerRun) {
            errors.add("Aborted write: generated file count " + parsedFiles.size()
                    + " exceeds production safety limit " + maxFilesPerRun + ".");
        }

        List<String> filesCreated = List.of();
        String folderStructure = "";
        if (request.applyToProject() && errors.isEmpty()) {
            Path rootPath = Paths.get("").toAbsolutePath().normalize().resolve(outputRootDir).normalize();
            long startNs = System.nanoTime();
            ReentrantLock lock = projectLocks.computeIfAbsent(rootPath.toString(), key -> new ReentrantLock());
            lock.lock();
            try {
                FileWriteResult writeResult = writeGeneratedFiles(
                        rootPath,
                        parsedFiles,
                        overwriteExisting,
                        restrictToMavenPaths,
                        mergeIfNeeded,
                        generationId
                );
                filesCreated = writeResult.writtenFiles();
                warnings.addAll(writeResult.warnings());
                errors.addAll(writeResult.errors());
            } finally {
                lock.unlock();
            }
            meterRegistry.timer("agent.codegen.write.duration").record(System.nanoTime() - startNs, java.util.concurrent.TimeUnit.NANOSECONDS);
            folderStructure = buildFolderTree(rootPath);
        }

        return new CodeGenResponse(
                generationId,
                generatedAt,
                null,
                output,
                filesCreated,
                List.of(),
                folderStructure,
                warnings,
                errors,
                safetyOverridesApplied
        );
    }

    public RequestBodyTemplateResponse createRequestBodyTemplate(RequestBodyTemplateRequest request) {
        String agentName = valueOrDefault(request.agentName(), "Code Generation Agent");
        String projectType = valueOrDefault(request.projectType(), "api");
        String outputStyle = valueOrDefault(request.outputStyle(), "multi-file");
        boolean includeTests = request.includeTests() == null || request.includeTests();
        boolean applyToProject = request.applyToProject() != null && request.applyToProject();
        boolean overwriteExisting = request.overwriteExisting() != null && request.overwriteExisting();
        boolean restrictToMavenPaths = request.restrictToMavenPaths() == null || request.restrictToMavenPaths();
        boolean mergeIfNeeded = request.mergeIfNeeded() != null && request.mergeIfNeeded();
        String outputRootDir = valueOrDefault(request.outputRootDir(), "generated-code");

        String generatedDescription = "You are %s. Generate production-ready Spring Boot 3.5.x and Java 17 code."
                .formatted(agentName);
        String generatedTask = renderPrompt("requestTemplateTask", Map.of(
                "description", request.description().trim()
        ));

        CodeGenRequest codeGenRequest = new CodeGenRequest(
                generatedDescription,
                generatedTask,
                projectType,
                includeTests,
                outputStyle,
                applyToProject,
                outputRootDir,
                overwriteExisting,
                restrictToMavenPaths,
                mergeIfNeeded,
                request.modelProvider()
        );

        return new RequestBodyTemplateResponse(
                agentName,
                codeGenRequest,
                List.of(
                        "Review task text before calling /api/agent/generate-code.",
                        "Use applyToProject=false first to preview generated files.",
                        "Switch applyToProject=true only after validating FILE paths."
                )
        );
    }

    public CodeGenResponse scaffoldAgent(AgentScaffoldRequest request) {
        boolean includeRepository = request.includeRepository() != null && request.includeRepository();
        boolean includeTests = request.includeTests() == null || request.includeTests();
        boolean overwriteExisting = request.overwriteExisting() != null && request.overwriteExisting();
        boolean mergeIfNeeded = request.mergeIfNeeded() == null || request.mergeIfNeeded();
        String includeRepositoryText = includeRepository ? "true" : "false";
        String task = renderPrompt("scaffoldTask", Map.of(
                "agentName", request.agentName().trim(),
                "description", request.description().trim(),
                "includeRepository", includeRepositoryText
        ));

        CodeGenRequest codeGenRequest = new CodeGenRequest(
                "You are %s code generation assistant.".formatted(request.agentName().trim()),
                task,
                "api",
                includeTests,
                "multi-file",
                true,
                request.projectPath().trim(),
                overwriteExisting,
                true,
                mergeIfNeeded,
                request.modelProvider()
        );
        CodeGenResponse generated = generateCode(codeGenRequest);
        List<Map<String, String>> generatedEndpointDetails = extractGeneratedEndpointDetails(generated.output());
        List<EndpointDetails> createdEndpoints = generatedEndpointDetails.stream()
                .map(item -> EndpointDetails.builder()
                        .url(Map.of(
                                "method", item.getOrDefault("method", "ANY"),
                                "path", item.getOrDefault("path", "/")
                        ))
                        .request(Map.of(
                                "type", item.getOrDefault("requestType", "none")
                        ))
                        .response(Map.of(
                                "type", item.getOrDefault("responseType", "unknown")
                        ))
                        .build())
                .distinct()
                .toList();

        return new CodeGenResponse(
                generated.generationId(),
                generated.generatedAt(),
                request.agentName().trim(),
                generated.output(),
                generated.filesCreated(),
                createdEndpoints,
                generated.folderStructure(),
                generated.warnings(),
                generated.errors(),
                generated.safetyOverridesApplied()
        );
    }

    public DbScriptAgentResponse scaffoldFromDbScript(DbScriptAgentRequest request) {
        String dbType = valueOrDefault(request.getDbType(), "postgres");
        String normalizedScript = normalizeDbScript(request.getDbScript(), dbType, request.getModelProvider());
        String task = renderPrompt("dbScriptScaffoldTask", Map.of(
                "dbType", dbType,
                "dbScript", normalizedScript
        ));

        CodeGenRequest codeGenRequest = new CodeGenRequest(
                "You are a database-driven scaffolding agent.",
                task,
                "api",
                true,
                "multi-file",
                true,
                request.getProjectPath().trim(),
                false,
                true,
                true,
                request.getModelProvider()
        );
        CodeGenResponse codeGenResponse = generateCode(codeGenRequest);

        CompilationFixRequest fixRequest = new CompilationFixRequest(
                request.getProjectPath().trim(),
                "springboot",
                request.getMaxIterations(),
                true,
                false,
                true,
                request.getModelProvider()
        );
        CompilationFixResponse fixResponse = fixCompilation(fixRequest);

        return new DbScriptAgentResponse(
                dbType,
                normalizedScript,
                codeGenResponse,
                fixResponse
        );
    }

    private String normalizeDbScript(String script, String dbType, String modelProvider) {
        if (script == null || script.isBlank()) {
            return "";
        }
        try {
            ChatLanguageModel model = resolveModel(modelProvider);
            String normalized = generateWithRetry(
                    model,
                    "You are a SQL validator. Return only corrected SQL script text.",
                    "Validate and correct this " + dbType + " SQL script. Keep semantics, fix syntax if needed. Return SQL only:\n\n" + script,
                    2,
                    12000,
                    12000
            );
            if (normalized == null || normalized.isBlank()) {
                return script;
            }
            return normalized.trim();
        } catch (Exception ex) {
            log.warn("Failed to normalize db script, using original: {}", ex.getMessage());
            return script;
        }
    }

   private List<Map<String, String>> extractGeneratedEndpointDetails(String output) {
    if (output == null || output.isBlank()) {
        return List.of();
    }

    List<Map<String, String>> endpoints = new ArrayList<>();
    List<String> classPaths = extractClassPaths(output);
    Pattern controllerMethodPattern = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\s*\\(([^)]*)\\)\\s*(?:@[A-Za-z0-9_$.]+(?:\\([^)]*\\))?\\s*)*public\\s+([A-Za-z0-9_$.<>?,\\s]+?)\\s+\\w+\\s*\\(([^)]*)\\)",
            Pattern.DOTALL
    );
    Matcher matcher = controllerMethodPattern.matcher(output);
    while (matcher.find()) {
        String annotation = matcher.group(1);
        String args = matcher.group(2);
        String responseType = matcher.group(3).trim().replaceAll("\\s+", " ");
        String params = matcher.group(4) == null ? "" : matcher.group(4);
        String requestType = extractControllerRequestType(params);

        List<String> methodPaths = extractPathsFromAnnotationArgs(args);
        List<String> httpMethods = extractHttpMethods(annotation, args);
        for (String base : classPaths) {
            for (String methodPath : methodPaths) {
                for (String method : httpMethods) {
                    endpoints.add(Map.of(
                            "method", method,
                            "path", normalizePath(base + "/" + methodPath),
                            "requestType", requestType,
                            "responseType", responseType
                    ));
                }
            }
        }
    }

    return endpoints.stream().distinct().toList();
}

    private String extractControllerRequestType(String params) {
        Matcher requestBodyMatcher = Pattern.compile("@RequestBody\\s+([A-Za-z0-9_$.<>]+)").matcher(params);
        if (requestBodyMatcher.find()) {
            return requestBodyMatcher.group(1);
        }
        Matcher requestParamMatcher = Pattern.compile("@RequestParam(?:\\([^)]*\\))?\\s+([A-Za-z0-9_$.<>]+)").matcher(params);
        if (requestParamMatcher.find()) {
            return requestParamMatcher.group(1);
        }
        return "none";
    }
    private List<String> extractClassPaths(String output) {
        Pattern classMapping = Pattern.compile("@RequestMapping\\s*\\(([^)]*)\\)");
        Matcher matcher = classMapping.matcher(output);
        if (matcher.find()) {
            List<String> paths = extractPathsFromAnnotationArgs(matcher.group(1));
            return paths.isEmpty() ? List.of("") : paths;
        }
        return List.of("");
    }

    private List<String> extractPathsFromAnnotationArgs(String annotationArgs) {
        if (annotationArgs == null || annotationArgs.isBlank()) {
            return List.of("");
        }
        List<String> paths = new ArrayList<>();
        Matcher quoted = Pattern.compile("\"([^\"]*)\"").matcher(annotationArgs);
        while (quoted.find()) {
            paths.add(quoted.group(1));
        }
        if (paths.isEmpty()) {
            return List.of("");
        }
        return paths;
    }

    private List<String> extractHttpMethods(String annotation, String annotationArgs) {
        if (!"RequestMapping".equals(annotation)) {
            return List.of(annotation.replace("Mapping", "").toUpperCase(Locale.ROOT));
        }
        List<String> methods = new ArrayList<>();
        Matcher methodMatcher = Pattern.compile("RequestMethod\\.([A-Z]+)").matcher(annotationArgs);
        while (methodMatcher.find()) {
            methods.add(methodMatcher.group(1));
        }
        if (methods.isEmpty()) {
            return List.of("ANY");
        }
        return methods;
    }

    private String normalizePath(String path) {
        String normalized = path == null ? "" : path.replaceAll("/+", "/").trim();
        if (normalized.isBlank()) {
            return "/";
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private List<String> validateGeneratedStandards(List<GeneratedFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        boolean hasExceptionAdvice = false;

        for (GeneratedFile file : files) {
            String path = file.relativePath().replace("\\", "/");
            String content = file.content();

            if (path.contains("/controller/") && path.endsWith(".java")) {
                if (!content.contains("ResponseEntity<")) {
                    warnings.add("API standard: controller should return ResponseEntity<?> -> " + path);
                }
                if (!content.contains("LoggerFactory.getLogger(") || !content.contains("Logger ")) {
                    warnings.add("API standard: controller should use SLF4J logger -> " + path);
                }
            }

            if (path.contains("/service/") && path.endsWith(".java")) {
                if (!content.contains("LoggerFactory.getLogger(") || !content.contains("Logger ")) {
                    warnings.add("API standard: service should use SLF4J logger -> " + path);
                }
            }

            if (content.contains("@Configuration") || content.contains("@ConfigurationProperties")) {
                if (!path.contains("/config/")) {
                    warnings.add("API standard: configuration classes should be under config package -> " + path);
                }
            }

            if (content.contains("@RestControllerAdvice") || content.contains("@ExceptionHandler")) {
                hasExceptionAdvice = true;
                if (!path.contains("/exception/")) {
                    warnings.add("API standard: exception handlers should be under exception package -> " + path);
                }
            }

            if (path.endsWith("Exception.java") && !path.contains("/exception/")) {
                warnings.add("API standard: exception classes should be under exception package -> " + path);
            }
        }

        if (!hasExceptionAdvice) {
            warnings.add("API standard: missing global exception handler (@RestControllerAdvice) in exception package.");
        }
        return warnings.stream().distinct().toList();
    }

    public CompilationFixResponse fixCompilation(CompilationFixRequest request) {
        Path projectPath = Paths.get(request.projectPath().trim()).toAbsolutePath().normalize();
        String projectType = request.projectType() == null ? "" : request.projectType().trim().toLowerCase(Locale.ROOT);
        int maxIterations = request.maxIterations() == null ? 50 : Math.max(1, Math.min(request.maxIterations(), 100));
        boolean applyChanges = true;
        boolean overwriteExisting = request.overwriteExisting() != null && request.overwriteExisting();
        boolean mergeIfNeeded = request.mergeIfNeeded() == null || request.mergeIfNeeded();
        List<CompilationFixIteration> iterations = new ArrayList<>();
        List<String> safetyOverridesApplied = new ArrayList<>();
        String operationId = "fix-" + UUID.randomUUID();
        boolean productionSafety = platformProperties.isProductionSafetyAlwaysOn();

        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            return new CompilationFixResponse(false, "Project path not found: " + projectPath, 0, iterations, safetyOverridesApplied);
        }

        if (projectType.isBlank()) {
            projectType = detectProjectType(projectPath);
            if (projectType.isBlank()) {
                return new CompilationFixResponse(false,
                        "Could not detect projectType. Use 'springboot' or 'angular'.", 0, iterations, safetyOverridesApplied);
            }
            safetyOverridesApplied.add("projectType=" + projectType);
        }

        if (productionSafety) {
            PrecheckResponse precheck = precheck(new PrecheckRequest(projectPath.toString(), projectType));
            if (!precheck.ready()) {
                return new CompilationFixResponse(false,
                        "Precheck failed in production safety mode: " + String.join(" | ", precheck.checksFailed()),
                        0,
                        iterations,
                        safetyOverridesApplied);
            }
            if (overwriteExisting) {
                safetyOverridesApplied.add("overwriteExisting=false");
            }
            if (!mergeIfNeeded) {
                safetyOverridesApplied.add("mergeIfNeeded=true");
            }
            overwriteExisting = false;
            mergeIfNeeded = true;
        }
        final String effectiveProjectType = projectType;

        ChatLanguageModel model = resolveModel(request.modelProvider());
        for (int i = 1; i <= maxIterations; i++) {
            long iterStart = System.nanoTime();
            AgentTools.CommandExecutionResult compile = agentTools.runBuildForProjectType(
                    projectPath.toString(),
                    effectiveProjectType,
                    180
            );
            String command = compile.command();
            if (command == null || command.isBlank()) {
                return new CompilationFixResponse(false, "Unsupported projectType. Use 'springboot' or 'angular'.", 0, iterations, safetyOverridesApplied);
            }
            if (compile.success()) {
                iterations.add(new CompilationFixIteration(
                        i,
                        command,
                        true,
                        List.of(),
                        List.of(),
                        List.of(),
                        limitText(compile.output(), 4000)
                ));
                return new CompilationFixResponse(true, "Compilation/build succeeded.", i, iterations, safetyOverridesApplied);
            }

            List<String> warnings = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            List<String> filesChanged = new ArrayList<>();

            String boundedCompilerOutput = limitText(compile.output(), platformProperties.effectiveCompilerOutputMaxChars());
            List<String> chunks = chunkText(
                    boundedCompilerOutput,
                    Math.max(platformProperties.effectiveErrorChunkMaxChars(), 2000)
            );
            int maxChunks = Math.max(platformProperties.effectiveMaxChunksPerIteration(), 1);
            int processedChunks = 0;
            for (String chunk : chunks) {
                if (processedChunks >= maxChunks) {
                    warnings.add("Compiler output has more chunks; continuing in next iteration.");
                    break;
                }
                processedChunks++;
                String fixTask = renderPrompt("compileFixTask", Map.of(
                        "projectType", effectiveProjectType,
                        "compilerOutput", chunk
                ));
                String aiOutput = generateWithRetry(
                        model,
                        "You fix compile errors and return only FILE blocks.",
                        fixTask,
                        2,
                        platformProperties.effectiveAiPromptMaxChars(),
                        platformProperties.effectiveAiOutputMaxChars()
                );
                List<GeneratedFile> parsed = parseGeneratedFiles(aiOutput);
                if (parsed.isEmpty()) {
                    warnings.add("No valid FILE blocks for chunk " + processedChunks + ".");
                    continue;
                }
                if (applyChanges) {
                    List<GeneratedFile> allowed = parsed.stream()
                            .filter(file -> isAllowedForProjectType(file.relativePath().replace('\\', '/'), effectiveProjectType))
                            .toList();
                    if (allowed.size() < parsed.size()) {
                        warnings.add("Some generated files were skipped due to project-type path restrictions.");
                    }
                    FileWriteResult write = writeGeneratedFiles(
                            projectPath,
                            allowed,
                            overwriteExisting,
                            "springboot".equals(effectiveProjectType),
                            mergeIfNeeded,
                            operationId + "-it" + i + "-c" + processedChunks
                    );
                    filesChanged = mergeDistinct(filesChanged, write.writtenFiles());
                    warnings.addAll(write.warnings());
                    errors.addAll(write.errors());
                } else {
                    warnings.add("applyChanges=false, no files were written.");
                }
            }
            meterRegistry.timer("agent.fix.iteration.duration").record(System.nanoTime() - iterStart, java.util.concurrent.TimeUnit.NANOSECONDS);
            if (!errors.isEmpty()) {
                meterRegistry.counter("agent.fix.iteration.errors").increment();
            }

            iterations.add(new CompilationFixIteration(
                    i,
                    command,
                    false,
                    filesChanged,
                    warnings,
                    errors,
                    limitText(compile.output(), Math.min(platformProperties.effectiveCompilerOutputMaxChars(), 4000))
            ));
        }

        return new CompilationFixResponse(false, "Max iterations reached; compilation still failing.", maxIterations, iterations, safetyOverridesApplied);
    }

    public CompilationRollbackResponse rollbackOperation(CompilationRollbackRequest request) {
        Path rootPath = Paths.get(request.projectPath().trim()).toAbsolutePath().normalize();
        Path manifestPath = rootPath.resolve(platformProperties.getAuditDir())
                .resolve(request.operationId().trim())
                .resolve("manifest.txt");
        List<String> restored = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (!Files.exists(manifestPath)) {
            return new CompilationRollbackResponse(false, request.operationId(), restored, List.of("Manifest not found: " + manifestPath));
        }

        ReentrantLock lock = projectLocks.computeIfAbsent(rootPath.toString(), key -> new ReentrantLock());
        lock.lock();
        try (BufferedReader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("timestamp=") || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                String relative = parts[0];
                boolean existedBefore = Boolean.parseBoolean(parts[1]);
                Path target = rootPath.resolve(relative).normalize();
                Path backup = Paths.get(parts[2]);
                try {
                    if (existedBefore) {
                        if (Files.exists(backup)) {
                            Path parent = target.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                            restored.add(relative);
                        } else {
                            errors.add("Missing backup for: " + relative);
                        }
                    } else {
                        Files.deleteIfExists(target);
                        restored.add(relative);
                    }
                } catch (Exception ex) {
                    errors.add("Failed restore for " + relative + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            errors.add("Failed to read manifest: " + ex.getMessage());
        } finally {
            lock.unlock();
        }
        return new CompilationRollbackResponse(errors.isEmpty(), request.operationId(), restored, errors);
    }

    public PrecheckResponse precheck(PrecheckRequest request) {
        Path projectPath = Paths.get(request.projectPath().trim()).toAbsolutePath().normalize();
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        String projectType = request.projectType() == null ? "" : request.projectType().trim().toLowerCase(Locale.ROOT);
        String detectedBuildCommand = "";
        String detectedBuildTool = "";

        if (Files.exists(projectPath) && Files.isDirectory(projectPath)) {
            passed.add("Project path exists and is a directory.");
        } else {
            failed.add("Project path not found or not a directory.");
            return new PrecheckResponse(false, projectPath.toString(), projectType, detectedBuildCommand, detectedBuildTool, passed, failed,
                    "Fix projectPath before running compilation fixes.");
        }

        if (projectType.isBlank()) {
            projectType = detectProjectType(projectPath);
            if (projectType.isBlank()) {
                failed.add("Could not detect projectType. Provide 'springboot' or 'angular'.");
                return new PrecheckResponse(false, projectPath.toString(), "", detectedBuildCommand, detectedBuildTool, passed, failed,
                        "Set projectType explicitly when project structure is non-standard.");
            }
            passed.add("Auto-detected projectType: " + projectType + ".");
        }

        if ("springboot".equals(projectType)) {
            checkFileExists(projectPath.resolve("pom.xml"), "Found pom.xml", "Missing pom.xml", passed, failed);
            String mvnCommand = agentTools.detectBuildCommandForProjectType(projectPath.toString(), "springboot");
            if (mvnCommand != null && !mvnCommand.isBlank()) {
                detectedBuildCommand = mvnCommand;
                detectedBuildTool = detectBuildTool(mvnCommand);
                passed.add("Build command resolved for Spring Boot.");
            } else {
                failed.add("Could not resolve Spring Boot build command.");
            }
        } else if ("angular".equals(projectType)) {
            checkFileExists(projectPath.resolve("package.json"), "Found package.json", "Missing package.json", passed, failed);
            checkFileExists(projectPath.resolve("angular.json"), "Found angular.json", "Missing angular.json", passed, failed);
            String npmCommand = agentTools.detectBuildCommandForProjectType(projectPath.toString(), "angular");
            if (npmCommand != null && !npmCommand.isBlank()) {
                detectedBuildCommand = npmCommand;
                detectedBuildTool = detectBuildTool(npmCommand);
                passed.add("Build command resolved for Angular.");
            } else {
                failed.add("Could not resolve Angular build command.");
            }
        } else {
            failed.add("Unsupported projectType. Use springboot or angular.");
        }

        boolean ready = failed.isEmpty();
        String recommendation = ready
                ? "Precheck passed. You can run /api/agent/fix-compilation."
                : "Precheck failed. Resolve failed checks before running auto-fix.";
        return new PrecheckResponse(ready, projectPath.toString(), projectType, detectedBuildCommand, detectedBuildTool, passed, failed, recommendation);
    }

    private String detectBuildTool(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("mvn ")) {
            return "maven";
        }
        if (normalized.contains("gradlew") || normalized.startsWith("gradle ")) {
            return "gradle";
        }
        if (normalized.startsWith("pnpm ")) {
            return "pnpm";
        }
        if (normalized.startsWith("yarn ")) {
            return "yarn";
        }
        if (normalized.startsWith("npm ")) {
            return "npm";
        }
        return "unknown";
    }

    private void checkFileExists(Path path, String okMessage, String failMessage, List<String> passed, List<String> failed) {
        if (Files.exists(path)) {
            passed.add(okMessage);
        } else {
            failed.add(failMessage + ": " + path.getFileName());
        }
    }

    private String detectProjectType(Path projectPath) {
        boolean hasPom = Files.exists(projectPath.resolve("pom.xml"));
        boolean hasPackageJson = Files.exists(projectPath.resolve("package.json"));
        boolean hasAngularJson = Files.exists(projectPath.resolve("angular.json"));
        if (hasPom) {
            return "springboot";
        }
        if (hasAngularJson || hasPackageJson) {
            return "angular";
        }
        return "";
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private boolean isAllowedForProjectType(String relativePath, String projectType) {
        if ("springboot".equals(projectType)) {
            return isAllowedMavenPath(relativePath);
        }
        if ("angular".equals(projectType)) {
            return relativePath.startsWith("src/")
                    || relativePath.equals("package.json")
                    || relativePath.equals("angular.json")
                    || relativePath.startsWith("tsconfig");
        }
        return false;
    }

    private String limitText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + System.lineSeparator() + "...(truncated)";
    }

    private String generateWithRetry(
            ChatLanguageModel model,
            String systemPrompt,
            String userPrompt,
            int maxAttempts,
            int promptMaxChars,
            int outputMaxChars
    ) {
        String boundedSystem = limitText(maskSecrets(systemPrompt), promptMaxChars);
        String boundedUser = limitText(maskSecrets(userPrompt), promptMaxChars);
        CodeGenAssistant assistant = buildCodeGenAssistant(model);
        RuntimeException last = null;
        for (int i = 1; i <= Math.max(maxAttempts, 1); i++) {
            try {
                String out = assistant.generate(boundedSystem, boundedUser);
                return limitText(out, outputMaxChars);
            } catch (RuntimeException ex) {
                last = ex;
                meterRegistry.counter("agent.ai.retry.failures").increment();
                log.warn("AI generation attempt {} failed: {}", i, ex.getMessage());
            }
        }
        throw new IllegalStateException("AI generation failed after retries", last);
    }

    private String maskSecrets(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?i)api[_-]?key\\s*[:=]\\s*[^\\s]+", "api_key=***")
                .replaceAll("(?i)token\\s*[:=]\\s*[^\\s]+", "token=***");
    }

    private List<String> chunkText(String text, int chunkSize) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            int split = text.lastIndexOf(System.lineSeparator(), end);
            if (split <= start) {
                split = end;
            }
            chunks.add(text.substring(start, split));
            start = split;
        }
        return chunks;
    }

    private List<String> mergeDistinct(List<String> base, List<String> incoming) {
        List<String> merged = new ArrayList<>(base);
        for (String value : incoming) {
            if (!merged.contains(value)) {
                merged.add(value);
            }
        }
        return merged;
    }

    private AgentAssistant buildAgentAssistant(ChatLanguageModel model) {
        return AiServices.builder(AgentAssistant.class)
                .chatLanguageModel(model)
                .tools(agentTools)
                .build();
    }

    private CodeGenAssistant buildCodeGenAssistant(ChatLanguageModel model) {
        return AiServices.builder(CodeGenAssistant.class)
                .chatLanguageModel(model)
                .tools(agentTools)
                .build();
    }

    private ChatLanguageModel resolveModel(String modelProvider) {
        String configuredDefault = valueOrDefault(platformProperties.getDefaultModelProvider(), "openai");
        String effectiveProvider = (modelProvider == null || modelProvider.isBlank())
                ? configuredDefault
                : modelProvider.trim();
        String key = normalizeProviderKey(effectiveProvider);
        Map<String, ChatLanguageModel> beans = applicationContext.getBeansOfType(ChatLanguageModel.class);
        for (Map.Entry<String, ChatLanguageModel> entry : beans.entrySet()) {
            String beanKey = normalizeProviderKey(entry.getKey());
            if (beanKey.contains(key)) {
                return entry.getValue();
            }
        }
        if (!"openai".equals(key)) {
            log.warn("Requested provider '{}' not available. Falling back to default OpenAI model.", effectiveProvider);
        }
        return chatLanguageModel;
    }

    private String normalizeProviderKey(String raw) {
        if (raw == null) {
            return "openai";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("_", "-");
        return switch (key) {
            case "google", "google-gemini" -> "gemini";
            case "vertex", "vertex-ai", "vertex-gemini" -> "vertex-gemini";
            case "azure", "azure-open-ai", "azure-openai" -> "azure-openai";
            case "claude" -> "anthropic";
            default -> key;
        };
    }

    private Map<String, PromptTemplateDef> loadPromptTemplates() {
        Map<String, PromptTemplateDef> templates = new HashMap<>();
        ClassPathResource resource = new ClassPathResource("prompts.st");
        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            String currentName = null;
            List<String> currentArgs = List.of();
            StringBuilder currentBody = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (currentName == null) {
                    Matcher matcher = TEMPLATE_HEADER.matcher(line);
                    if (matcher.matches()) {
                        currentName = matcher.group(1);
                        String argsRaw = matcher.group(2).trim();
                        if (argsRaw.isBlank()) {
                            currentArgs = List.of();
                        } else {
                            String[] args = argsRaw.split(",");
                            List<String> parsedArgs = new ArrayList<>();
                            for (String arg : args) {
                                parsedArgs.add(arg.trim());
                            }
                            currentArgs = parsedArgs;
                        }
                        currentBody = new StringBuilder();
                    }
                } else if (line.equals(">>")) {
                    String body = currentBody.toString().stripTrailing();
                    templates.put(currentName, new PromptTemplateDef(currentArgs, body));
                    currentName = null;
                    currentArgs = List.of();
                } else {
                    currentBody.append(line).append(System.lineSeparator());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load prompts.st from resources", ex);
        }
        return templates;
    }

    private String renderPrompt(String templateName, Map<String, String> values) {
        PromptTemplateDef templateDef = promptTemplates.get(templateName);
        if (templateDef == null) {
            throw new IllegalArgumentException("Prompt template not found: " + templateName);
        }

        String rendered = templateDef.template();
        for (String arg : templateDef.args()) {
            String value = values.getOrDefault(arg, "");
            rendered = rendered.replace("<" + arg + ">", value);
        }
        return rendered;
    }

    private List<GeneratedFile> parseGeneratedFiles(String modelOutput) {
        Pattern pattern = Pattern.compile("FILE:\\s*(.+?)\\R```[\\w-]*\\R([\\s\\S]*?)\\R```");
        Matcher matcher = pattern.matcher(modelOutput);
        List<GeneratedFile> files = new ArrayList<>();

        while (matcher.find()) {
            String relativePath = matcher.group(1).trim();
            String content = matcher.group(2);
            if (!relativePath.isBlank()) {
                files.add(new GeneratedFile(relativePath, content));
            }
        }
        return files;
    }

    private FileWriteResult writeGeneratedFiles(
            Path rootPath,
            List<GeneratedFile> files,
            boolean overwriteExisting,
            boolean restrictToMavenPaths,
            boolean mergeIfNeeded,
            String operationId
    ) {
        List<String> written = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<AuditRecord> auditRecords = new ArrayList<>();
        try {
            Files.createDirectories(rootPath);
            Path auditRoot = rootPath.resolve(platformProperties.getAuditDir()).resolve(operationId);
            Path backupRoot = auditRoot.resolve("backup");
            Files.createDirectories(backupRoot);
            for (GeneratedFile file : files) {
                String normalizedRelativePath = file.relativePath().replace('\\', '/');
                if (restrictToMavenPaths && !isAllowedMavenPath(normalizedRelativePath)) {
                    warnings.add("Skipped non-Maven path (restrictToMavenPaths=true): " + normalizedRelativePath);
                    continue;
                }
                Path target = rootPath.resolve(file.relativePath()).normalize();
                if (!target.startsWith(rootPath)) {
                    warnings.add("Skipped path outside output root: " + file.relativePath());
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (Files.exists(target) && !overwriteExisting) {
                    if (mergeIfNeeded && isMergeSupported(normalizedRelativePath)) {
                        String existingContent = Files.readString(target);
                        Path backupPath = backupRoot.resolve(normalizedRelativePath + ".bak").normalize();
                        createBackupIfNeeded(target, backupPath);
                        String mergedContent = mergeText(existingContent, file.content(), normalizedRelativePath);
                        Files.writeString(target, mergedContent);
                        written.add(rootPath.relativize(target).toString().replace('\\', '/'));
                        auditRecords.add(new AuditRecord(normalizedRelativePath, true, backupPath.toString().replace('\\', '/')));
                        warnings.add("Merged into existing file: "
                                + rootPath.relativize(target).toString().replace('\\', '/'));
                        continue;
                    }
                    warnings.add("Skipped existing file (overwriteExisting=false): "
                            + rootPath.relativize(target).toString().replace('\\', '/'));
                    continue;
                }
                if (Files.exists(target)) {
                    Path backupPath = backupRoot.resolve(normalizedRelativePath + ".bak").normalize();
                    createBackupIfNeeded(target, backupPath);
                    auditRecords.add(new AuditRecord(normalizedRelativePath, true, backupPath.toString().replace('\\', '/')));
                } else {
                    auditRecords.add(new AuditRecord(normalizedRelativePath, false, ""));
                }
                Files.writeString(target, file.content());
                written.add(rootPath.relativize(target).toString().replace('\\', '/'));
            }
            writeAuditManifest(rootPath, operationId, auditRecords);
        } catch (IOException ex) {
            errors.add("Failed to write generated files: " + ex.getMessage());
        }
        return new FileWriteResult(written, warnings, errors);
    }

    private void createBackupIfNeeded(Path source, Path backupPath) throws IOException {
        Path parent = backupPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(backupPath)) {
            Files.copy(source, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeAuditManifest(Path rootPath, String operationId, List<AuditRecord> records) throws IOException {
        if (records.isEmpty()) {
            return;
        }
        Path auditRoot = rootPath.resolve(platformProperties.getAuditDir()).resolve(operationId);
        Files.createDirectories(auditRoot);
        StringBuilder content = new StringBuilder();
        content.append("timestamp=").append(Instant.now()).append(System.lineSeparator());
        for (AuditRecord record : records) {
            content.append(record.relativePath())
                    .append("|")
                    .append(record.existedBefore())
                    .append("|")
                    .append(record.backupPath())
                    .append(System.lineSeparator());
        }
        Files.writeString(auditRoot.resolve("manifest.txt"), content.toString(), StandardCharsets.UTF_8);
    }

    private boolean isAllowedMavenPath(String relativePath) {
        return "pom.xml".equals(relativePath)
                || MAVEN_ALLOWED_PREFIXES.stream().anyMatch(relativePath::startsWith);
    }

    private boolean isMergeSupported(String relativePath) {
        return "pom.xml".equals(relativePath)
                || relativePath.endsWith(".java")
                || relativePath.endsWith(".yml")
                || relativePath.endsWith(".yaml")
                || relativePath.endsWith(".properties")
                || relativePath.endsWith(".ftl");
    }

    private String mergeText(String existing, String generated, String relativePath) {
        if (relativePath.endsWith(".java")) {
            return mergeJavaText(existing, generated);
        }
        String trimmedGenerated = generated.strip();
        if (trimmedGenerated.isEmpty() || existing.contains(trimmedGenerated)) {
            return existing;
        }
        return existing.stripTrailing()
                + System.lineSeparator()
                + System.lineSeparator()
                + "# --- merged-by-agent ---"
                + System.lineSeparator()
                + trimmedGenerated
                + System.lineSeparator();
    }

    private String mergeJavaText(String existing, String generated) {
        String trimmedGenerated = generated.strip();
        if (trimmedGenerated.isEmpty() || existing.contains(trimmedGenerated)) {
            return existing;
        }

        try {
            String mergePrompt = """
                    You are merging Java code changes.
                    Merge the generated Java code into the existing Java file.
                    Requirements:
                    - Return ONLY final Java code, no markdown, no explanation.
                    - Keep package/imports valid and non-duplicated.
                    - Preserve existing behavior unless generated code extends it.
                    - Avoid duplicate methods/fields/classes.
                    - Result must compile on Java 17.

                    EXISTING JAVA FILE:
                    %s

                    GENERATED JAVA CODE:
                    %s
                    """.formatted(existing, trimmedGenerated);

            String merged = chatLanguageModel.generate(mergePrompt);
            String normalizedMerged = stripCodeFences(merged).strip();
            if (!normalizedMerged.isBlank()) {
                return normalizedMerged + System.lineSeparator();
            }
        } catch (Exception ignored) {
            // Fall back to deterministic merge when AI merge fails.
        }

        return fallbackJavaMerge(existing, trimmedGenerated);
    }

    private String fallbackJavaMerge(String existing, String trimmedGenerated) {
        String javaInsert = extractJavaBody(trimmedGenerated);
        int closingBraceIndex = existing.lastIndexOf('}');
        if (closingBraceIndex <= 0) {
            return existing.stripTrailing()
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + "/* --- merged-by-agent ---"
                    + System.lineSeparator()
                    + trimmedGenerated
                    + System.lineSeparator()
                    + "--- merged-by-agent --- */"
                    + System.lineSeparator();
        }

        String before = existing.substring(0, closingBraceIndex).stripTrailing();
        String after = existing.substring(closingBraceIndex);
        return before
                + System.lineSeparator()
                + System.lineSeparator()
                + "    // --- merged-by-agent ---"
                + System.lineSeparator()
                + javaInsert
                + System.lineSeparator()
                + after;
    }

    private String extractJavaBody(String javaText) {
        int firstOpen = javaText.indexOf('{');
        int lastClose = javaText.lastIndexOf('}');
        if (firstOpen >= 0 && lastClose > firstOpen) {
            String body = javaText.substring(firstOpen + 1, lastClose).strip();
            if (!body.isEmpty()) {
                return body;
            }
        }
        return javaText;
    }

    private String stripCodeFences(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed;
    }

    private String buildFolderTree(Path rootPath) {
        if (!Files.exists(rootPath)) {
            return "";
        }
        try {
            StringBuilder tree = new StringBuilder();
            tree.append(rootPath.getFileName()).append("/").append("\n");
            List<Path> paths = Files.walk(rootPath)
                    .filter(path -> !path.equals(rootPath))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            for (Path path : paths) {
                Path rel = rootPath.relativize(path);
                int depth = rel.getNameCount();
                tree.append("  ".repeat(Math.max(0, depth - 1)));
                tree.append("- ").append(rel.toString().replace('\\', '/'));
                if (Files.isDirectory(path)) {
                    tree.append("/");
                }
                tree.append("\n");
            }
            return tree.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build folder structure", ex);
        }
    }

    private record GeneratedFile(String relativePath, String content) {
    }

    private record FileWriteResult(List<String> writtenFiles, List<String> warnings, List<String> errors) {
    }

    private record AuditRecord(String relativePath, boolean existedBefore, String backupPath) {
    }

    private record PromptTemplateDef(List<String> args, String template) {
    }

    interface AgentAssistant {
        @SystemMessage("{{systemPrompt}}")
        @UserMessage("{{userPrompt}}")
        String execute(@V("systemPrompt") String systemPrompt, @V("userPrompt") String userPrompt);
    }

    interface CodeGenAssistant {
        @SystemMessage("{{systemPrompt}}")
        @UserMessage("{{userPrompt}}")
        String generate(@V("systemPrompt") String systemPrompt, @V("userPrompt") String userPrompt);
    }
}
