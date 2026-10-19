package com.example.letters.run;

public enum RunStatus {
    /** Queue rows written; waiting for the letter-generator to produce the documents. */
    GENERATING,
    /** Every run item has a document. Counts are frozen from here on. */
    COMPLETE,
    /** No progress for longer than the stuck threshold. Release is blocked. */
    NEEDS_ATTENTION
}
