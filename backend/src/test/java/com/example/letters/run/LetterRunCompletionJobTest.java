package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.letters.common.exception.BusinessRuleException;
import com.example.letters.config.LettersProperties;
import com.example.letters.support.IntegrationTest;

/** Uses application.yaml: needs-attention-after = 4h. */
@ExtendWith(OutputCaptureExtension.class)
class LetterRunCompletionJobTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";
    private static final Duration NEEDS_ATTENTION_AFTER = Duration.ofHours(4);

    @Autowired
    LetterRunCompletionJob job;

    @Autowired
    LetterRunService service;

    @Autowired
    LettersProperties properties;

    @Test
    void readsTheThresholdFromConfiguration() {
        assertThat(properties.needsAttentionAfter()).isEqualTo(NEEDS_ATTENTION_AFTER);
        assertThat(properties.checkInterval()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void setsCompletedAtWithoutAnyoneReadingTheRun() {
        long runId = runFor("APP-1");
        clock.advance(Duration.ofMinutes(2));
        data.document("DOC-1", "APP-1", TYPE);

        job.check();

        assertThat(count("SELECT COUNT(*) FROM letter_run WHERE run_id = ? AND completed_at IS NOT NULL", runId))
                .isEqualTo(1);
    }

    @Test
    void logsAWarningForARunThatNeedsAttention(CapturedOutput output) {
        long runId = runFor("APP-1", "APP-2");
        data.document("DOC-1", "APP-1", TYPE);
        clock.advance(NEEDS_ATTENTION_AFTER.plusSeconds(1));

        job.check();

        assertThat(output.getOut()).containsPattern(
                "WARN .*ALERT letter run " + runId + " needs attention: LETTER_A has generated 1 of 2 letters");
    }

    @Test
    void doesNotWarnAboutARecentRun(CapturedOutput output) {
        runFor("APP-1");
        clock.advance(NEEDS_ATTENTION_AFTER);

        job.check();

        assertThat(output.getOut()).doesNotContain("ALERT letter run");
    }

    @Test
    void doesNotWarnAboutACompletedRunHoweverOld(CapturedOutput output) {
        long runId = runFor("APP-1");
        produceLetters(runId);
        clock.advance(Duration.ofDays(30));

        job.check();

        assertThat(output.getOut()).doesNotContain("ALERT letter run " + runId);
    }

    @Test
    void aRunThatNeedsAttentionStillBlocksANewRunForTheType() {
        runFor("APP-1");
        clock.advance(NEEDS_ATTENTION_AFTER.plusSeconds(1));
        data.eligibleApplication("APP-2", TYPE);

        assertThatThrownBy(() -> generateRun(TYPE))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS"));
    }

    private long runFor(String... appIds) {
        for (String appId : appIds) {
            data.eligibleApplication(appId, TYPE);
        }
        return generateRun(TYPE);
    }
}
