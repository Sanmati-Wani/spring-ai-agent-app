package com.prismx.ai.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class AgentTools {

    @Tool("Get the current date and time for a timezone. Use IANA timezone like Asia/Kolkata.")
    public String getCurrentTime(@P("IANA timezone id, for example Asia/Kolkata") String timezone) {
        ZoneId zoneId = parseZone(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    @Tool("Get project folder/file tree for code generation context. Use absolute or relative root path.")
    public String getProjectStructure(
            @P("Root directory path to inspect") String rootDirectory,
            @P("Max depth to scan, recommended 3 to 6") int maxDepth
    ) {
        Path root = normalizePath(rootDirectory);
        int depth = maxDepth <= 0 ? 4 : Math.min(maxDepth, 8);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return "Directory not found: " + root;
        }

        try (Stream<Path> stream = Files.walk(root, depth)) {
            List<Path> paths = stream
                    .filter(path -> !path.equals(root))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());

            StringBuilder tree = new StringBuilder();
            tree.append(root.toString().replace('\\', '/')).append("/\n");
            for (Path path : paths) {
                Path rel = root.relativize(path);
                int level = rel.getNameCount();
                tree.append("  ".repeat(Math.max(0, level - 1)));
                tree.append("- ").append(rel.toString().replace('\\', '/'));
                if (Files.isDirectory(path)) {
                    tree.append("/");
                }
                tree.append("\n");
            }
            return tree.toString();
        } catch (IOException ex) {
            return "Failed to read structure: " + ex.getMessage();
        }
    }

    @Tool("Read file content for reference before generating code. Returns first N lines.")
    public String readFilePreview(
            @P("File path to read") String filePath,
            @P("Maximum lines to return, recommended 80 to 200") int maxLines
    ) {
        Path path = normalizePath(filePath);
        int limit = maxLines <= 0 ? 120 : Math.min(maxLines, 300);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return "File not found: " + path;
        }

        try (Stream<String> lines = Files.lines(path)) {
            return lines.limit(limit).collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException ex) {
            return "Failed to read file: " + ex.getMessage();
        }
    }

    @Tool("Find sample Java files by keyword in path or filename to copy project style.")
    public String findJavaSamples(
            @P("Root directory to search") String rootDirectory,
            @P("Keyword like controller, service, dto, repository, invoice") String keyword
    ) {
        Path root = normalizePath(rootDirectory);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return "Directory not found: " + root;
        }

        try (Stream<Path> stream = Files.walk(root, 8)) {
            List<String> matches = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> kw.isBlank() || path.toLowerCase().contains(kw))
                    .limit(30)
                    .toList();

            if (matches.isEmpty()) {
                return "No Java samples found for keyword: " + kw;
            }
            return String.join(System.lineSeparator(), matches);
        } catch (IOException ex) {
            return "Failed to search files: " + ex.getMessage();
        }
    }

    public CommandExecutionResult runBuildForProjectType(String projectPath, String projectType, int timeoutSeconds) {
        String normalizedType = projectType == null ? "" : projectType.trim().toLowerCase(Locale.ROOT);
        Path root = normalizePath(projectPath);
        String command = detectBuildCommandForProjectType(projectPath, normalizedType);
        if (command == null) {
            return new CommandExecutionResult(false, -1, "Unsupported projectType: " + projectType, "");
        }
        return runCommand(root, command, timeoutSeconds);
    }

    public String detectBuildCommandForProjectType(String projectPath, String projectType) {
        String normalizedType = projectType == null ? "" : projectType.trim().toLowerCase(Locale.ROOT);
        return resolveBuildCommand(normalizePath(projectPath), normalizedType);
    }

    private CommandExecutionResult runCommand(Path workingDir, String command, int timeoutSeconds) {
        ProcessBuilder builder = buildPlatformCommand(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                in.transferTo(outputBytes);
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandExecutionResult(false, -1, "Command timed out: " + command, command);
            }
            String output = outputBytes.toString(StandardCharsets.UTF_8);
            return new CommandExecutionResult(process.exitValue() == 0, process.exitValue(), output, command);
        } catch (Exception ex) {
            return new CommandExecutionResult(false, -1, "Failed to execute command: " + ex.getMessage(), command);
        }
    }

    private ProcessBuilder buildPlatformCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new ProcessBuilder("cmd", "/c", command);
        }
        return new ProcessBuilder("sh", "-c", command);
    }

    private String resolveBuildCommand(Path root, String projectType) {
        if ("springboot".equals(projectType)) {
            return resolveSpringBootBuild(root);
        }
        if ("angular".equals(projectType)) {
            return resolveAngularBuild(root);
        }
        return null;
    }

    private String resolveSpringBootBuild(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) {
            return "mvn -q -DskipTests compile";
        }
        if (Files.exists(root.resolve("gradlew"))) {
            return isWindows() ? "gradlew.bat compileJava -q" : "./gradlew compileJava -q";
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            return "gradle -q compileJava";
        }
        return null;
    }

    private String resolveAngularBuild(Path root) {
        if (!Files.exists(root.resolve("package.json"))) {
            return null;
        }
        if (Files.exists(root.resolve("pnpm-lock.yaml"))) {
            return "pnpm build";
        }
        if (Files.exists(root.resolve("yarn.lock"))) {
            return "yarn build";
        }
        return "npm run build";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private Path normalizePath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return Paths.get("").toAbsolutePath().normalize();
        }
        return Paths.get(pathText).toAbsolutePath().normalize();
    }

    private ZoneId parseZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception ex) {
            return ZoneId.of("UTC");
        }
    }

    public record CommandExecutionResult(boolean success, int exitCode, String output, String command) {
    }
}
