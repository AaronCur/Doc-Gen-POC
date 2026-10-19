package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.example.letters.config.LettersProperties;
import com.example.letters.support.IntegrationTest;
import com.example.letters.common.exception.BusinessRuleException;

/** Uses application.properties: 3 cycles x 10s generator interval = 30s threshold. */
@ExtendWith(OutputCaptureExtension.class)
class StuckDetectionTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";
    private static final Duration THRESHOLD = Duration.ofSeconds(30);

    @Autowired
    StuckRunDetector detector;

    @Autowired
    LetterRunRepository runs;

    @Autowired
    LetterRunService service;

    @Autowired
    LettersProperties properties;

    @Test
    void thresholdIsStuckCyclesTimesGeneratorInterval() {
        assertThat(properties.stuckCycles()).isEqualTo(3);
        assertThat(properties.generatorInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.stuckThreshold()).isEqualTo(THRESHOLD);
    }

    @Test
    void noProgressPastTheThresholdGivesNeedsAttentionAndAWarning(CapturedOutput output) {
        long runId = twoAppRun();

        clock.advance(THRESHOLD.plusSeconds(1));
        detector.check();

        assertThat(status(runId)).isEqualTo(RunStatus.NEEDS_ATTENTION);
        assertThat(output.getOut())
                .containsPattern("WARN .*ALERT letter run " + runId + " needs attention");
    }

    @Test
    void noProgressWithinTheThresholdStaysGenerating() {
        long runId = twoAppRun();

        clock.advance(THRESHOLD);
        detector.check();

        assertThat(status(runId)).isEqualTo(RunStatus.GENERATING);
    }

    @Test
    void recentProgressResetsTheThreshold() {
        long runId = twoAppRun();
        clock.advance(Duration.ofSeconds(20));
        data.document("DOC-1", "APP-1", TYPE);
        detector.check(); // records progress at +20s

        clock.advance(Duration.ofSeconds(20)); // +40s overall, but only 20s since progress
        detector.check();

        assertThat(status(runId)).isEqualTo(RunStatus.GENERATING);
    }

    @Test
    void progressResumingGivesGeneratingAgain() {
        long runId = twoAppRun();
        clock.advance(THRESHOLD.plusSeconds(1));
        detector.check();
        assertThat(status(runId)).isEqualTo(RunStatus.NEEDS_ATTENTION);

        clock.advance(Duration.ofSeconds(5));
        data.document("DOC-1", "APP-1", TYPE);
        detector.check();

        LetterRun run = runs.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(RunStatus.GENERATING);
        assertThat(run.getGeneratedCount()).isEqualTo(1);
        assertThat(run.getLastProgressAt()).isEqualTo(clock.instant());
    }

    @Test
    void theAlertIsRaisedOncePerStall(CapturedOutput output) {
        long runId = twoAppRun();

        clock.advance(THRESHOLD.plusSeconds(1));
        detector.check();
        clock.advance(Duration.ofMinutes(1));
        detector.check();

        assertThat(output.getOut().split("ALERT letter run " + runId + " ", -1)).hasSize(2);
    }

    @Test
    void lettersThatAppearedSinceTheLastRefreshCompleteTheRunInsteadOfFlaggingIt() {
        long runId = twoAppRun();
        clock.advance(THRESHOLD.plusMinutes(5));
        data.document("DOC-1", "APP-1", TYPE);
        data.document("DOC-2", "APP-2", TYPE);

        detector.check();

        assertThat(status(runId)).isEqualTo(RunStatus.COMPLETE);
    }

    @Test
    void aCompleteRunIsNeverFlagged() {
        long runId = twoAppRun();
        produceLetters(runId);

        clock.advance(Duration.ofDays(1));
        detector.check();

        assertThat(status(runId)).isEqualTo(RunStatus.COMPLETE);
    }

    @Test
    void aStuckRunStillBlocksANewRunForTheType() {
        twoAppRun();
        clock.advance(THRESHOLD.plusSeconds(1));
        detector.check();
        data.eligibleApplication("APP-3", TYPE);

        assertThatThrownBy(() -> generateRun(TYPE))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("RUN_IN_PROGRESS"));
    }

    @Test
    void aStuckRunCannotBeReleased() {
        long runId = twoAppRun();
        data.document("DOC-1", "APP-1", TYPE);
        clock.advance(THRESHOLD.plusSeconds(1));
        detector.check(); // progress recorded at this check, so advance again
        clock.advance(THRESHOLD.plusSeconds(1));
        detector.check();
        assertThat(status(runId)).isEqualTo(RunStatus.NEEDS_ATTENTION);

        assertThatThrownBy(() -> service.releaseLetterRun(runId, "staff"))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("NOT_COMPLETE"));
    }

    private long twoAppRun() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        return generateRun(TYPE);
    }

    private RunStatus status(long runId) {
        return runs.findById(runId).orElseThrow().getStatus();
    }
}
