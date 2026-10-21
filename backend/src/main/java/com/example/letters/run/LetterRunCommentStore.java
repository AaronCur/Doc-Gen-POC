package com.example.letters.run;

/**
 * Where a run's mandatory comment goes. The storage location isn't decided yet, so Generate only
 * talks to this interface; swap the implementation once it is. The comment is not stored on
 * letter_run and is not returned in run responses.
 */
public interface LetterRunCommentStore {

    void store(Long runId, String comment, String author);
}
