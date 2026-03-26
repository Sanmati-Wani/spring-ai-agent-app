package com.prismx.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ModelProviderConfig {

    @Bean(name = "openaiChatLanguageModel")
    @Primary
    @ConditionalOnProperty(prefix = "agent.providers.openai", name = "enabled", havingValue = "true")
    public ChatLanguageModel openaiChatLanguageModel(ModelProviderProperties properties) {
        ModelProviderProperties.ProviderConfig cfg = properties.getOpenai();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("OpenAI is enabled but agent.providers.openai.api-key is missing");
        }
        return OpenAiChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .temperature(resolveTemperature(cfg, 1.0))
                .build();
    }

    @Bean(name = "geminiChatLanguageModel")
    @ConditionalOnProperty(prefix = "agent.providers.gemini", name = "enabled", havingValue = "true")
    public ChatLanguageModel geminiChatLanguageModel(ModelProviderProperties properties) {
        ModelProviderProperties.ProviderConfig cfg = properties.getGemini();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini is enabled but agent.providers.gemini.api-key is missing");
        }
        return GoogleAiGeminiChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .temperature(resolveTemperature(cfg, 0.7))
                .build();
    }

    @Bean(name = "anthropicChatLanguageModel")
    @ConditionalOnProperty(prefix = "agent.providers.anthropic", name = "enabled", havingValue = "true")
    public ChatLanguageModel anthropicChatLanguageModel(ModelProviderProperties properties) {
        ModelProviderProperties.ProviderConfig cfg = properties.getAnthropic();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            throw new IllegalStateException("Anthropic is enabled but agent.providers.anthropic.api-key is missing");
        }
        return AnthropicChatModel.builder()
                .apiKey(cfg.getApiKey())
                .modelName(cfg.getModelName())
                .temperature(resolveTemperature(cfg, 0.7))
                .build();
    }

    private static Double resolveTemperature(ModelProviderProperties.ProviderConfig cfg, double defaultTemperature) {
        if (cfg.getTemperature() == null) {
            return defaultTemperature;
        }
        return cfg.getTemperature();
    }
}
