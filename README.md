# LangChain4j Agent App (Java 17)

## Run

1. Set API key:

```powershell
$env:OPENAI_API_KEY="your-key"
```

2. Start app:

```powershell
mvn spring-boot:run
```

3. Open Swagger UI:

- `http://localhost:8090/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8090/v3/api-docs`
- Actuator health: `http://localhost:8090/actuator/health`

## Detailed Usage Guide

### Base URL and headers

- Base URL (local): `http://localhost:8090`
- Content header: `Content-Type: application/json`
- Optional request trace header: `X-Request-Id: <any-unique-id>`
- If API key is configured, pass: `X-Agent-Api-Key: <AGENT_API_KEY>`

### Endpoint quick map

- `POST /api/agent/run` -> general assistant response (can use tools)
- `POST /api/agent/request-body-template` -> generate a logical `CodeGenRequest` from plain description
- `POST /api/agent/generate-code` -> generate code; optionally write files to disk
- `POST /api/agent/scaffold` -> scaffold an agent/module structure by name + path
- `POST /api/agent/precheck` -> validate project path/type and detect build strategy
- `POST /api/agent/fix-compilation` -> iterative build-error fixing loop
- `POST /api/agent/rollback` -> rollback file writes for a prior operation id

### Recommended production flow

1. Call `/api/agent/request-body-template` from user description.
2. Call `/api/agent/generate-code` with `applyToProject=false` first (preview mode).
3. Validate generated `FILE:` paths/content.
4. Call `/api/agent/generate-code` with `applyToProject=true`.
5. Call `/api/agent/precheck` on target project.
6. Call `/api/agent/fix-compilation` to iterate fixes.
7. If needed, call `/api/agent/rollback` with returned operation id.

### PowerShell examples

#### 1) Generate request body template

```powershell
$headers = @{
  "Content-Type" = "application/json"
  "X-Agent-Api-Key" = "$env:AGENT_API_KEY"
}

$body = @{
  description = "Create customer CRUD API with validation and tests"
  agentName = "CustomerAgent"
  modelProvider = "openai"
  projectType = "api"
  includeTests = $true
  outputStyle = "multi-file"
  applyToProject = $false
  outputRootDir = "generated-code"
  overwriteExisting = $false
  restrictToMavenPaths = $true
  mergeIfNeeded = $true
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8090/api/agent/request-body-template" -Headers $headers -Body $body
```

#### 2) Generate code (preview mode)

```powershell
$body = @{
  description = "You generate clean production-ready Spring APIs."
  task = "Create Customer CRUD with DTO, service, repository, controller, validation and global exception handling."
  modelProvider = "openai"
  projectType = "api"
  includeTests = $true
  outputStyle = "multi-file"
  applyToProject = $false
  outputRootDir = "generated-code"
  overwriteExisting = $false
  restrictToMavenPaths = $true
  mergeIfNeeded = $true
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8090/api/agent/generate-code" -Headers $headers -Body $body
```

#### 3) Precheck target project

```powershell
$body = @{
  projectPath = "D:/platform-base-path/applications/sample-app"
  projectType = "springboot" # optional; auto-detect if omitted
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8090/api/agent/precheck" -Headers $headers -Body $body
```

#### 4) Fix compilation

```powershell
$body = @{
  projectPath = "D:/platform-base-path/applications/sample-app"
  projectType = "springboot" # optional if auto-detect works
  modelProvider = "openai"
  maxIterations = 4
  applyChanges = $true
  overwriteExisting = $false
  mergeIfNeeded = $true
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8090/api/agent/fix-compilation" -Headers $headers -Body $body
```

#### 5) Rollback an operation

```powershell
$body = @{
  operationId = "fix-<operation-id>"
  projectPath = "D:/platform-base-path/applications/sample-app"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8090/api/agent/rollback" -Headers $headers -Body $body
```

### Key request fields and behavior

- `modelProvider`: routing key (`openai`, `gemini`, `anthropic`, etc.)
- `applyToProject`:
  - `false` -> generate only, no file write
  - `true` -> writes files under `outputRootDir`
