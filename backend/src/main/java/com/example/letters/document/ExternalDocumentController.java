package com.example.letters.document;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Stub of the external users' document list, so the staged reveal can be demonstrated. Every
 * response is filtered by {@link DocumentVisibilityPolicy}. ExternalEndpointGuardTest fails if a
 * new /api/external endpoint is added without a visibility test.
 *
 * <p>Not modelled in the POC: checking that the external user may access this application at all.
 * The real system already does that and must keep doing it.
 */
@RestController
@RequestMapping("/api/external")
@Tag(name = "External Document API", description = "Stand-in for the external users' document list")
public class ExternalDocumentController {

    private final DocumentService documentService;

    public ExternalDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping(value = "/applications/{appId}/documents", version = "1")
    @Operation(summary = "Documents an external user can see (EXTERNAL)")
    public ResponseEntity<List<DocumentResponse>> getExternalDocuments(@PathVariable String appId) {
        return ResponseEntity.ok(documentService.getExternalDocuments(appId));
    }
}
