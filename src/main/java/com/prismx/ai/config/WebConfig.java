package com.prismx.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({AgentPlatformProperties.class, ModelProviderProperties.class})
public class WebConfig implements WebMvcConfigurer {

    private final AgentApiInterceptor agentApiInterceptor;

    public WebConfig(AgentApiInterceptor agentApiInterceptor) {
        this.agentApiInterceptor = agentApiInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(agentApiInterceptor).addPathPatterns("/api/agent/**");
    }
}
