package com.example.letters.run;

import java.util.List;

/** The applications in a run that have no document yet. */
public record LetterRunPendingResponse(
        Long runId,
        List<String> pendingAppIds
) { }
