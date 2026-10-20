package com.example.letters.run;

/**
 * Calculated on read, never stored. Checked in this order: COMPLETE if completed_at is set;
 * NEEDS_ATTENTION if requested longer ago than {@code letters.needs-attention-after}; QUEUED if no
 * letter exists yet; otherwise IN_PROGRESS.
 */
public enum RunStatus {
    /** Queue rows written; the letter-generator hasn't produced any letter yet. */
    QUEUED,
    /** Some letters exist, not all. */
    IN_PROGRESS,
    /** Every run item has had a document (completed_at is set). Counts are frozen from here on. */
    COMPLETE,
    /** Not complete, and requested longer ago than the threshold. Release is blocked. */
    NEEDS_ATTENTION
}
