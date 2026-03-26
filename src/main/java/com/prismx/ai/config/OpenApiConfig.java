package com.prismx.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI agentAppOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring AI Agent API")
                        .description("API endpoints for running agents and generating code")
                        .version("v1")
                        .license(new License().name("Internal Use")));
    }
}
