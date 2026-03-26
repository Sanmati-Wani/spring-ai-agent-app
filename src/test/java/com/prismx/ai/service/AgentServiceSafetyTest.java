package com.prismx.ai.service;

import com.prismx.ai.config.AgentPlatformProperties;
import com.prismx.ai.dto.CompilationFixRequest;
import com.prismx.ai.dto.CompilationFixResponse;
import com.prismx.ai.tool.AgentTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceSafetyTest {

    @TempDir
    Path tempDir;

    @Test
    void requiresPrecheckPassInProductionSafetyMode() {
        ChatLanguageModel chatModel = mock(ChatLanguageModel.class);
        AgentTools tools = mock(AgentTools.class);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansOfType(ChatLanguageModel.class)).thenReturn(Collections.emptyMap());
        when(tools.detectBuildCommandForProjectType(any(), any())).thenReturn("mvn -q -DskipTests compile");

        AgentPlatformProperties props = new AgentPlatformProperties();
        props.setProductionSafetyAlwaysOn(true);

        AgentService service = new AgentService(
                chatModel,
                tools,
                context,
                new SimpleMeterRegistry(),
                props
        );

        CompilationFixRequest request = new CompilationFixRequest(
                tempDir.toString(),
                "springboot",
                1,
                true,
                true,
                false,
                "openai"
        );

        CompilationFixResponse response = service.fixCompilation(request);

        assertFalse(response.fixed());
        assertTrue(response.finalMessage().startsWith("Precheck failed in production safety mode:"));
    }

    @Test
    void autoDetectsProjectTypeAndRecordsSafetyOverrides() throws IOException {
        Files.writeString(tempDir.resolve("package.json"), "{}");
        Files.writeString(tempDir.resolve("angular.json"), "{}");

        ChatLanguageModel chatModel = mock(ChatLanguageModel.class);
        AgentTools tools = mock(AgentTools.class);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansOfType(ChatLanguageModel.class)).thenReturn(Collections.emptyMap());
        when(tools.detectBuildCommandForProjectType(any(), any())).thenReturn("npm run build");
        when(tools.runBuildForProjectType(any(), any(), any(Integer.class)))
                .thenReturn(new AgentTools.CommandExecutionResult(true, 0, "BUILD SUCCESS", "npm run build"));

        AgentPlatformProperties props = new AgentPlatformProperties();
        props.setProductionSafetyAlwaysOn(true);

        AgentService service = new AgentService(
                chatModel,
                tools,
                context,
                new SimpleMeterRegistry(),
                props
        );

        CompilationFixRequest request = new CompilationFixRequest(
                tempDir.toString(),
                null,
                1,
                true,
                true,
                false,
                "openai"
        );

        CompilationFixResponse response = service.fixCompilation(request);

        assertTrue(response.fixed());
        assertTrue(response.safetyOverridesApplied().contains("projectType=angular"));
        assertTrue(response.safetyOverridesApplied().contains("applyChanges=false"));
    }
}
