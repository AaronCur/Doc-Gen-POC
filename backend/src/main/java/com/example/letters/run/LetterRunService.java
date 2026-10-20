package com.example.letters.run;

import java.time.Clock;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.letters.common.exception.BusinessRuleException;
import com.example.letters.lettertype.LetterType;
import com.example.letters.lettertype.LetterTypeLockRepository;
import com.example.letters.lettertype.LetterTypeRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class LetterRunService {

    private final LetterRunRepository letterRunRepository;
    private final LetterRunItemRepository letterRunItemRepository;
    private final LetterTypeRepository letterTypeRepository;
    private final LetterTypeLockRepository letterTypeLockRepository;
    private final LetterRequestQueueRepository letterRequestQueueRepository;
    private final LetterRunStatusService letterRunStatusService;
    private final Clock clock;

    public LetterRunService(LetterRunRepository letterRunRepository, LetterRunItemRepository letterRunItemRepository,
            LetterTypeRepository letterTypeRepository, LetterTypeLockRepository letterTypeLockRepository,
            LetterRequestQueueRepository letterRequestQueueRepository,
            LetterRunStatusService letterRunStatusService, Clock clock) {
        this.letterRunRepository = letterRunRepository;
        this.letterRunItemRepository = letterRunItemRepository;
        this.letterTypeRepository = letterTypeRepository;
        this.letterTypeLockRepository = letterTypeLockRepository;
        this.letterRequestQueueRepository = letterRequestQueueRepository;
        this.letterRunStatusService = letterRunStatusService;
        this.clock = clock;
    }

    /**
     * Creates a run and queues one letter request per eligible application, all in one local
     * transaction. Any exception thrown here rolls everything back. Duplicates are prevented by the
     * per-type lock plus the pending check, so no idempotency key is needed.
     */
    @Transactional
    public LetterRunResponse generateLetters(LetterRunRequest request, String actor) {
        String type = request.letterType();

        // 1. Serialise generation per letter type. Held until commit or rollback.
        letterTypeLockRepository.findWithLockByLetterType(type)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.BAD_REQUEST, "UNKNOWN_LETTER_TYPE",
                        "Unknown letter type: " + type));

        // 2. Any unprocessed queue row for this type means a previous run is still generating
        //    (or a second click arrived while the first was being processed).
        if (letterRequestQueueRepository.countPending(type) > 0) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "RUN_IN_PROGRESS",
                    "A run for this letter type is still generating.");
        }

        LetterType letterType = letterTypeRepository.findById(type)
                .orElseThrow(() -> new EntityNotFoundException("Letter type not found with code: " + type));

        // 3. The run starts visible to staff only.
        LetterRun run = letterRunRepository.saveAndFlush(
                new LetterRun(letterType, request.claimSubmittedDate(), actor, clock.instant()));

        // 4. Record membership once, from the eligibility rule.
        int total = letterRunItemRepository.insertForEligibleApplications(run.getId(), type);
        if (total == 0) {
            throw new BusinessRuleException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_ELIGIBLE_APPLICATIONS",
                    "No new applications are awaiting this letter type.");
        }

        // 5.
        run.setTotalRequested(total);
        letterRunRepository.save(run);

        // 6. Populate the shared queue from the run items.
        letterRequestQueueRepository.insertPendingForRun(run.getId());

        return mapToResponse(run);
    }

    /** Newest first, with each run's status calculated. */
    @Transactional
    public LetterRunPageResponse getLetterRuns(int page, int size) {
        Page<LetterRun> runs = letterRunRepository.findAllByOrderByRequestedAtDescIdDesc(PageRequest.of(page, size));
        return new LetterRunPageResponse(runs.map(this::mapToResponse).getContent(), runs.getTotalElements());
    }

    @Transactional
    public LetterRunResponse getLetterRunById(Long id) {
        return mapToResponse(findOrThrow(id));
    }

    /** The run's applications that have no document yet. */
    @Transactional
    public LetterRunPendingResponse getPendingApplications(Long id) {
        if (!letterRunRepository.existsById(id)) {
            throw runNotFound(id);
        }
        return new LetterRunPendingResponse(id, letterRunItemRepository.findPendingAppIdsByRunId(id));
    }

    /**
     * Makes a run's letters visible to external users, in one guarded UPDATE: only a completed,
     * staff-only run changes. If nothing was updated, works out why for the error.
     */
    @Transactional
    public LetterRunResponse releaseLetterRun(Long id, String actor) {
        if (letterRunRepository.release(id, actor, clock.instant()) == 0) {
            LetterRun run = findOrThrow(id);
            if (run.getVisibility() == RunVisibility.STAFF_AND_EXTERNAL) {
                throw new BusinessRuleException(HttpStatus.CONFLICT, "ALREADY_RELEASED",
                        "This run is already visible to external users.");
            }
            throw new BusinessRuleException(HttpStatus.CONFLICT, "NOT_COMPLETE",
                    "Available when generation completes.");
        }
        return mapToResponse(findOrThrow(id));
    }

    private LetterRun findOrThrow(Long id) {
        return letterRunRepository.findWithLetterTypeById(id).orElseThrow(() -> runNotFound(id));
    }

    private static EntityNotFoundException runNotFound(Long id) {
        return new EntityNotFoundException("Letter run not found with id: " + id);
    }

    private LetterRunResponse mapToResponse(LetterRun run) {
        LetterRunStatusService.RunProgress progress = letterRunStatusService.calculate(run);
        return new LetterRunResponse(
                run.getId(),
                run.getLetterType().getCode(),
                run.getLetterType().getName(),
                run.getClaimSubmittedDate(),
                progress.status(),
                run.getVisibility(),
                run.getTotalRequested(),
                progress.generated(),
                run.getRequestedBy(),
                run.getRequestedAt(),
                run.getReleasedBy(),
                run.getReleasedAt()
        );
    }
}
