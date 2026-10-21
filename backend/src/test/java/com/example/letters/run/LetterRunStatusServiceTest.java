package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.letters.config.LettersProperties;
import com.example.letters.lettertype.LetterType;

/** The status rules and their order, with the repositories mocked. */
@ExtendWith(MockitoExtension.class)
class LetterRunStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");
    private static final Duration THRESHOLD = Duration.ofHours(4);

    @Mock LetterRunRepository letterRunRepository;
    @Mock LetterRunItemRepository letterRunItemRepository;

    private LetterRunStatusService statusService;

    @BeforeEach
    void setUp() {
        statusService = new LetterRunStatusService(letterRunRepository, letterRunItemRepository,
                new LettersProperties(THRESHOLD, Duration.ofMinutes(1)), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static LetterRun run(Instant requestedAt, int total) {
        LetterRun run = new LetterRun("PLAN-1A", new LetterType("LETTER_A", "Letter type A"),
                LocalDate.of(2026, 2, 1), "admin", requestedAt);
        run.setId(7L);
        run.setTotalRequested(total);
        return run;
    }

    @Test
    void aCompletedRunIsCompleteAtTotalOverTotalWithoutCounting() {
        LetterRun run = run(NOW.minus(Duration.ofDays(9)), 5);
        run.setCompletedAt(NOW.minus(Duration.ofDays(8)));

        assertThat(statusService.calculate(run)).isEqualTo(new LetterRunStatusService.RunProgress(5, RunStatus.COMPLETE));
        verify(letterRunItemRepository, never()).countGeneratedByRunId(anyLong());
    }

    @Test
    void allLettersGeneratedSetsCompletedAtOnce() {
        LetterRun run = run(NOW.minus(Duration.ofMinutes(5)), 5);
        when(letterRunItemRepository.countGeneratedByRunId(7L)).thenReturn(5L);
        when(letterRunRepository.markCompleted(7L, NOW)).thenReturn(1);

        assertThat(statusService.calculate(run).status()).isEqualTo(RunStatus.COMPLETE);
        assertThat(run.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void completeWinsOverNeedsAttention() {
        LetterRun run = run(NOW.minus(THRESHOLD.multipliedBy(3)), 2);
        when(letterRunItemRepository.countGeneratedByRunId(7L)).thenReturn(2L);
        when(letterRunRepository.markCompleted(7L, NOW)).thenReturn(1);

        assertThat(statusService.calculate(run).status()).isEqualTo(RunStatus.COMPLETE);
    }

    @Test
    void anotherCallerCompletingFirstLeavesItsCompletedAt() {
        LetterRun run = run(NOW.minus(Duration.ofMinutes(5)), 5);
        when(letterRunItemRepository.countGeneratedByRunId(7L)).thenReturn(5L);
        when(letterRunRepository.markCompleted(7L, NOW)).thenReturn(0);

        assertThat(statusService.calculate(run).status()).isEqualTo(RunStatus.COMPLETE);
        assertThat(run.getCompletedAt()).isNull(); // not overwritten in this persistence context
    }

    @Test
    void olderThanTheThresholdNeedsAttentionWinsOverQueuedAndInProgress() {
        LetterRun run = run(NOW.minus(THRESHOLD).minusSeconds(1), 5);
        when(letterRunItemRepository.countGeneratedByRunId(7L)).thenReturn(0L);

        assertThat(statusService.calculate(run))
                .isEqualTo(new LetterRunStatusService.RunProgress(0, RunStatus.NEEDS_ATTENTION));
    }

    @Test
    void exactlyAtTheThresholdIsNotYetNeedsAttention() {
        LetterRun run = run(NOW.minus(THRESHOLD), 5);
        when(letterRunItemRepository.countGeneratedByRunId(7L)).thenReturn(2L);

        assertThat(statusService.calculate(run).status()).isEqualTo(RunStatus.IN_PROGRESS);
    }

    @Test
    void nothingGeneratedIsQueued() {
        LetterRun run = run(NOW.minus(Duration.ofMinutes(1)), 5);
        when(letterRunItemRepository.countGeneratedByRunId(7L)).thenReturn(0L);

        assertThat(statusService.calculate(run)).isEqualTo(new LetterRunStatusService.RunProgress(0, RunStatus.QUEUED));
    }

    @Test
    void someGeneratedIsInProgress() {
        LetterRun run = run(NOW.minus(Duration.ofMinutes(1)), 5);
        when(letterRunItemRepository.countGeneratedByRunId(7L)).thenReturn(3L);

        assertThat(statusService.calculate(run))
                .isEqualTo(new LetterRunStatusService.RunProgress(3, RunStatus.IN_PROGRESS));
    }
}
