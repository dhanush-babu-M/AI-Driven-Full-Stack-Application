package com.pathpilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pathPilotOpenApi(AppProperties appProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title(appProperties.name() + " API")
                        .description(appProperties.tagline())
                        .version("v1"));
    }
}
