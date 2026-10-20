package com.example.letters.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.letters.run.LetterRunCompletionJob;
import com.example.letters.run.LetterRunRequest;
import com.example.letters.run.LetterRunService;

/**
 * Base for tests against the full application context and the H2 schema built by Flyway.
 *
 * <p>Tests commit for real rather than rolling back, because later tests (concurrency, scheduled
 * jobs) need data visible across transactions. Data is cleared and the clock reset before each
 * test instead.
 */
@SpringBootTest(properties = "letters.scheduling.enabled=false")
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    public static final Instant START = Instant.parse("2026-03-02T09:00:00Z");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    protected TestData data;

    @BeforeEach
    void resetData() {
        clock.set(START);
        data = new TestData(jdbc);
        data.reset();
    }

    protected int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Autowired
    private LetterRunService runService;

    @Autowired
    private LetterRunCompletionJob completionJob;

    /** Generates a run for every currently eligible application of the type; returns its ID. */
    protected long generateRun(String letterType) {
        return runService.generateLetters(new LetterRunRequest(letterType, LocalDate.of(2026, 2, 1)), "admin")
                .runId();
    }

    /**
     * Does what the letter-generator would: a document for each of the run's items that has none,
     * flag Y on the queue, then the completion job (which sets completed_at).
     */
    protected void produceLetters(long runId) {
        for (Map<String, Object> item : jdbc.queryForList(
                "SELECT app_id, letter_type FROM letter_run_item WHERE run_id = ?", runId)) {
            String appId = (String) item.get("APP_ID");
            String letterType = (String) item.get("LETTER_TYPE");
            data.document(documentId(runId, appId), appId, letterType);
            jdbc.update("UPDATE letter_request_queue SET generated_flag = 'Y' WHERE queue_key = ?",
                    appId + "_" + letterType);
        }
        completionJob.check();
    }

    /** The doc ID {@link #produceLetters} gives the letter for an application in a run. */
    protected static String documentId(long runId, String appId) {
        return "DOC-" + appId + "-RUN" + runId;
    }
}
