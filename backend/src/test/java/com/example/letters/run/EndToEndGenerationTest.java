package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.letters.simulatedgenerator.SimulatedGeneratorProperties;
import com.example.letters.simulatedgenerator.SimulatedLetterGenerator;
import com.example.letters.support.IntegrationTest;

/** Generate, let the simulated generator work through the queue, and watch the run complete. */
class EndToEndGenerationTest extends IntegrationTest {

    @Autowired
    LetterRunService service;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void runCompletesOnceTheGeneratorHasProcessedEveryItem() {
        for (int i = 1; i <= 12; i++) {
            data.eligibleApplication("APP-" + i, "LETTER_A");
        }
        var generator = new SimulatedLetterGenerator(jdbc, transactions, clock,
                new SimulatedGeneratorProperties(Duration.ofSeconds(10), 5, List.of()));
        long runId = service.generateLetters(new LetterRunRequest("LETTER_A", LocalDate.of(2026, 2, 1)), "admin")
                .runId();
        assertThat(listedRun(runId).status()).isEqualTo(RunStatus.QUEUED);

        generator.tick();
        assertThat(listedRun(runId).generatedCount()).isEqualTo(5);
        assertThat(listedRun(runId).status()).isEqualTo(RunStatus.IN_PROGRESS);

        generator.tick();
        generator.tick();
        LetterRunResponse run = listedRun(runId);
        assertThat(run.generatedCount()).isEqualTo(12);
        assertThat(run.status()).isEqualTo(RunStatus.COMPLETE);
        assertThat(run.visibility()).isEqualTo(RunVisibility.STAFF_ONLY);
    }

    private LetterRunResponse listedRun(long runId) {
        return service.getLetterRuns(0, 20).items().stream().filter(r -> r.runId() == runId).findFirst().orElseThrow();
    }
}
