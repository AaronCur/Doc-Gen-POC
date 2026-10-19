package com.example.letters.run;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
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
    private final LetterRunAuditRepository letterRunAuditRepository;
    private final LetterTypeRepository letterTypeRepository;
    private final LetterTypeLockRepository letterTypeLockRepository;
    private final LetterRequestQueueRepository letterRequestQueueRepository;
    private final LetterRunProgressService letterRunProgressService;
    private final Clock clock;

    public LetterRunService(LetterRunRepository letterRunRepository, LetterRunItemRepository letterRunItemRepository,
            LetterRunAuditRepository letterRunAuditRepository, LetterTypeRepository letterTypeRepository,
            LetterTypeLockRepository letterTypeLockRepository,
            LetterRequestQueueRepository letterRequestQueueRepository,
            LetterRunProgressService letterRunProgressService, Clock clock) {
        this.letterRunRepository = letterRunRepository;
        this.letterRunItemRepository = letterRunItemRepository;
        this.letterRunAuditRepository = letterRunAuditRepository;
        this.letterTypeRepository = letterTypeRepository;
        this.letterTypeLockRepository = letterTypeLockRepository;
        this.letterRequestQueueRepository = letterRequestQueueRepository;
        this.letterRunProgressService = letterRunProgressService;
        this.clock = clock;
    }

    /** The outcome of a generate request: the run, and whether this call created it. */
    public record GenerateResult(LetterRunResponse run, boolean created) { }

    /**
     * Creates a run and queues one letter request per eligible application, all in one local
     * transaction. Any exception thrown here rolls everything back.
     */
    @Transactional
    public GenerateResult generateLetters(LetterRunRequest request, String idempotencyKey, String actor) {
        String type = request.letterType();

        // 1. Serialise generation per letter type. Held until commit or rollback.
        letterTypeLockRepository.findWithLockByLetterType(type)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.BAD_REQUEST, "UNKNOWN_LETTER_TYPE",
                        "Unknown letter type: " + type));

        // 2. A repeated submit (double click, retry after a timeout) returns the original run.
        Optional<LetterRun> existing = letterRunRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            LetterRun run = existing.get();
            if (!run.getLetterType().getCode().equals(type)
                    || !run.getClaimSubmittedDate().equals(request.claimSubmittedDate())) {
                throw idempotencyKeyReused();
            }
            return new GenerateResult(mapToResponse(run), false);
        }

        // 3. Any unprocessed queue row for this type means a previous run is still generating.
        if (letterRequestQueueRepository.countPending(type) > 0) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "RUN_IN_PROGRESS",
                    "A run for this letter type is still generating.");
        }

        Instant now = clock.instant();
        LetterType letterType = letterTypeRepository.findById(type)
                .orElseThrow(() -> new EntityNotFoundException("Letter type not found with code: " + type));

        // 4. The run starts GENERATING and visible to staff only.
        LetterRun run;
        try {
            run = letterRunRepository.saveAndFlush(
                    new LetterRun(letterType, request.claimSubmittedDate(), actor, idempotencyKey, now));
        } catch (DataIntegrityViolationException e) {
            // The same key used at the same moment for a different letter type (a different lock).
            throw idempotencyKeyReused();
        }

        // 5. Record membership once, from the eligibility rule.
        int total = letterRunItemRepository.insertForEligibleApplications(run.getId(), type, now);
        if (total == 0) {
            throw new BusinessRuleException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_ELIGIBLE_APPLICATIONS",
                    "No new applications are awaiting this letter type.");
        }

        // 6.
        run.setTotalCount(total);
        letterRunRepository.save(run);

        // 7. Populate the shared queue from the run items.
        letterRequestQueueRepository.insertPendingForRun(run.getId());

        // 8.
        letterRunAuditRepository.save(new LetterRunAudit(run, AuditAction.GENERATE, actor, now));

        return new GenerateResult(mapToResponse(run), true);
    }

    /** Newest first. Progress of active runs is refreshed before the page is read. */
    @Transactional
    public LetterRunPageResponse getLetterRuns(int page, int size) {
        letterRunProgressService.refreshActiveRuns();
        Page<LetterRun> runs = letterRunRepository.findAllByOrderByRequestedAtDescIdDesc(PageRequest.of(page, size));
        return new LetterRunPageResponse(runs.map(this::mapToResponse).getContent(), runs.getTotalElements());
    }

    @Transactional
    public LetterRunResponse getLetterRunById(Long id) {
        LetterRun run = findWithLockOrThrow(id);
        letterRunProgressService.refresh(run);
        return mapToResponse(run);
    }

    /**
     * Makes a run's letters visible to external users. Allowed only for a COMPLETE run that is still
     * STAFF_ONLY. Progress is refreshed first, so a run whose last letter has just appeared can be
     * released without waiting for the next refresh. The row lock makes concurrent releases queue up.
     */
    @Transactional
    public LetterRunResponse releaseLetterRun(Long id, String actor) {
        LetterRun run = findWithLockOrThrow(id);
        letterRunProgressService.refresh(run);

        if (run.getVisibility() == RunVisibility.STAFF_AND_EXTERNAL) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "ALREADY_RELEASED",
                    "This run is already visible to external users.");
        }
        if (run.getStatus() != RunStatus.COMPLETE) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "NOT_COMPLETE",
                    "Available when generation completes.");
        }

        Instant now = clock.instant();
        run.setVisibility(RunVisibility.STAFF_AND_EXTERNAL);
        run.setReleasedBy(actor);
        run.setReleasedAt(now);
        LetterRun releasedRun = letterRunRepository.save(run);
        letterRunAuditRepository.save(new LetterRunAudit(releasedRun, AuditAction.RELEASE, actor, now));
        return mapToResponse(releasedRun);
    }

    /**
     * Procedure for a mistaken release (no UI): hides the run's letters from external users again.
     * released_by / released_at are cleared so the run reads as "Staff only"; who released and
     * revoked it, and when, stays in letter_run_audit.
     */
    @Transactional
    public LetterRunResponse revokeLetterRun(Long id, String actor) {
        LetterRun run = findWithLockOrThrow(id);
        if (run.getVisibility() != RunVisibility.STAFF_AND_EXTERNAL) {
            throw new BusinessRuleException(HttpStatus.CONFLICT, "NOT_RELEASED",
                    "This run is not visible to external users.");
        }

        run.setVisibility(RunVisibility.STAFF_ONLY);
        run.setReleasedBy(null);
        run.setReleasedAt(null);
        LetterRun revokedRun = letterRunRepository.save(run);
        letterRunAuditRepository.save(new LetterRunAudit(revokedRun, AuditAction.REVOKE, actor, clock.instant()));
        return mapToResponse(revokedRun);
    }

    private LetterRun findWithLockOrThrow(Long id) {
        return letterRunRepository.findWithLockById(id)
                .orElseThrow(() -> new EntityNotFoundException("Letter run not found with id: " + id));
    }

    private static BusinessRuleException idempotencyKeyReused() {
        return new BusinessRuleException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different request.");
    }

    private LetterRunResponse mapToResponse(LetterRun run) {
        return new LetterRunResponse(
                run.getId(),
                run.getLetterType().getCode(),
                run.getLetterType().getName(),
                run.getClaimSubmittedDate(),
                run.getStatus(),
                run.getVisibility(),
                run.getTotalCount(),
                run.getGeneratedCount(),
                run.getRequestedBy(),
                run.getRequestedAt(),
                run.getReleasedBy(),
                run.getReleasedAt()
        );
    }
}
