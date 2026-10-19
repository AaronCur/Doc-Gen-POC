package com.example.letters.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.example.letters.support.IntegrationTest;

/**
 * Fails when someone adds an endpoint under /api/external without proving it filters through
 * DocumentVisibilityPolicy. To fix: add tests to DocumentVisibilityTest that show the new endpoint
 * never returns a staff-only document, then add its pattern below.
 */
class ExternalEndpointGuardTest extends IntegrationTest {

    /** Every /api/external endpoint, each covered by DocumentVisibilityTest. */
    private static final List<String> COVERED_EXTERNAL_ENDPOINTS = List.of(
            "/api/external/applications/{appId}/documents");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyExternalEndpointIsCoveredByAVisibilityTest() {
        List<String> externalPatterns = mappings.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .filter(pattern -> pattern.startsWith("/api/external"))
                .toList();

        assertThat(externalPatterns)
                .as("Endpoints under /api/external must be covered by DocumentVisibilityTest")
                .containsExactlyInAnyOrderElementsOf(COVERED_EXTERNAL_ENDPOINTS);
    }
}
