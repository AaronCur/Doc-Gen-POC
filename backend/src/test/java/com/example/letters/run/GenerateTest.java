package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.common.exception.BusinessRuleException;
import com.example.letters.support.IntegrationTest;

class GenerateTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";
    private static final LocalDate CLAIM_DATE = LocalDate.of(2026, 2, 1);
    private static final LetterRunRequest REQUEST = new LetterRunRequest(PLAN, TYPE, CLAIM_DATE, "Test run");

    @Autowired
    LetterRunService service;

    @Test
    void writesTheRunItemsAndQueueRows() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        data.eligibleApplication("APP-3", TYPE);
        data.document("DOC-1", "APP-3", TYPE); // already has a letter: excluded

        LetterRunResponse run = service.generateLetters(REQUEST, "admin");

        assertThat(run.letterType()).isEqualTo(TYPE);
        assertThat(run.letterTypeName()).isEqualTo("Letter type A");
        assertThat(run.claimDeadlineDate()).isEqualTo(CLAIM_DATE);
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.visibility()).isEqualTo(RunVisibility.STAFF_ONLY);
        assertThat(run.totalCount()).isEqualTo(2);
        assertThat(run.generatedCount()).isZero();
        assertThat(run.requestedBy()).isEqualTo("admin");
        assertThat(run.requestedAt()).isEqualTo(START);

        assertThat(run.planId()).isEqualTo(PLAN);
        assertThat(jdbc.queryForMap(
                "SELECT plan_id, claim_deadline_date, total_requested FROM letter_run WHERE run_id = ?", run.runId()))
                .containsEntry("PLAN_ID", PLAN)
                .containsEntry("CLAIM_DEADLINE_DATE", java.sql.Date.valueOf(CLAIM_DATE))
                .containsEntry("TOTAL_REQUESTED", 2);
        assertThat(jdbc.queryForList(
                "SELECT app_id || ':' || letter_type FROM letter_run_item WHERE run_id = ? ORDER BY app_id",
                String.class, run.runId()))
                .containsExactly("APP-1:LETTER_A", "APP-2:LETTER_A");
        // The queue key is built in the queue insert: appid_lettertype.
        assertThat(jdbc.queryForList(
                "SELECT queue_key FROM letter_request_queue WHERE generated_flag = 'N' ORDER BY queue_key", String.class))
                .containsExactly("APP-1_LETTER_A", "APP-2_LETTER_A");
    }

    @Test
    void noEligibleApplicationsReturns422AndWritesNothing() {
        data.eligibleApplication("APP-1", TYPE);
        data.document("DOC-1", "APP-1", TYPE);

        assertThatThrownBy(() -> service.generateLetters(REQUEST, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getCode()).isEqualTo("NO_ELIGIBLE_APPLICATIONS");
                });

        assertNothingWritten();
    }

    @Test
    void aSecondGenerateWhileOneIsPendingReturns409() {
        data.eligibleApplication("APP-1", TYPE);
        service.generateLetters(REQUEST, "admin");
        data.eligibleApplication("APP-2", TYPE);

        assertThatThrownBy(() -> service.generateLetters(REQUEST, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
                });
        assertThat(count("SELECT COUNT(*) FROM letter_run")).isEqualTo(1);
    }

    @Test
    void theSameTypeInAnotherPlanIsNotBlocked() {
        data.eligibleApplicationInPlan("PLAN-A", "APP-1", TYPE);
        data.eligibleApplicationInPlan("PLAN-B", "APP-2", TYPE);
        service.generateLetters(new LetterRunRequest("PLAN-A", TYPE, CLAIM_DATE, "Test run"), "admin");

        LetterRunResponse planB = service.generateLetters(
                new LetterRunRequest("PLAN-B", TYPE, CLAIM_DATE, "Test run"), "admin");

        assertThat(planB.planId()).isEqualTo("PLAN-B");
        assertThat(jdbc.queryForList(
                "SELECT app_id FROM letter_run_item WHERE run_id = ?", String.class, planB.runId()))
                .containsExactly("APP-2");
        assertThat(count("SELECT COUNT(*) FROM letter_run")).isEqualTo(2);
    }

    @Test
    void aCompletedRunDoesNotBlockTheNextOne() {
        data.eligibleApplication("APP-1", TYPE);
        long firstRun = service.generateLetters(REQUEST, "admin").runId();
        produceLetters(firstRun);
        data.eligibleApplication("APP-2", TYPE);

        assertThat(service.generateLetters(REQUEST, "admin").totalCount()).isEqualTo(1);
    }

    @Test
    void aRunWhoseLettersHaveAllArrivedDoesNotBlockEvenBeforeItIsMarkedComplete() {
        // The documents exist but nothing has read the run or run the job, so completed_at is still
        // null. Generate recalculates the run first, finds it complete, and goes ahead.
        data.eligibleApplication("APP-1", TYPE);
        long firstRun = service.generateLetters(REQUEST, "admin").runId();
        simulateGeneratorProcessedEverything();
        data.eligibleApplication("APP-2", TYPE);

        assertThat(service.generateLetters(REQUEST, "admin").totalCount()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM letter_run WHERE run_id = ? AND completed_at IS NOT NULL", firstRun))
                .isEqualTo(1);
    }

    @Test
    void aRepeatedClickIsRejectedRatherThanDuplicated() {
        // Without an idempotency key, the unfinished-run check is what stops a double submit.
        data.eligibleApplication("APP-1", TYPE);
        service.generateLetters(REQUEST, "admin");

        assertThatThrownBy(() -> service.generateLetters(REQUEST, "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS"));
        assertThat(count("SELECT COUNT(*) FROM letter_request_queue")).isEqualTo(1);
    }

    @Test
    void aPendingQueueRowFromAnotherSourceNoLongerBlocksGenerateButItsApplicationIsSkipped() {
        // e.g. a datafix queued APP-1 and the generator has not processed it yet. The 409 is now
        // about runs, not queue rows; the eligibility rule still leaves APP-1 out.
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        data.queueRow("APP-1", TYPE, 'N');

        LetterRunResponse run = service.generateLetters(REQUEST, "admin");

        assertThat(jdbc.queryForList("SELECT app_id FROM letter_run_item WHERE run_id = ?", String.class, run.runId()))
                .containsExactly("APP-2");
    }

    @Test
    void aPendingRowForAnotherTypeDoesNotBlock() {
        data.eligibleApplication("APP-1", TYPE);
        data.queueRow("APP-1", "LETTER_B", 'N');

        assertThat(service.generateLetters(REQUEST, "admin").totalCount()).isEqualTo(1);
    }

    @Test
    void aRepeatRunPicksUpOnlyApplicationsWithoutALetter() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        long firstRun = service.generateLetters(REQUEST, "admin").runId();
        simulateGeneratorProcessedEverything();

        data.eligibleApplication("APP-3", TYPE);
        LetterRunResponse second = service.generateLetters(REQUEST, "admin");

        assertThat(second.runId()).isNotEqualTo(firstRun);
        assertThat(second.totalCount()).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT app_id FROM letter_run_item WHERE run_id = ?", String.class, second.runId()))
                .containsExactly("APP-3");
    }

    @Test
    void anUnknownLetterTypeIsRejectedWith400() {
        assertThatThrownBy(() -> service.generateLetters(
                new LetterRunRequest(PLAN, "LETTER_Z", CLAIM_DATE, "Test run"), "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("UNKNOWN_LETTER_TYPE");
                });
        assertNothingWritten();
    }

    /** What the external letter-generator does: a document per pending row, then flag Y. */
    private void simulateGeneratorProcessedEverything() {
        List<String> keys = jdbc.queryForList(
                "SELECT queue_key FROM letter_request_queue WHERE generated_flag = 'N'", String.class);
        for (String key : keys) {
            String appId = key.substring(0, key.indexOf('_'));
            data.document("DOC-" + appId, appId, TYPE);
        }
        jdbc.update("UPDATE letter_request_queue SET generated_flag = 'Y' WHERE generated_flag = 'N'");
    }

    private void assertNothingWritten() {
        assertThat(count("SELECT COUNT(*) FROM letter_run")).isZero();
        assertThat(count("SELECT COUNT(*) FROM letter_run_item")).isZero();
        assertThat(count("SELECT COUNT(*) FROM letter_request_queue")).isZero();
    }
}
