package com.example.letters.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Stand-in for the existing case-documents table. The document type is the letter type. */
@Entity
@Table(name = "case_documents")
@Getter
@Setter
@NoArgsConstructor
public class CaseDocument {

    @Id
    @Column(name = "doc_id")
    private String docId;

    @Column(name = "app_id", nullable = false)
    private String appId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
