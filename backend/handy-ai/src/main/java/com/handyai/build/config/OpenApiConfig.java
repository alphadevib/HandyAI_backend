package com.handyai.build.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI 3 description springdoc generates from the controllers, served at /v3/api-docs, with
 * Swagger UI at /swagger-ui.html for trying requests in the browser.
 *
 * <p>Most endpoints are public, so the bearer token is optional: sign in through
 * {@code POST /api/auth/login}, press "Authorize" and paste the token to call the {@code /api/me}
 * and {@code /api/admin} endpoints.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI handyAiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HandyAI API")
                        .version("v1")
                        .description("Find, compare and track AI tools: catalogue, semantic search, "
                                + "recommendations, an LLM chat assistant (Ollama), reviews, saved "
                                + "tools and subscription tracking.")
                        .license(new License().name("Private")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("The token from POST /api/auth/login or /api/auth/register")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
