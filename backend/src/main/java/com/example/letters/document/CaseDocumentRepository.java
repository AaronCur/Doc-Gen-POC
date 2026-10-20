package com.example.letters.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Stand-in for the existing case-documents access. case-service only reads this table. */
@Repository
public interface CaseDocumentRepository extends JpaRepository<CaseDocument, String> {

    /** All documents for an application, regardless of who may see them. Staff only. */
    List<CaseDocument> findAllByAppIdOrderByCreatedAtAscDocIdAsc(String appId);

    /** The application's documents an external user may see. */
    @Query("select d from CaseDocument d where d.appId = :appId and "
            + DocumentVisibilityPolicy.VISIBLE_TO_EXTERNAL
            + " order by d.createdAt, d.docId")
    List<CaseDocument> findVisibleToExternalByAppId(@Param("appId") String appId);

    /** One document, only if an external user may see it. */
    @Query("select d from CaseDocument d where d.docId = :docId and " + DocumentVisibilityPolicy.VISIBLE_TO_EXTERNAL)
    Optional<CaseDocument> findVisibleToExternalByDocId(@Param("docId") String docId);

    /** IDs of the application's documents an external user may see, for the staff view's flag. */
    @Query("select d.docId from CaseDocument d where d.appId = :appId and " + DocumentVisibilityPolicy.VISIBLE_TO_EXTERNAL)
    List<String> findVisibleToExternalDocIdsByAppId(@Param("appId") String appId);
}
