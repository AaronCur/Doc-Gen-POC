package com.example.letters.document;

import java.time.Instant;

/** A document as staff see it, flagged with whether external users can see it yet. */
public record StaffDocumentResponse(
        String docId,
        String appId,
        String documentType,
        Instant createdAt,
        boolean visibleToExternal
) { }