- `overwriteExisting`: replace existing files when true
- `mergeIfNeeded`: merge into supported files when overwrite is false
- `restrictToMavenPaths`: allows only `pom.xml`, `src/main/java`, `src/main/resources`, `src/test/java`
- `projectType` for fix/precheck:
  - `springboot` or `angular`
  - optional when auto-detection can infer type

### Important response fields

- `generationId`, `generatedAt`: traceability for generated outputs
- `warnings`, `errors`: non-blocking vs blocking outcomes
- `filesCreated`, `folderStructure`: write summary
- `safetyOverridesApplied`: safety mode overrides applied at runtime
- `detectedBuildCommand`, `detectedBuildTool`: precheck build strategy visibility

## Call agent

```http
POST /api/agent/run
Content-Type: application/json

{
  "description": "You are a travel planning assistant that gives concise plans",
  "task": "Create a 3-day budget itinerary for Goa"
}
```


## Tool-calling example

The agent is wired with a `getCurrentTime` tool.

```http
POST /api/agent/run
Content-Type: application/json

{
  "description": "You are a helpful assistant that uses tools for accurate time information.",
  "task": "What is the current time in Asia/Kolkata and UTC?"
}
```

## Code generation agent (Spring Boot 3.5 + Java 17)

Use this endpoint when you want generated code from a description/task.

```http
POST /api/agent/generate-code
Content-Type: application/json

{
  "description": "You generate clean production-ready Spring APIs.",
  "task": "Create CRUD endpoints for Book with validation and global exception handling.",
  "modelProvider": "openai",
  "projectType": "api",
  "includeTests": true,
  "outputStyle": "multi-file",
  "applyToProject": true,
  "outputRootDir": "generated-code",
  "overwriteExisting": false,
  "restrictToMavenPaths": true,
  "mergeIfNeeded": false
}
```

When `applyToProject` is `true`, files from `FILE: ...` blocks are written under `outputRootDir`.
The response also includes:
- `generationId` and `generatedAt` for traceability
- `filesCreated`: list of created files relative to the root directory
- `folderStructure`: a text tree of generated folders/files
- `warnings`: non-blocking issues (for example existing file skipped)
- `errors`: blocking issues (for example invalid FILE output format)
- `safetyOverridesApplied`: enforced safety changes applied at runtime
- `restrictToMavenPaths=true` allows writes only to `pom.xml`, `src/main/java`, `src/main/resources`, `src/test/java`

The code-generation assistant can also use tools to inspect existing projects:
- `getProjectStructure(rootDirectory, maxDepth)`
- `findJavaSamples(rootDirectory, keyword)`
- `readFilePreview(filePath, maxLines)`

## Build request body from description

Use this endpoint to convert a plain description into a logical `CodeGenRequest`.

```http
POST /api/agent/request-body-template
Content-Type: application/json

{
  "description": "Create invoice CRUD APIs with validation and tests.",
  "agentName": "Invoice Code Agent",
  "modelProvider": "openai",
  "projectType": "api",
  "includeTests": true,
  "outputStyle": "multi-file",
  "applyToProject": false,
  "outputRootDir": "generated-code",
  "overwriteExisting": false,
  "restrictToMavenPaths": true,
  "mergeIfNeeded": false
}
```

Then use the returned `requestBody` directly in `/api/agent/generate-code`.

## Scaffold by agent name + project path

Use this when you want direct generation into a target project structure.

```http
POST /api/agent/scaffold
Content-Type: application/json

{
  "agentName": "IrsForm990",
  "projectPath": "D:/platform-base-path/applications/dfdfd-local/dfdfd-local-server/dfdfd-local-application",
  "description": "Search IRS organizations, download Form 990 PDFs, extract data, and build JSON output.",
  "modelProvider": "openai",
  "includeRepository": true,
  "includeTests": true,
  "overwriteExisting": false,
  "mergeIfNeeded": true
}
```

This will generate and place files in:
- `controller`, `service`, `dto`, `model`
- `repository` when needed
- `src/main/resources/templates/<agentName>.ftl`
- updates in `application*.yml` and `pom.xml` when required
- set `mergeIfNeeded=true` to merge into existing `pom.xml`, `.java`, `.yml/.yaml`, `.properties`, `.ftl` files when overwrite is disabled (`.java` uses AI-assisted merge with fallback)

