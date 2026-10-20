package com.example.letters.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.letters.config.LettersProperties;

import jakarta.transaction.Transactional;

/**
 * Every {@code letters.check-interval}: sets completed_at on runs whose letters all exist, and logs
 * a WARN for each run that needs attention. Statuses are calculated on read, so this job isn't
 * needed for them to be correct; it makes completion stick without anyone opening the page, and
 * raises the alert.
 */
@Component
public class LetterRunCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(LetterRunCompletionJob.class);

    private final LetterRunRepository letterRunRepository;
    private final LetterRunStatusService letterRunStatusService;
    private final LettersProperties lettersProperties;

    public LetterRunCompletionJob(LetterRunRepository letterRunRepository,
            LetterRunStatusService letterRunStatusService, LettersProperties lettersProperties) {
        this.letterRunRepository = letterRunRepository;
        this.letterRunStatusService = letterRunStatusService;
        this.lettersProperties = lettersProperties;
    }

    // ---------------------------------------------------------------------------------------
    // SINGLE-POD LOCK GOES HERE. In production this job must run on one pod only. The POC is a
    // single instance, so no locking library is added. Wrap this method with the chosen
    // mechanism (see README, "Single-pod scheduling"). Running it on two pods at once would not
    // corrupt data, since completed_at is set with a guarded update, but each pod would log the
    // WARN alerts.
    // ---------------------------------------------------------------------------------------
    @Scheduled(fixedDelayString = "${letters.check-interval:1m}")
    @Transactional
    public void check() {
        for (LetterRun run : letterRunRepository.findAllByCompletedAtIsNullOrderByIdAsc()) {
            LetterRunStatusService.RunProgress progress = letterRunStatusService.calculate(run);
            if (progress.status() == RunStatus.NEEDS_ATTENTION) {
                log.warn("ALERT letter run {} needs attention: {} has generated {} of {} letters and was requested "
                                + "at {}, more than {} ago. Release is blocked until it completes.",
                        run.getId(), run.getLetterType().getCode(), progress.generated(), run.getTotalRequested(),
                        run.getRequestedAt(), lettersProperties.needsAttentionAfter());
            }
        }
    }
}
