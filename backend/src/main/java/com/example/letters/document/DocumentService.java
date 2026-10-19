package com.example.letters.document;

import java.util.List;

import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

/** Stand-ins for the existing document features, plus the external read through the policy. */
@Service
public class DocumentService {

    private final CaseDocumentRepository caseDocumentRepository;
    private final DocumentVisibilityPolicy documentVisibilityPolicy;

    public DocumentService(CaseDocumentRepository caseDocumentRepository,
            DocumentVisibilityPolicy documentVisibilityPolicy) {
        this.caseDocumentRepository = caseDocumentRepository;
        this.documentVisibilityPolicy = documentVisibilityPolicy;
    }

    /** Filtered by {@link DocumentVisibilityPolicy}: letters of unreleased runs are left out. */
    @Transactional
    public List<DocumentResponse> getExternalDocuments(String appId) {
        List<CaseDocument> documents = caseDocumentRepository.findAllByAppIdOrderByCreatedAtAscDocIdAsc(appId);
        return documentVisibilityPolicy.visibleToExternal(documents).stream().map(this::mapToResponse).toList();
    }

    /** Unfiltered: staff see every document, with a flag saying whether external users can. */
    @Transactional
    public List<StaffDocumentResponse> getStaffDocuments(String appId) {
        return caseDocumentRepository.findAllByAppIdOrderByCreatedAtAscDocIdAsc(appId).stream()
                .map(this::mapToStaffResponse)
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

    private StaffDocumentResponse mapToStaffResponse(CaseDocument document) {
        return new StaffDocumentResponse(
                document.getDocId(),
                document.getAppId(),
                document.getDocumentType(),
                document.getCreatedAt(),
                documentVisibilityPolicy.isVisibleToExternal(document)
        );
    }
}
