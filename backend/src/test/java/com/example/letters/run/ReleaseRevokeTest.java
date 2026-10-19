package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.common.exception.BusinessRuleException;
import com.example.letters.support.IntegrationTest;

import jakarta.persistence.EntityNotFoundException;

/** Release and revoke rules against the real database. HTTP and roles: LetterRunControllerTest. */
class ReleaseRevokeTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";

    @Autowired
    LetterRunService service;

    // --- Release ---

    @Test
    void releaseRecordsTheActorAndAudits() {
        long runId = completedRun();
        clock.advance(Duration.ofHours(2));

        LetterRunResponse released = service.releaseLetterRun(runId, "staff");

        assertThat(released.visibility()).isEqualTo(RunVisibility.STAFF_AND_EXTERNAL);
        assertThat(released.releasedBy()).isEqualTo("staff");
        assertThat(released.releasedAt()).isEqualTo(START.plus(Duration.ofHours(2)));
        assertThat(audit(runId)).containsExactly("GENERATE:admin", "RELEASE:staff");
    }

    @Test
    void releaseIsBlockedWhileGenerating() {
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);

        assertThatThrownBy(() -> service.releaseLetterRun(runId, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("NOT_COMPLETE");
                });
        assertThat(audit(runId)).containsExactly("GENERATE:admin");
    }

    @Test
    void releaseIsBlockedWhenNeedsAttention() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        long runId = generateRun(TYPE);
        data.document("DOC-1", "APP-1", TYPE); // one of two generated, then the run stalls
        jdbc.update("UPDATE letter_run SET status = 'NEEDS_ATTENTION' WHERE run_id = ?", runId);

        assertThatThrownBy(() -> service.releaseLetterRun(runId, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_COMPLETE"));
    }

    @Test
    void releaseIsBlockedWhenAlreadyReleased() {
        long runId = completedRun();
        service.releaseLetterRun(runId, "staff");

        assertThatThrownBy(() -> service.releaseLetterRun(runId, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ALREADY_RELEASED"));
        assertThat(audit(runId)).containsExactly("GENERATE:admin", "RELEASE:staff");
    }

    @Test
    void releaseSucceedsAsSoonAsTheLastLetterExistsEvenBeforeAListRefresh() {
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);
        data.document("DOC-1", "APP-1", TYPE); // status in the table is still GENERATING

        LetterRunResponse released = service.releaseLetterRun(runId, "staff");

        assertThat(released.status()).isEqualTo(RunStatus.COMPLETE);
        assertThat(released.visibility()).isEqualTo(RunVisibility.STAFF_AND_EXTERNAL);
    }

    @Test
    void releaseOfAnUnknownRunIsNotFound() {
        assertThatThrownBy(() -> service.releaseLetterRun(999L, "staff"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Letter run not found with id: 999");
    }

    // --- Revoke ---

    @Test
    void revokeHidesTheRunAgainClearsTheReleaseAndAudits() {
        long runId = completedRun();
        service.releaseLetterRun(runId, "staff");

        LetterRunResponse revoked = service.revokeLetterRun(runId, "admin");

        assertThat(revoked.visibility()).isEqualTo(RunVisibility.STAFF_ONLY);
        assertThat(revoked.releasedBy()).isNull();
        assertThat(revoked.releasedAt()).isNull();
        assertThat(audit(runId)).containsExactly("GENERATE:admin", "RELEASE:staff", "REVOKE:admin");
    }

    @Test
    void aRevokedRunCanBeReleasedAgain() {
        long runId = completedRun();
        service.releaseLetterRun(runId, "staff");
        service.revokeLetterRun(runId, "admin");

        assertThat(service.releaseLetterRun(runId, "staff").visibility()).isEqualTo(RunVisibility.STAFF_AND_EXTERNAL);
    }

    @Test
    void revokeOfAnUnreleasedRunIsRejected() {
        long runId = completedRun();

        assertThatThrownBy(() -> service.revokeLetterRun(runId, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("NOT_RELEASED"));
    }

    private long completedRun() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        long runId = generateRun(TYPE);
        produceLetters(runId);
        return runId;
    }

    private List<String> audit(long runId) {
        return jdbc.queryForList(
                "SELECT action || ':' || actor FROM letter_run_audit WHERE run_id = ? ORDER BY audit_id",
                String.class, runId);
    }
}
