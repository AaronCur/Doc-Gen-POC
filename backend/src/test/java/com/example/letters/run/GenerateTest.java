package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.support.IntegrationTest;
import com.example.letters.common.exception.BusinessRuleException;

class GenerateTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";
    private static final LocalDate CLAIM_DATE = LocalDate.of(2026, 2, 1);

    @Autowired
    LetterRunService service;

    @Autowired
    LetterRunRepository runs;

    @Test
    void writesRunItemsQueueRowsAndAudit() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        data.eligibleApplication("APP-3", TYPE);
        data.document("DOC-1", "APP-3", TYPE); // already has a letter: excluded

        var result = service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin");

        assertThat(result.created()).isTrue();
        LetterRunResponse run = result.run();
        assertThat(run.letterType()).isEqualTo(TYPE);
        assertThat(run.letterTypeName()).isEqualTo("Letter type A");
        assertThat(run.claimSubmittedDate()).isEqualTo(CLAIM_DATE);
        assertThat(run.status()).isEqualTo(RunStatus.GENERATING);
        assertThat(run.visibility()).isEqualTo(RunVisibility.STAFF_ONLY);
        assertThat(run.totalCount()).isEqualTo(2);
        assertThat(run.generatedCount()).isZero();
        assertThat(run.requestedBy()).isEqualTo("admin");
        assertThat(run.requestedAt()).isEqualTo(START);
        assertThat(runs.findById(run.runId()).orElseThrow().getLastProgressAt()).isEqualTo(START);

        assertThat(jdbc.queryForList(
                "SELECT queue_key FROM letter_run_item WHERE run_id = ? ORDER BY app_id", String.class, run.runId()))
                .containsExactly("APP-1_LETTER_A", "APP-2_LETTER_A");
        assertThat(jdbc.queryForList(
                "SELECT queue_key FROM letter_request_queue WHERE generated_flag = 'N' ORDER BY queue_key", String.class))
                .containsExactly("APP-1_LETTER_A", "APP-2_LETTER_A");
        assertThat(jdbc.queryForList(
                "SELECT action FROM letter_run_audit WHERE run_id = ? AND actor = 'admin'", String.class, run.runId()))
                .containsExactly("GENERATE");
    }

    @Test
    void noEligibleApplicationsReturns422AndWritesNothing() {
        data.eligibleApplication("APP-1", TYPE);
        data.document("DOC-1", "APP-1", TYPE);

        assertThatThrownBy(() -> service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(422);
                    assertThat(e.getCode()).isEqualTo("NO_ELIGIBLE_APPLICATIONS");
                });

        assertNothingWritten();
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheSameRun() {
        data.eligibleApplication("APP-1", TYPE);

        var first = service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin");
        var second = service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin");

        assertThat(second.created()).isFalse();
        assertThat(second.run().runId()).isEqualTo(first.run().runId());
        assertThat(count("SELECT COUNT(*) FROM letter_run")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM letter_request_queue")).isEqualTo(1);
    }

    @Test
    void idempotencyKeyReusedForADifferentRequestReturns409() {
        data.eligibleApplication("APP-1", TYPE);
        service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin");

        assertThatThrownBy(() -> service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE.plusDays(1)), "key-1", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void secondGenerateWhileOneIsPendingReturns409() {
        data.eligibleApplication("APP-1", TYPE);
        service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin");
        data.eligibleApplication("APP-2", TYPE);

        assertThatThrownBy(() -> service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-2", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS");
                });
        assertThat(count("SELECT COUNT(*) FROM letter_run")).isEqualTo(1);
    }

    @Test
    void pendingRowFromAnotherSourceAlsoBlocksGenerate() {
        // e.g. a datafix inserted a row for this type and the generator has not processed it yet
        data.eligibleApplication("APP-1", TYPE);
        data.queueRow("APP-9", TYPE, 'N');

        assertThatThrownBy(() -> service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS"));
    }

    @Test
    void pendingRowForAnotherTypeDoesNotBlock() {
        data.eligibleApplication("APP-1", TYPE);
        data.queueRow("APP-1", "LETTER_B", 'N');

        assertThat(service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin").created()).isTrue();
    }

    @Test
    void repeatRunPicksUpOnlyApplicationsWithoutALetter() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        long firstRun = service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-1", "admin").run().runId();
        simulateGeneratorProcessedEverything();

        data.eligibleApplication("APP-3", TYPE);
        var second = service.generateLetters(new LetterRunRequest(TYPE, CLAIM_DATE), "key-2", "admin");

        assertThat(second.run().runId()).isNotEqualTo(firstRun);
        assertThat(second.run().totalCount()).isEqualTo(1);
        assertThat(jdbc.queryForList(
                "SELECT app_id FROM letter_run_item WHERE run_id = ?", String.class, second.run().runId()))
                .containsExactly("APP-3");
    }

    @Test
    void unknownLetterTypeIsRejected() {
        assertThatThrownBy(() -> service.generateLetters(new LetterRunRequest("LETTER_Z", CLAIM_DATE), "key-1", "admin"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(400);
                    assertThat(e.getCode()).isEqualTo("UNKNOWN_LETTER_TYPE");
                });
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
        assertThat(count("SELECT COUNT(*) FROM letter_run_audit")).isZero();
        assertThat(count("SELECT COUNT(*) FROM letter_request_queue")).isZero();
    }
}
