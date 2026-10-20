package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.common.exception.BusinessRuleException;
import com.example.letters.support.IntegrationTest;

import jakarta.persistence.EntityNotFoundException;

/** Release against the real database: one guarded UPDATE. HTTP and roles: LetterRunControllerTest. */
class ReleaseTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";

    @Autowired
    LetterRunService service;

    @Test
    void releaseRecordsWhoAndWhen() {
        long runId = completedRun();
        clock.advance(Duration.ofHours(2));

        LetterRunResponse released = service.releaseLetterRun(runId, "staff");

        assertThat(released.visibility()).isEqualTo(RunVisibility.STAFF_AND_EXTERNAL);
        assertThat(released.releasedBy()).isEqualTo("staff");
        assertThat(released.releasedAt()).isEqualTo(START.plus(Duration.ofHours(2)));
        assertThat(released.status()).isEqualTo(RunStatus.COMPLETE);
    }

    @Test
    void releaseChangesOnlyTheVisibilityAndReleaseColumns() {
        long runId = completedRun();
        var before = jdbc.queryForMap(
                "SELECT letter_type, claim_submitted_date, total_requested, requested_by, requested_at, completed_at "
                        + "FROM letter_run WHERE run_id = ?", runId);

        service.releaseLetterRun(runId, "staff");

        assertThat(jdbc.queryForMap(
                "SELECT letter_type, claim_submitted_date, total_requested, requested_by, requested_at, completed_at "
                        + "FROM letter_run WHERE run_id = ?", runId)).isEqualTo(before);
    }

    @Test
    void releaseIsBlockedWhileQueued() {
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);

        assertNotComplete(runId);
        assertThat(visibility(runId)).isEqualTo("STAFF_ONLY");
    }

    @Test
    void releaseIsBlockedWhileInProgress() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        long runId = generateRun(TYPE);
        data.document("DOC-1", "APP-1", TYPE);

        assertNotComplete(runId);
    }

    @Test
    void releaseIsBlockedWhenTheRunNeedsAttention() {
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);
        clock.advance(Duration.ofHours(5));

        assertNotComplete(runId);
    }

    @Test
    void releaseNeedsCompletedAtToBeSet() {
        // Every letter exists, but nothing has read the run or run the job yet, so completed_at
        // is still null and the guarded UPDATE does not match.
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);
        data.document("DOC-1", "APP-1", TYPE);
        assertNotComplete(runId);

        // Any read (the page polling, or the job) sets it, and release then succeeds.
        service.getLetterRunById(runId);
        assertThat(service.releaseLetterRun(runId, "staff").visibility()).isEqualTo(RunVisibility.STAFF_AND_EXTERNAL);
    }

    @Test
    void releaseIsBlockedWhenAlreadyReleased() {
        long runId = completedRun();
        service.releaseLetterRun(runId, "staff");

        assertThatThrownBy(() -> service.releaseLetterRun(runId, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("ALREADY_RELEASED");
                });
        assertThat(jdbc.queryForObject("SELECT released_by FROM letter_run WHERE run_id = ?", String.class, runId))
                .isEqualTo("staff");
    }

    @Test
    void releaseOfAnUnknownRunIsNotFound() {
        assertThatThrownBy(() -> service.releaseLetterRun(999L, "staff"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Letter run not found with id: 999");
    }

    private long completedRun() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        long runId = generateRun(TYPE);
        produceLetters(runId);
        return runId;
    }

    private void assertNotComplete(long runId) {
        assertThatThrownBy(() -> service.releaseLetterRun(runId, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("NOT_COMPLETE");
                });
    }

    private String visibility(long runId) {
        return jdbc.queryForObject("SELECT visibility FROM letter_run WHERE run_id = ?", String.class, runId);
    }
}