## Auto-fix compilation

Use this endpoint to iterate compile -> AI fix -> recompile until success or max iterations.

```http
POST /api/agent/fix-compilation
Content-Type: application/json

{
  "projectPath": "D:/platform-base-path/applications/dfdfd-local/dfdfd-local-server/dfdfd-local-application",
  "projectType": "springboot",
  "modelProvider": "openai",
  "maxIterations": 4,
  "applyChanges": true,
  "overwriteExisting": false,
  "mergeIfNeeded": true
}
```

`projectType` values:
- `springboot` (runs `mvn -q -DskipTests compile`)
- `angular` (runs `npm run build`)

Build command strategy auto-detection:
- `springboot`: Maven (`pom.xml`) or Gradle (`gradlew` / `build.gradle(.kts)`)
- `angular`: `pnpm`, `yarn`, or `npm` based on lockfiles

## Precheck before auto-fix

Use this endpoint to validate project path and baseline structure/tooling before running `/fix-compilation`.
`projectType` is optional; if omitted, service auto-detects from project files.
Response also includes:
- `detectedBuildCommand` for observability (for example `mvn -q -DskipTests compile`)
- `detectedBuildTool` (`maven|gradle|npm|pnpm|yarn|unknown`)

```http
POST /api/agent/precheck
Content-Type: application/json

{
  "projectPath": "D:/platform-base-path/applications/dfdfd-local/dfdfd-local-server/dfdfd-local-application"
}
```

## Rollback by operation id

Use this endpoint to restore files changed by a previous generation/fix operation (uses audit backups).

```http
POST /api/agent/rollback
Content-Type: application/json

{
  "operationId": "fix-<id>-it1",
  "projectPath": "D:/platform-base-path/applications/dfdfd-local/dfdfd-local-server/dfdfd-local-application"
}
```

## Enterprise controls included

- Request ID propagation (`X-Request-Id`)
- API key auth via `X-Agent-Api-Key` (enable by setting `AGENT_API_KEY`)
- Basic in-memory rate limit on `/api/agent/**`
- Actuator metrics/health endpoints
- Per-project lock to avoid concurrent write conflicts
- Audit manifest + backups under `.agent-audit` for rollback
- Always-on production safety mode (`agent.platform.production-safety-always-on=true`):
  - enforces `overwriteExisting=false`, `restrictToMavenPaths=true`, `mergeIfNeeded=true` for code writes
  - requires precheck to pass before compilation-fix
  - auto-detects `projectType` for precheck/fix when omitted
  - blocks oversized generation batches via `agent.platform.max-files-per-run`

## Smart context management (for large error logs)

The fix loop now handles large compiler output in chunks and continues across chunks per iteration.

Tune these in `agent.platform`:
- `compiler-output-max-chars` (default `50000`)
- `ai-prompt-max-chars` (default `50000`)
- `ai-output-max-chars` (default `50000`)
- `error-chunk-max-chars` (default `15000`)
- `max-chunks-per-iteration` (default `4`)

Context strategy shortcut:
- `context-profile: balanced` (default)
- `context-profile: aggressive` (uses higher safe caps when specific values are not set)

## Model provider from config

Set default provider in profile YAML:

```yaml
agent:
  platform:
    default-model-provider: openai
```

Request-level `modelProvider` overrides this value.
If requested provider is unavailable, service falls back to OpenAI/default model.

To enable additional providers:

```yaml
agent:
  providers:
    gemini:
      enabled: true
      api-key: ${GOOGLE_GENAI_API_KEY}
      model-name: ${GEMINI_MODEL_NAME:gemini-1.5-flash}
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      model-name: ${ANTHROPIC_MODEL_NAME:claude-3-5-sonnet-20241022}
```

Then set `modelProvider` in request as `gemini` or `anthropic`, or set `agent.platform.default-model-provider` accordingly.

Configured provider keys supported by routing:
- `openai`
- `gemini`
- `vertex-gemini`
- `anthropic`
- `azure-openai`
- `ollama`
- `mistral`
- `openrouter`

Note: In this version, runtime model beans are implemented for `openai`, `gemini`, and `anthropic`.
Other keys are configuration-ready aliases and safely fall back to the default provider unless corresponding beans are added.
