package com.example.letters.run;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.letters.config.LettersProperties;

import jakarta.transaction.Transactional;

/**
 * Flags runs the letter-generator has stopped making progress on. The real generator retries a
 * failing row forever, so without this a run would sit at "x / y letters" with no signal. A
 * NEEDS_ATTENTION run cannot be released, and returns to GENERATING if letters start appearing.
 */
@Component
public class StuckRunDetector {

    private static final Logger log = LoggerFactory.getLogger(StuckRunDetector.class);

    private final LetterRunProgressService letterRunProgressService;
    private final LetterRunRepository letterRunRepository;
    private final LettersProperties lettersProperties;
    private final Clock clock;

    public StuckRunDetector(LetterRunProgressService letterRunProgressService, LetterRunRepository letterRunRepository,
            LettersProperties lettersProperties, Clock clock) {
        this.letterRunProgressService = letterRunProgressService;
        this.letterRunRepository = letterRunRepository;
        this.lettersProperties = lettersProperties;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------------------------
    // SINGLE-POD LOCK GOES HERE. In production this job must run on one pod only. The POC is a
    // single instance, so no locking library is added. Wrap this method with the chosen
    // mechanism (see README, "Single-pod scheduling"). Running it on two pods at once would not
    // corrupt data, since the runs are row-locked while they are updated, but the WARN alert
    // could be raised twice.
    // ---------------------------------------------------------------------------------------
    @Scheduled(fixedDelayString = "${letters.stuck-check-interval:1m}")
    @Transactional
    public void check() {
        // Refresh first, so a run whose letters have just appeared completes or resumes
        // instead of being flagged.
        letterRunProgressService.refreshActiveRuns();

        Instant cutoff = clock.instant().minus(lettersProperties.stuckThreshold());
        // Only GENERATING runs can move to NEEDS_ATTENTION, so the alert is raised once per stall.
        for (LetterRun run : letterRunRepository.findWithLockByStatusInOrderByIdAsc(List.of(RunStatus.GENERATING))) {
            if (run.getLastProgressAt().isBefore(cutoff)) {
                run.setStatus(RunStatus.NEEDS_ATTENTION);
                letterRunRepository.save(run);
                log.warn("ALERT letter run {} needs attention: {} has generated {} of {} letters with no "
                                + "progress since {} (threshold {}). Release is blocked until it completes.",
                        run.getId(), run.getLetterType().getCode(), run.getGeneratedCount(), run.getTotalCount(),
                        run.getLastProgressAt(), lettersProperties.stuckThreshold());
            }
        }
    }
}
