package com.example.letters.run;

import java.time.Instant;
import java.time.LocalDate;

/** The run JSON from the API contract. */
public record LetterRunResponse(
        Long runId,
        String letterType,
        String letterTypeName,
        LocalDate claimSubmittedDate,
        RunStatus status,
        RunVisibility visibility,
        int totalCount,
        int generatedCount,
        String requestedBy,
        Instant requestedAt,
        String releasedBy,
        Instant releasedAt
) { }
