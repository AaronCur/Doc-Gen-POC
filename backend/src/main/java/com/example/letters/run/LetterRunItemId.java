package com.example.letters.run;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link LetterRunItem}: (run_id, app_id). */
public class LetterRunItemId implements Serializable {

    private Long runId;
    private String appId;

    public LetterRunItemId() {
    }

    public LetterRunItemId(Long runId, String appId) {
        this.runId = runId;
        this.appId = appId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LetterRunItemId id && Objects.equals(runId, id.runId) && Objects.equals(appId, id.appId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId, appId);
    }
}
