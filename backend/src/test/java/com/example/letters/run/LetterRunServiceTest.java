package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.letters.common.exception.BusinessRuleException;
import com.example.letters.lettertype.LetterType;
import com.example.letters.lettertype.LetterTypeLock;
import com.example.letters.lettertype.LetterTypeLockRepository;
import com.example.letters.lettertype.LetterTypeRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * The order of the generate checks and the release/revoke rules, with the repositories mocked.
 * The SQL, locking and transactions are tested against the database in GenerateTest,
 * GenerateConcurrencyTest and ReleaseRevokeTest.
 */
@ExtendWith(MockitoExtension.class)
class LetterRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");
    private static final LocalDate CLAIM_DATE = LocalDate.of(2026, 2, 1);
    private static final LetterRunRequest REQUEST = new LetterRunRequest("LETTER_A", CLAIM_DATE);

    @Mock LetterRunRepository letterRunRepository;
    @Mock LetterRunItemRepository letterRunItemRepository;
    @Mock LetterRunAuditRepository letterRunAuditRepository;
    @Mock LetterTypeRepository letterTypeRepository;
    @Mock LetterTypeLockRepository letterTypeLockRepository;
    @Mock LetterRequestQueueRepository letterRequestQueueRepository;
    @Mock LetterRunProgressService letterRunProgressService;

    private final LetterType letterTypeA = new LetterType("LETTER_A", "Letter type A");

    private LetterRunService letterRunService;

    @BeforeEach
    void setUp() {
        letterRunService = new LetterRunService(letterRunRepository, letterRunItemRepository, letterRunAuditRepository,
                letterTypeRepository, letterTypeLockRepository, letterRequestQueueRepository,
                letterRunProgressService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LetterRun existingRun(String idempotencyKey) {
        LetterRun run = new LetterRun(letterTypeA, CLAIM_DATE, "admin", idempotencyKey, NOW);
        run.setId(7L);
        run.setTotalCount(3);
        return run;
    }

    private void typeIsLocked() {
        when(letterTypeLockRepository.findWithLockByLetterType("LETTER_A")).thenReturn(Optional.of(new LetterTypeLock()));
    }

    // --- Generate ---

    @Test
    void generateCreatesARunWhenApplicationsAreEligible() {
        typeIsLocked();
        when(letterRunRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(letterRequestQueueRepository.countPending("LETTER_A")).thenReturn(0);
        when(letterTypeRepository.findById("LETTER_A")).thenReturn(Optional.of(letterTypeA));
        when(letterRunRepository.saveAndFlush(any(LetterRun.class))).thenAnswer(invocation -> {
            LetterRun run = invocation.getArgument(0);
            run.setId(7L);
            return run;
        });
        when(letterRunItemRepository.insertForEligibleApplications(7L, "LETTER_A", NOW)).thenReturn(3);

        LetterRunService.GenerateResult result = letterRunService.generateLetters(REQUEST, "key-1", "admin");

        assertThat(result.created()).isTrue();
        assertThat(result.run().runId()).isEqualTo(7L);
        assertThat(result.run().totalCount()).isEqualTo(3);
        assertThat(result.run().status()).isEqualTo(RunStatus.GENERATING);
        assertThat(result.run().visibility()).isEqualTo(RunVisibility.STAFF_ONLY);
        verify(letterRequestQueueRepository).insertPendingForRun(7L);
        verify(letterRunAuditRepository).save(any(LetterRunAudit.class));
    }

    @Test
    void generateRejectsAnUnknownLetterTypeBeforeAnythingElse() {
        when(letterTypeLockRepository.findWithLockByLetterType("LETTER_Z")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> letterRunService.generateLetters(new LetterRunRequest("LETTER_Z", CLAIM_DATE), "key-1",
                "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_LETTER_TYPE"));
        verify(letterRunRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void generateReturnsTheOriginalRunForARepeatedKeyWithoutCheckingTheQueue() {
        typeIsLocked();
        when(letterRunRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existingRun("key-1")));

        LetterRunService.GenerateResult result = letterRunService.generateLetters(REQUEST, "key-1", "admin");

        assertThat(result.created()).isFalse();
        assertThat(result.run().runId()).isEqualTo(7L);
        verify(letterRequestQueueRepository, never()).countPending(anyString());
        verify(letterRunRepository, never()).saveAndFlush(any());
    }

    @Test
    void generateRejectsAKeyReusedForADifferentDate() {
        typeIsLocked();
        when(letterRunRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existingRun("key-1")));

        assertThatThrownBy(() -> letterRunService.generateLetters(
                new LetterRunRequest("LETTER_A", CLAIM_DATE.plusDays(1)), "key-1", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void generateRejectsWhileQueueRowsArePending() {
        typeIsLocked();
        when(letterRunRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(letterRequestQueueRepository.countPending("LETTER_A")).thenReturn(4);

        assertThatThrownBy(() -> letterRunService.generateLetters(REQUEST, "key-1", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
                });
        verify(letterRunRepository, never()).saveAndFlush(any());
    }

    @Test
    void generateRejectsWhenNothingIsEligibleAndQueuesNothing() {
        typeIsLocked();
        when(letterRunRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(letterRequestQueueRepository.countPending("LETTER_A")).thenReturn(0);
        when(letterTypeRepository.findById("LETTER_A")).thenReturn(Optional.of(letterTypeA));
        when(letterRunRepository.saveAndFlush(any(LetterRun.class))).thenAnswer(invocation -> {
            LetterRun run = invocation.getArgument(0);
            run.setId(7L);
            return run;
        });
        when(letterRunItemRepository.insertForEligibleApplications(7L, "LETTER_A", NOW)).thenReturn(0);

        assertThatThrownBy(() -> letterRunService.generateLetters(REQUEST, "key-1", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getCode()).isEqualTo("NO_ELIGIBLE_APPLICATIONS");
                });
        verify(letterRequestQueueRepository, never()).insertPendingForRun(anyLong());
    }

    // --- Release and revoke ---

    @Test
    void releaseRecordsTheActorAndTime() {
        LetterRun run = existingRun("key-1");
        run.setStatus(RunStatus.COMPLETE);
        when(letterRunRepository.findWithLockById(7L)).thenReturn(Optional.of(run));
        when(letterRunRepository.save(run)).thenReturn(run);

        LetterRunResponse released = letterRunService.releaseLetterRun(7L, "staff");

        assertThat(released.visibility()).isEqualTo(RunVisibility.STAFF_AND_EXTERNAL);
        assertThat(released.releasedBy()).isEqualTo("staff");
        assertThat(released.releasedAt()).isEqualTo(NOW);
        verify(letterRunProgressService).refresh(run);
    }

    @Test
    void releaseRejectsARunThatIsNotComplete() {
        when(letterRunRepository.findWithLockById(7L)).thenReturn(Optional.of(existingRun("key-1")));

        assertThatThrownBy(() -> letterRunService.releaseLetterRun(7L, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_COMPLETE"));
    }

    @Test
    void releaseRejectsARunThatIsAlreadyReleased() {
        LetterRun run = existingRun("key-1");
        run.setStatus(RunStatus.COMPLETE);
        run.setVisibility(RunVisibility.STAFF_AND_EXTERNAL);
        when(letterRunRepository.findWithLockById(7L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> letterRunService.releaseLetterRun(7L, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ALREADY_RELEASED"));
    }

    @Test
    void releaseOfAnUnknownRunIsNotFound() {
        when(letterRunRepository.findWithLockById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> letterRunService.releaseLetterRun(99L, "staff"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Letter run not found with id: 99");
    }

    @Test
    void revokeRejectsARunThatIsNotReleased() {
        when(letterRunRepository.findWithLockById(7L)).thenReturn(Optional.of(existingRun("key-1")));

        assertThatThrownBy(() -> letterRunService.revokeLetterRun(7L, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_RELEASED"));
    }
}
