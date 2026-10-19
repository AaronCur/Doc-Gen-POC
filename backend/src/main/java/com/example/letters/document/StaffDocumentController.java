package com.example.letters.document;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Stubs of existing staff features: the unfiltered document list, and deleting a letter. */
@RestController
@RequestMapping("/api/staff")
@Tag(name = "Staff Document API", description = "Stand-ins for existing staff document features")
public class StaffDocumentController {

    private final DocumentService documentService;

    public StaffDocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping(value = "/applications/{appId}/documents", version = "1")
    @Operation(summary = "All documents for an application (STAFF)")
    public ResponseEntity<List<StaffDocumentResponse>> getStaffDocuments(@PathVariable String appId) {
        return ResponseEntity.ok(documentService.getStaffDocuments(appId));
    }

    @DeleteMapping(value = "/documents/{docId}", version = "1")
    @Operation(summary = "Delete a document (STAFF); the next run picks the application up again")
    public ResponseEntity<Void> deleteDocumentById(@PathVariable String docId) {
        documentService.deleteDocumentById(docId);
        return ResponseEntity.noContent().build();
    }
}
