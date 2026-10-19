package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.support.IntegrationTest;

class ProgressTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";

    @Autowired
    LetterRunService service;

    @Autowired
    LetterRunProgressService progress;

    @Autowired
    LetterRunRepository runs;

    @Test
    void progressAdvancesAsDocumentsAppear() {
        long runId = generateRunFor("APP-1", "APP-2", "APP-3");

        clock.advance(Duration.ofMinutes(1));
        data.document("DOC-1", "APP-1", TYPE);
        progress.refreshActiveRuns();

        LetterRun run = run(runId);
        assertThat(run.getGeneratedCount()).isEqualTo(1);
        assertThat(run.getStatus()).isEqualTo(RunStatus.GENERATING);
        assertThat(run.getLastProgressAt()).isEqualTo(START.plus(Duration.ofMinutes(1)));
    }

    @Test
    void noNewDocumentsLeavesLastProgressUnchanged() {
        long runId = generateRunFor("APP-1", "APP-2");

        clock.advance(Duration.ofMinutes(5));
        progress.refreshActiveRuns();

        assertThat(run(runId).getLastProgressAt()).isEqualTo(START);
        assertThat(run(runId).getGeneratedCount()).isZero();
    }

    @Test
    void allDocumentsPresentCompletesTheRun() {
        long runId = generateRunFor("APP-1", "APP-2");

        clock.advance(Duration.ofMinutes(2));
        data.document("DOC-1", "APP-1", TYPE);
        data.document("DOC-2", "APP-2", TYPE);
        progress.refreshActiveRuns();

        LetterRun run = run(runId);
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETE);
        assertThat(run.getGeneratedCount()).isEqualTo(2);
        assertThat(run.getCompletedAt()).isEqualTo(START.plus(Duration.ofMinutes(2)));
    }

    @Test
    void completeFreezesTheCounts() {
        long runId = generateRunFor("APP-1", "APP-2");
        data.document("DOC-1", "APP-1", TYPE);
        data.document("DOC-2", "APP-2", TYPE);
        progress.refreshActiveRuns();
        Instant completedAt = run(runId).getCompletedAt();

        // Staff later delete a letter (regeneration). The finished run is not recounted.
        data.deleteDocument("DOC-1");
        clock.advance(Duration.ofHours(1));
        progress.refreshActiveRuns();

        LetterRun run = run(runId);
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETE);
        assertThat(run.getGeneratedCount()).isEqualTo(2);
        assertThat(run.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void progressOnANeedsAttentionRunMovesItBackToGenerating() {
        long runId = generateRunFor("APP-1", "APP-2");
        jdbc.update("UPDATE letter_run SET status = 'NEEDS_ATTENTION' WHERE run_id = ?", runId);

        data.document("DOC-1", "APP-1", TYPE);
        progress.refreshActiveRuns();

        assertThat(run(runId).getStatus()).isEqualTo(RunStatus.GENERATING);
        assertThat(run(runId).getGeneratedCount()).isEqualTo(1);
    }

    @Test
    void documentsOfAnotherTypeDoNotCount() {
        long runId = generateRunFor("APP-1");
        data.document("DOC-1", "APP-1", "LETTER_B");

        progress.refreshActiveRuns();

        assertThat(run(runId).getGeneratedCount()).isZero();
    }

    @Test
    void listRefreshesProgressBeforeReturning() {
        long runId = generateRunFor("APP-1", "APP-2");
        data.document("DOC-1", "APP-1", TYPE);

        var page = service.getLetterRuns(0, 20);

        assertThat(page.items()).singleElement().satisfies(run -> {
            assertThat(run.runId()).isEqualTo(runId);
            assertThat(run.generatedCount()).isEqualTo(1);
        });
    }

    private long generateRunFor(String... appIds) {
        for (String appId : appIds) {
            data.eligibleApplication(appId, TYPE);
        }
        return service.generateLetters(new LetterRunRequest(TYPE, LocalDate.of(2026, 2, 1)), "key-" + System.nanoTime(),
                "admin").run().runId();
    }

    private LetterRun run(long runId) {
        return runs.findById(runId).orElseThrow();
    }
}
