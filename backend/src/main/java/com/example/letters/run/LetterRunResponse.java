package com.example.letters.run;

import java.time.Instant;
import java.time.LocalDate;

/** The run JSON from the API contract. The generate comment is deliberately not included. */
public record LetterRunResponse(
        Long runId,
        String planId,
        String letterType,
        String letterTypeName,
        LocalDate claimDeadlineDate,
        RunStatus status,
        RunVisibility visibility,
        int totalCount,
        int generatedCount,
        String requestedBy,
        Instant requestedAt,
        String releasedBy,
        Instant releasedAt
) { }
