package com.example.letters.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** POC ONLY: logs the comment instead of storing it, until the storage location is decided. */
@Component
public class LoggingLetterRunCommentStore implements LetterRunCommentStore {

    private static final Logger log = LoggerFactory.getLogger(LoggingLetterRunCommentStore.class);

    @Override
    public void store(Long runId, String comment, String author) {
        log.info("Comment for letter run {} by {}: {}", runId, author, comment);
    }
}
