package com.example.letters.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.example.letters.support.IntegrationTest;

/** Without the dev profile, neither the OpenAPI document nor Swagger UI is served. */
@AutoConfigureMockMvc
class OpenApiDisabledByDefaultTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocsAreNotServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsNotServed() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
