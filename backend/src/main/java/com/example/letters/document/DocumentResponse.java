package com.example.letters.document;

import java.time.Instant;

/** A document as an external user sees it. */
public record DocumentResponse(
        String docId,
        String appId,
        String documentType,
        Instant createdAt
) { }
