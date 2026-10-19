package com.example.letters.config;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.letters.support.IntegrationTest;

/** The OpenAPI document as the dev profile serves it. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiDocsTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void documentsEveryEndpointWithoutCredentials() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("case-service: letter generation"))
                .andExpect(jsonPath("$.paths['/api/letter-types'].get").exists())
                .andExpect(jsonPath("$.paths['/api/letter-types/{code}/eligible-count'].get").exists())
                .andExpect(jsonPath("$.paths['/api/letter-runs'].get").exists())
                .andExpect(jsonPath("$.paths['/api/letter-runs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/letter-runs/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/letter-runs/{id}/release'].post").exists())
                .andExpect(jsonPath("$.paths['/api/letter-runs/{id}/revoke'].post").exists())
                .andExpect(jsonPath("$.paths['/api/external/applications/{appId}/documents'].get").exists())
                .andExpect(jsonPath("$.paths['/api/staff/applications/{appId}/documents'].get").exists())
                .andExpect(jsonPath("$.paths['/api/staff/documents/{docId}'].delete").exists());
    }

    @Test
    void generateDocumentsTheIdempotencyKeyHeader() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/letter-runs'].post.parameters[?(@.name == 'Idempotency-Key')].in")
                        .value(hasItem("header")))
                .andExpect(jsonPath("$.paths['/api/letter-runs'].post.parameters[?(@.name == 'Idempotency-Key')].required")
                        .value(hasItem(true)));
    }

    @Test
    void declaresHttpBasicForEveryOperation() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.security[*].basicAuth").exists())
                .andExpect(jsonPath("$.tags[*].name", hasItem("Letter Run API")));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void theDocumentIsPublicButTheApiIsNot() throws Exception {
        mvc.perform(get("/api/letter-runs")).andExpect(status().isUnauthorized());
    }
}
