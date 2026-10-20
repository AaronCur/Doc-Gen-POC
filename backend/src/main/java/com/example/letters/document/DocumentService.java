package com.example.letters.document;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

/**
 * Stand-ins for the existing document features, plus the external reads. External reads use the
 * repository queries that embed {@link DocumentVisibilityPolicy#VISIBLE_TO_EXTERNAL}, so hidden
 * documents never leave the database.
 */
@Service
public class DocumentService {

    private final CaseDocumentRepository caseDocumentRepository;

    public DocumentService(CaseDocumentRepository caseDocumentRepository) {
        this.caseDocumentRepository = caseDocumentRepository;
    }

    /** The application's documents an external user may see; letters of unreleased runs are left out. */
    @Transactional
    public List<DocumentResponse> getExternalDocuments(String appId) {
        return caseDocumentRepository.findVisibleToExternalByAppId(appId).stream().map(this::mapToResponse).toList();
    }

    /**
     * One document for an external user. A hidden document is reported exactly like a missing one,
     * so the response doesn't reveal that it exists.
     */
    @Transactional
    public DocumentResponse getExternalDocumentById(String docId) {
        return caseDocumentRepository.findVisibleToExternalByDocId(docId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new EntityNotFoundException("Document not found with id: " + docId));
    }

    /** Unfiltered: staff see every document, with a flag saying whether external users can. */
    @Transactional
    public List<StaffDocumentResponse> getStaffDocuments(String appId) {
        Set<String> visibleToExternal = Set.copyOf(caseDocumentRepository.findVisibleToExternalDocIdsByAppId(appId));
        return caseDocumentRepository.findAllByAppIdOrderByCreatedAtAscDocIdAsc(appId).stream()
                .map(document -> mapToStaffResponse(document, visibleToExternal.contains(document.getDocId())))
                .toList();
    }

    /** Stand-in for the existing delete feature, so regeneration can be demonstrated. */
    @Transactional
    public void deleteDocumentById(String docId) {
        if (!caseDocumentRepository.existsById(docId)) {
            throw new EntityNotFoundException("Document not found with id: " + docId);
        }
        caseDocumentRepository.deleteById(docId);
    }

    private DocumentResponse mapToResponse(CaseDocument document) {
        return new DocumentResponse(
                document.getDocId(),
                document.getAppId(),
                document.getDocumentType(),
                document.getCreatedAt()
        );
    }

    private StaffDocumentResponse mapToStaffResponse(CaseDocument document, boolean visibleToExternal) {
        return new StaffDocumentResponse(
                document.getDocId(),
                document.getAppId(),
                document.getDocumentType(),
                document.getCreatedAt(),
                visibleToExternal
        );
    }
}
