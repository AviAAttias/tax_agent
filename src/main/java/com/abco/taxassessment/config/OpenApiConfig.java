package com.abco.taxassessment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** OpenAPI 3.1 specification configuration (§5). */
@Configuration
public class OpenApiConfig {

    @Value("${app.environment:local}")
    private String environment;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ABCO Tax Assessment Service API")
                        .description("Multi-tenant bank statement tax assessment platform. " +
                                     "All endpoints require JWT authentication. " +
                                     "Tenant identity is derived from the 'tid' JWT claim — " +
                                     "never from path parameters or request body.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ABCO Platform Engineering")
                                .email("platform@abco.com"))
                        .license(new License().name("Proprietary").url("https://abco.com")))
                .servers(List.of(
                        new Server().url("/").description("Current environment: " + environment)))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token with 'tid' claim identifying the tenant. " +
                                             "Obtain from your OAuth2 authorization server.")));
    }
}
