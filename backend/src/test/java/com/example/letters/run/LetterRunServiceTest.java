package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
 * The order of the generate checks and the release rules, with the repositories mocked. The SQL,
 * locking and transactions are tested against the database in GenerateTest,
 * GenerateConcurrencyTest and ReleaseTest.
 */
@ExtendWith(MockitoExtension.class)
class LetterRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");
    private static final LocalDate CLAIM_DATE = LocalDate.of(2026, 2, 1);
    private static final String PLAN = "PLAN-1A";
    private static final LetterRunRequest REQUEST =
            new LetterRunRequest(PLAN, "LETTER_A", CLAIM_DATE, "Annual decision letters");

    @Mock LetterRunRepository letterRunRepository;
    @Mock LetterRunItemRepository letterRunItemRepository;
    @Mock LetterTypeRepository letterTypeRepository;
    @Mock LetterTypeLockRepository letterTypeLockRepository;
    @Mock LetterRequestQueueRepository letterRequestQueueRepository;
    @Mock LetterRunStatusService letterRunStatusService;
    @Mock LetterRunCommentStore letterRunCommentStore;

    private final LetterType letterTypeA = new LetterType("LETTER_A", "Letter type A");

    private LetterRunService letterRunService;

    @BeforeEach
    void setUp() {
        letterRunService = new LetterRunService(letterRunRepository, letterRunItemRepository, letterTypeRepository,
                letterTypeLockRepository, letterRequestQueueRepository, letterRunStatusService, letterRunCommentStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LetterRun existingRun() {
        LetterRun run = new LetterRun(PLAN, letterTypeA, CLAIM_DATE, "admin", NOW);
        run.setId(7L);
        run.setTotalRequested(3);
        return run;
    }

    private void typeIsLocked() {
        when(letterTypeLockRepository.findWithLockByLetterType("LETTER_A")).thenReturn(Optional.of(new LetterTypeLock()));
    }

    private void runIsSavedWithId7() {
        when(letterTypeRepository.findById("LETTER_A")).thenReturn(Optional.of(letterTypeA));
        when(letterRunRepository.saveAndFlush(any(LetterRun.class))).thenAnswer(invocation -> {
            LetterRun run = invocation.getArgument(0);
            run.setId(7L);
            return run;
        });
    }

    // --- Generate ---

    private void noUnfinishedRunsInThePlan() {
        when(letterRunRepository.findAllByPlanIdAndLetterType_CodeAndCompletedAtIsNull(PLAN, "LETTER_A"))
                .thenReturn(List.of());
    }

    @Test
    void generateCreatesARunForThePlanAndPassesTheCommentToTheStore() {
        typeIsLocked();
        noUnfinishedRunsInThePlan();
        runIsSavedWithId7();
        when(letterRunItemRepository.insertForEligibleApplications(7L, "LETTER_A", PLAN)).thenReturn(3);
        when(letterRunStatusService.calculate(any(LetterRun.class)))
                .thenReturn(new LetterRunStatusService.RunProgress(0, RunStatus.QUEUED));

        LetterRunResponse run = letterRunService.generateLetters(REQUEST, "admin");

        assertThat(run.runId()).isEqualTo(7L);
        assertThat(run.planId()).isEqualTo(PLAN);
        assertThat(run.claimDeadlineDate()).isEqualTo(CLAIM_DATE);
        assertThat(run.totalCount()).isEqualTo(3);
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.visibility()).isEqualTo(RunVisibility.STAFF_ONLY);
        assertThat(run.requestedBy()).isEqualTo("admin");
        verify(letterRequestQueueRepository).insertPendingForRun(7L);
        verify(letterRunCommentStore).store(7L, "Annual decision letters", "admin");
    }

    @Test
    void generateRejectsAnUnknownLetterTypeBeforeAnythingElse() {
        when(letterTypeLockRepository.findWithLockByLetterType("LETTER_Z")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> letterRunService.generateLetters(
                new LetterRunRequest(PLAN, "LETTER_Z", CLAIM_DATE, "Test"), "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("UNKNOWN_LETTER_TYPE");
                });
        verify(letterRunRepository, never()).findAllByPlanIdAndLetterType_CodeAndCompletedAtIsNull(anyString(),
                anyString());
        verify(letterRunCommentStore, never()).store(any(), any(), any());
    }

    @Test
    void generateRejectsWhenThePlanHasAnUnfinishedRunOfTheType() {
        typeIsLocked();
        LetterRun unfinished = existingRun();
        when(letterRunRepository.findAllByPlanIdAndLetterType_CodeAndCompletedAtIsNull(PLAN, "LETTER_A"))
                .thenReturn(List.of(unfinished));
        when(letterRunStatusService.calculate(unfinished))
                .thenReturn(new LetterRunStatusService.RunProgress(1, RunStatus.IN_PROGRESS));

        assertThatThrownBy(() -> letterRunService.generateLetters(REQUEST, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
                });
        verify(letterRunRepository, never()).saveAndFlush(any());
        verify(letterRunCommentStore, never()).store(any(), any(), any());
    }

    @Test
    void generateGoesAheadWhenThePlansOnlyIncompleteRunTurnsOutComplete() {
        typeIsLocked();
        LetterRun lastLettersJustArrived = existingRun();
        when(letterRunRepository.findAllByPlanIdAndLetterType_CodeAndCompletedAtIsNull(PLAN, "LETTER_A"))
                .thenReturn(List.of(lastLettersJustArrived));
        when(letterRunStatusService.calculate(any(LetterRun.class)))
                .thenReturn(new LetterRunStatusService.RunProgress(3, RunStatus.COMPLETE));
        runIsSavedWithId7();
        when(letterRunItemRepository.insertForEligibleApplications(7L, "LETTER_A", PLAN)).thenReturn(2);

        assertThat(letterRunService.generateLetters(REQUEST, "admin").totalCount()).isEqualTo(2);
    }

    @Test
    void generateRejectsWhenNothingIsEligibleAndQueuesNothing() {
        typeIsLocked();
        noUnfinishedRunsInThePlan();
        runIsSavedWithId7();
        when(letterRunItemRepository.insertForEligibleApplications(7L, "LETTER_A", PLAN)).thenReturn(0);

        assertThatThrownBy(() -> letterRunService.generateLetters(REQUEST, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getCode()).isEqualTo("NO_ELIGIBLE_APPLICATIONS");
                });
        verify(letterRequestQueueRepository, never()).insertPendingForRun(anyLong());
        verify(letterRunCommentStore, never()).store(any(), any(), any());
    }

    // --- Release ---

    @Test
    void releaseIsOneGuardedUpdateThenReturnsTheRun() {
        LetterRun run = existingRun();
        run.setVisibility(RunVisibility.STAFF_AND_EXTERNAL);
        run.setReleasedBy("staff");
        run.setReleasedAt(NOW);
        when(letterRunRepository.release(7L, "staff", NOW)).thenReturn(1);
        when(letterRunRepository.findWithLetterTypeById(7L)).thenReturn(Optional.of(run));
        when(letterRunStatusService.calculate(run))
                .thenReturn(new LetterRunStatusService.RunProgress(3, RunStatus.COMPLETE));

        LetterRunResponse released = letterRunService.releaseLetterRun(7L, "staff");

        assertThat(released.visibility()).isEqualTo(RunVisibility.STAFF_AND_EXTERNAL);
        assertThat(released.releasedBy()).isEqualTo("staff");
    }

    @Test
    void releaseReportsAlreadyReleasedWhenNothingWasUpdated() {
        LetterRun run = existingRun();
        run.setVisibility(RunVisibility.STAFF_AND_EXTERNAL);
        when(letterRunRepository.release(eq(7L), eq("staff"), any())).thenReturn(0);
        when(letterRunRepository.findWithLetterTypeById(7L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> letterRunService.releaseLetterRun(7L, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ALREADY_RELEASED"));
    }

    @Test
    void releaseReportsNotCompleteWhenNothingWasUpdatedForAStaffOnlyRun() {
        when(letterRunRepository.release(eq(7L), eq("staff"), any())).thenReturn(0);
        when(letterRunRepository.findWithLetterTypeById(7L)).thenReturn(Optional.of(existingRun()));

        assertThatThrownBy(() -> letterRunService.releaseLetterRun(7L, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_COMPLETE"));
    }

    @Test
    void releaseOfAnUnknownRunIsNotFound() {
        when(letterRunRepository.release(eq(99L), eq("staff"), any())).thenReturn(0);
        when(letterRunRepository.findWithLetterTypeById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> letterRunService.releaseLetterRun(99L, "staff"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Letter run not found with id: 99");
    }

    // --- Pending ---

    @Test
    void pendingReturnsTheAppIdsWithoutADocument() {
        when(letterRunRepository.existsById(7L)).thenReturn(true);
        when(letterRunItemRepository.findPendingAppIdsByRunId(7L)).thenReturn(List.of("APP-0043"));

        assertThat(letterRunService.getPendingApplications(7L))
                .isEqualTo(new LetterRunPendingResponse(7L, List.of("APP-0043")));
    }

    @Test
    void pendingForAnUnknownRunIsNotFound() {
        when(letterRunRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> letterRunService.getPendingApplications(99L))
                .isInstanceOf(EntityNotFoundException.class);
        verify(letterRunItemRepository, never()).findPendingAppIdsByRunId(anyLong());
    }
}
