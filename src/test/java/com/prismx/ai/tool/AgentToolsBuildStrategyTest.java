package com.prismx.ai.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentToolsBuildStrategyTest {

    private final AgentTools agentTools = new AgentTools();

    @TempDir
    Path tempDir;

    @Test
    void detectsMavenForSpringBoot() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

        String command = agentTools.detectBuildCommandForProjectType(tempDir.toString(), "springboot");

        assertEquals("mvn -q -DskipTests compile", command);
    }

    @Test
    void detectsGradleForSpringBoot() throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");

        String command = agentTools.detectBuildCommandForProjectType(tempDir.toString(), "springboot");

        assertEquals("gradle -q compileJava", command);
    }

    @Test
    void detectsPnpmForAngular() throws IOException {
        Files.writeString(tempDir.resolve("package.json"), "{}");
        Files.writeString(tempDir.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'");

        String command = agentTools.detectBuildCommandForProjectType(tempDir.toString(), "angular");

        assertEquals("pnpm build", command);
    }
}
