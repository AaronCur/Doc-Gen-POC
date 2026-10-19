package com.example.letters.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * The OpenAPI document served by springdoc at /v3/api-docs, with Swagger UI at
 * /swagger-ui/index.html. Both are switched on in the dev profile only (application-dev.yaml).
 *
 * <p>Every operation requires HTTP Basic, so Swagger UI's "Authorize" button accepts the mock users
 * (admin/admin, staff/staff, external/external). Replace the scheme when the real IdP is in place.
 */
@Configuration
public class OpenApiConfig {

    static final String BASIC_AUTH = "basicAuth";

    @Bean
    OpenAPI caseServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("case-service: letter generation")
                        .version("poc")
                        .description("""
                                Admin letter generation with a staged reveal. Admins generate a run of letters \
                                for a letter type; letters are visible to staff only until a staff user enables \
                                the run for external users.

                                Errors are RFC 9457 ProblemDetail responses with a stable `code` property. \
                                Roles: ADMIN, STAFF, EXTERNAL (POC users: admin, staff, external; password = \
                                username)."""))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
