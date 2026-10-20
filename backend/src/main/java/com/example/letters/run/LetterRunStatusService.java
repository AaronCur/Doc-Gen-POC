package com.example.letters.run;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.example.letters.config.LettersProperties;

import jakarta.transaction.Transactional;

/**
 * Calculates a run's generated count and status on read; neither is stored. The one thing it
 * writes is completed_at, the first time every item has a document, so a finished run is frozen
 * at total / total and is never recounted.
 */
@Service
public class LetterRunStatusService {

    private final LetterRunRepository letterRunRepository;
    private final LetterRunItemRepository letterRunItemRepository;
    private final LettersProperties lettersProperties;
    private final Clock clock;

    public LetterRunStatusService(LetterRunRepository letterRunRepository,
            LetterRunItemRepository letterRunItemRepository, LettersProperties lettersProperties, Clock clock) {
        this.letterRunRepository = letterRunRepository;
        this.letterRunItemRepository = letterRunItemRepository;
        this.lettersProperties = lettersProperties;
        this.clock = clock;
    }

    /** A run's calculated progress: letters generated so far, and the status. */
    public record RunProgress(int generated, RunStatus status) { }

    /**
     * Status rules, in order: COMPLETE if completed_at is set; NEEDS_ATTENTION if requested longer
     * ago than {@code letters.needs-attention-after}; QUEUED if nothing is generated; else IN_PROGRESS.
     */
    @Transactional
    public RunProgress calculate(LetterRun run) {
        if (run.getCompletedAt() != null) {
            return new RunProgress(run.getTotalRequested(), RunStatus.COMPLETE);
        }

        int generated = (int) letterRunItemRepository.countGeneratedByRunId(run.getId());
        Instant now = clock.instant();

        if (generated >= run.getTotalRequested()) {
            // Guarded update: if another caller got there first, its completed_at stands.
            if (letterRunRepository.markCompleted(run.getId(), now) == 1) {
                run.setCompletedAt(now);
            }
            return new RunProgress(run.getTotalRequested(), RunStatus.COMPLETE);
        }
        if (run.getRequestedAt().isBefore(now.minus(lettersProperties.needsAttentionAfter()))) {
            return new RunProgress(generated, RunStatus.NEEDS_ATTENTION);
        }
        return new RunProgress(generated, generated == 0 ? RunStatus.QUEUED : RunStatus.IN_PROGRESS);
    }
}
