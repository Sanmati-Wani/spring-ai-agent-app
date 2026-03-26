package com.prismx.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "agent.providers")
public class ModelProviderProperties {

    private ProviderConfig openai = new ProviderConfig();
    private ProviderConfig gemini = new ProviderConfig();
    private ProviderConfig vertexGemini = new ProviderConfig();
    private ProviderConfig anthropic = new ProviderConfig();
    private ProviderConfig azureOpenai = new ProviderConfig();
    private ProviderConfig ollama = new ProviderConfig();
    private ProviderConfig mistral = new ProviderConfig();
    private ProviderConfig openrouter = new ProviderConfig();

    @Getter
    @Setter
    public static class ProviderConfig {
        private boolean enabled;
        private String apiKey = "";
        private String modelName = "";
        private Double temperature;

        public void setApiKey(String apiKey) {
            this.apiKey = sanitizeHeaderValue(apiKey);
        }

        private static String sanitizeHeaderValue(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("\r", "").replace("\n", "").trim();
        }
    }
}
