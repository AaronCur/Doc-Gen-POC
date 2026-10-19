package com.example.letters.run;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

/**
 * Brings a run's generated count and status up to date from case-documents. Called when runs are
 * listed or released and by the stuck detector. Only GENERATING and NEEDS_ATTENTION runs are
 * recounted; a COMPLETE run is frozen.
 */
@Service
public class LetterRunProgressService {

    static final List<RunStatus> ACTIVE_STATUSES = List.of(RunStatus.GENERATING, RunStatus.NEEDS_ATTENTION);

    private final LetterRunRepository letterRunRepository;
    private final LetterRunItemRepository letterRunItemRepository;
    private final Clock clock;

    public LetterRunProgressService(LetterRunRepository letterRunRepository,
            LetterRunItemRepository letterRunItemRepository, Clock clock) {
        this.letterRunRepository = letterRunRepository;
        this.letterRunItemRepository = letterRunItemRepository;
        this.clock = clock;
    }

    @Transactional
    public void refreshActiveRuns() {
        for (LetterRun run : letterRunRepository.findWithLockByStatusInOrderByIdAsc(ACTIVE_STATUSES)) {
            refresh(run);
        }
    }

    /** The caller must hold the run's lock (see {@link LetterRunRepository#findWithLockById}). */
    @Transactional
    public void refresh(LetterRun run) {
        if (!run.isActive()) {
            return; // never recount a COMPLETE run
        }
        long generated = letterRunItemRepository.countGeneratedByRunId(run.getId());
        Instant now = clock.instant();

        if (generated >= run.getTotalCount()) {
            run.setStatus(RunStatus.COMPLETE);
            run.setGeneratedCount(run.getTotalCount());
            run.setLastProgressAt(now);
            run.setCompletedAt(now);
            letterRunRepository.save(run);
        } else if (generated > run.getGeneratedCount()) {
            run.setGeneratedCount((int) generated);
            run.setLastProgressAt(now);
            run.setStatus(RunStatus.GENERATING); // progress resumed after NEEDS_ATTENTION
            letterRunRepository.save(run);
        }
    }
}
