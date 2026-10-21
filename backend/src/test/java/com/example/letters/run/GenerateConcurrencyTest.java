package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.support.IntegrationTest;
import com.example.letters.common.exception.BusinessRuleException;

/** Two admins pressing Generate for the same type at the same moment. */
class GenerateConcurrencyTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";
    private static final LocalDate CLAIM_DATE = LocalDate.of(2026, 2, 1);

    @Autowired
    LetterRunService service;

    @RepeatedTest(5)
    void twoGenerateRequestsAtOnceProduceExactlyOneRunAndNoDuplicateQueueRows() throws Exception {
        for (int i = 1; i <= 20; i++) {
            data.eligibleApplication("APP-" + i, TYPE);
        }

        List<Object> outcomes = runConcurrently(
                () -> service.generateLetters(new LetterRunRequest(PLAN, TYPE, CLAIM_DATE, "Test run"), "admin"),
                () -> service.generateLetters(new LetterRunRequest(PLAN, TYPE, CLAIM_DATE, "Test run"), "admin"));

        assertThat(outcomes).filteredOn(o -> o instanceof LetterRunResponse).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof BusinessRuleException e && e.getCode().equals("RUN_IN_PROGRESS"))
                .hasSize(1);

        assertThat(count("SELECT COUNT(*) FROM letter_run")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM letter_request_queue WHERE generated_flag = 'N'")).isEqualTo(20);
        assertThat(count("""
                SELECT COUNT(*) FROM (
                  SELECT queue_key FROM letter_request_queue
                  WHERE generated_flag = 'N'
                  GROUP BY queue_key HAVING COUNT(*) > 1
                ) duplicates
                """)).isZero();
    }

    @RepeatedTest(5)
    void theSameTypeInTwoPlansAtOnceProducesOneRunPerPlan() throws Exception {
        // The type lock is shared across plans, so these run one after the other, but neither blocks.
        data.eligibleApplicationInPlan("PLAN-A", "APP-1", TYPE);
        data.eligibleApplicationInPlan("PLAN-B", "APP-2", TYPE);

        List<Object> outcomes = runConcurrently(
                () -> service.generateLetters(new LetterRunRequest("PLAN-A", TYPE, CLAIM_DATE, "Test run"), "admin"),
                () -> service.generateLetters(new LetterRunRequest("PLAN-B", TYPE, CLAIM_DATE, "Test run"), "admin"));

        assertThat(outcomes).allMatch(o -> o instanceof LetterRunResponse);
        assertThat(count("SELECT COUNT(*) FROM letter_run")).isEqualTo(2);
        assertThat(count("SELECT COUNT(DISTINCT plan_id) FROM letter_run")).isEqualTo(2);
    }

    /** Starts both tasks together and returns each one's result or thrown exception. */
    private List<Object> runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    startTogether.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
