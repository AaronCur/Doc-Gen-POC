package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.support.IntegrationTest;

import jakarta.persistence.EntityNotFoundException;

/**
 * Status and generated count are calculated on read, never stored. Uses application.yaml:
 * needs-attention-after = 4h.
 */
class RunStatusTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";
    private static final Duration NEEDS_ATTENTION_AFTER = Duration.ofHours(4);

    @Autowired
    LetterRunService service;

    @Test
    void aNewRunIsQueued() {
        long runId = runFor("APP-1", "APP-2");

        LetterRunResponse run = service.getLetterRunById(runId);

        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.generatedCount()).isZero();
        assertThat(run.totalCount()).isEqualTo(2);
    }

    @Test
    void someLettersMakeItInProgress() {
        long runId = runFor("APP-1", "APP-2");
        data.document("DOC-1", "APP-1", TYPE);

        LetterRunResponse run = service.getLetterRunById(runId);

        assertThat(run.status()).isEqualTo(RunStatus.IN_PROGRESS);
        assertThat(run.generatedCount()).isEqualTo(1);
    }

    @Test
    void allLettersCompleteTheRunAndSetCompletedAt() {
        long runId = runFor("APP-1", "APP-2");
        clock.advance(Duration.ofMinutes(3));
        data.document("DOC-1", "APP-1", TYPE);
        data.document("DOC-2", "APP-2", TYPE);

        LetterRunResponse run = service.getLetterRunById(runId);

        assertThat(run.status()).isEqualTo(RunStatus.COMPLETE);
        assertThat(run.generatedCount()).isEqualTo(2);
        assertThat(completedAt(runId)).isEqualTo(START.plus(Duration.ofMinutes(3)));
    }

    @Test
    void aCompletedRunIsFrozenAtTotalOverTotal() {
        long runId = runFor("APP-1", "APP-2");
        data.document("DOC-1", "APP-1", TYPE);
        data.document("DOC-2", "APP-2", TYPE);
        service.getLetterRunById(runId); // sets completed_at
        Timestamp completedAt = completedAt(runId);

        // Staff later delete a letter (regeneration). The finished run is not recounted.
        data.deleteDocument("DOC-1");
        clock.advance(Duration.ofDays(1));
        LetterRunResponse run = service.getLetterRunById(runId);

        assertThat(run.status()).isEqualTo(RunStatus.COMPLETE);
        assertThat(run.generatedCount()).isEqualTo(2);
        assertThat(completedAt(runId)).isEqualTo(completedAt);
    }

    @Test
    void anIncompleteRunOlderThanTheThresholdNeedsAttention() {
        long runId = runFor("APP-1", "APP-2");
        data.document("DOC-1", "APP-1", TYPE);

        clock.advance(NEEDS_ATTENTION_AFTER);
        assertThat(service.getLetterRunById(runId).status()).isEqualTo(RunStatus.IN_PROGRESS);

        clock.advance(Duration.ofSeconds(1));
        LetterRunResponse run = service.getLetterRunById(runId);
        assertThat(run.status()).isEqualTo(RunStatus.NEEDS_ATTENTION);
        assertThat(run.generatedCount()).isEqualTo(1);
    }

    @Test
    void aQueuedRunCanAlsoNeedAttention() {
        long runId = runFor("APP-1");

        clock.advance(NEEDS_ATTENTION_AFTER.plusSeconds(1));

        assertThat(service.getLetterRunById(runId).status()).isEqualTo(RunStatus.NEEDS_ATTENTION);
    }

    @Test
    void aRunThatNeedsAttentionStillCompletesWhenTheLastLettersArrive() {
        long runId = runFor("APP-1");
        clock.advance(NEEDS_ATTENTION_AFTER.plusHours(1));
        data.document("DOC-1", "APP-1", TYPE);

        assertThat(service.getLetterRunById(runId).status()).isEqualTo(RunStatus.COMPLETE);
    }

    @Test
    void documentsOfAnotherTypeDoNotCount() {
        long runId = runFor("APP-1");
        data.document("DOC-1", "APP-1", "LETTER_B");

        assertThat(service.getLetterRunById(runId).generatedCount()).isZero();
    }

    @Test
    void theListCalculatesEachRunsStatus() {
        long runId = runFor("APP-1", "APP-2");
        data.document("DOC-1", "APP-1", TYPE);

        assertThat(service.getLetterRuns(PLAN, 0, 20).items()).singleElement().satisfies(run -> {
            assertThat(run.runId()).isEqualTo(runId);
            assertThat(run.status()).isEqualTo(RunStatus.IN_PROGRESS);
            assertThat(run.generatedCount()).isEqualTo(1);
        });
    }

    @Test
    void theListReturnsOnlyTheRequestedPlansRuns() {
        data.eligibleApplicationInPlan("PLAN-A", "APP-1", TYPE);
        data.eligibleApplicationInPlan("PLAN-B", "APP-2", TYPE);
        long runA = generateRun("PLAN-A", TYPE);
        long runB = generateRun("PLAN-B", TYPE);

        assertThat(service.getLetterRuns("PLAN-A", 0, 20).items()).extracting(LetterRunResponse::runId)
                .containsExactly(runA);
        assertThat(service.getLetterRuns("PLAN-B", 0, 20).items()).extracting(LetterRunResponse::runId)
                .containsExactly(runB);
        assertThat(service.getLetterRuns("PLAN-B", 0, 20).total()).isEqualTo(1);
        assertThat(service.getLetterRuns("PLAN-C", 0, 20).items()).isEmpty();
    }

    // --- Pending applications ---

    @Test
    void pendingListsTheApplicationsWithNoDocumentYet() {
        long runId = runFor("APP-1", "APP-2", "APP-3");
        data.document("DOC-2", "APP-2", TYPE);

        LetterRunPendingResponse pending = service.getPendingApplications(runId);

        assertThat(pending.runId()).isEqualTo(runId);
        assertThat(pending.pendingAppIds()).containsExactly("APP-1", "APP-3");
    }

    @Test
    void pendingForAnUnknownRunIsNotFound() {
        assertThatThrownBy(() -> service.getPendingApplications(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Letter run not found with id: 999");
    }

    private long runFor(String... appIds) {
        for (String appId : appIds) {
            data.eligibleApplication(appId, TYPE);
        }
        return generateRun(TYPE);
    }

    private Timestamp completedAt(long runId) {
        return jdbc.queryForObject("SELECT completed_at FROM letter_run WHERE run_id = ?", Timestamp.class, runId);
    }
}
