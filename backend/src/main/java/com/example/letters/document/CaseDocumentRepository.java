package com.example.letters.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Stand-in for the existing case-documents access. */
@Repository
public interface CaseDocumentRepository extends JpaRepository<CaseDocument, String> {

    /**
     * All documents for an application, regardless of who may see them. Anything serving external
     * users must filter the result through {@link DocumentVisibilityPolicy}.
     */
    List<CaseDocument> findAllByAppIdOrderByCreatedAtAscDocIdAsc(String appId);
}
