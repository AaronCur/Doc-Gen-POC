package com.example.letters.simulatedgenerator;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * NOT PART OF CASE-SERVICE. Behaves like the external letter-generator: each tick it reads up to
 * {@code batchSize} queue rows flagged N and, for each one, stores a document in case-documents
 * and sets the row's flag to Y. Each row is its own transaction.
 */
public class SimulatedLetterGenerator {

    private static final Logger log = LoggerFactory.getLogger(SimulatedLetterGenerator.class);

    private record QueueRow(String queueKey, String letterType) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final SimulatedGeneratorProperties properties;

    public SimulatedLetterGenerator(JdbcTemplate jdbc, PlatformTransactionManager transactions, Clock clock,
            SimulatedGeneratorProperties properties) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
        this.properties = properties;
    }

    /** Runs one tick and returns the number of letters generated. */
    @Scheduled(fixedDelayString = "${simulated-generator.interval:10s}")
    public int tick() {
        // FETCH FIRST is SQL:2008. Fine for the simulator, which only ever runs on H2.
        List<QueueRow> pending = jdbc.query("""
                SELECT queue_key, letter_type FROM letter_request_queue
                WHERE generated_flag = 'N'
                ORDER BY queue_key
                FETCH FIRST ? ROWS ONLY
                """,
                (rs, i) -> new QueueRow(rs.getString("queue_key"), rs.getString("letter_type")),
                properties.batchSize());

        int generated = 0;
        for (QueueRow row : pending) {
            String appId = appIdFrom(row);
            if (properties.failingAppIds().contains(appId)) {
                // Like the real generator: the row stays N and is retried on every tick.
                log.info("Simulated generator: letter for {} failed (configured to always fail)", row.queueKey());
                continue;
            }
            transaction.executeWithoutResult(status -> generate(row, appId));
            generated++;
        }
        if (generated > 0) {
            log.info("Simulated generator: generated {} letter(s)", generated);
        }
        return generated;
    }

    private void generate(QueueRow row, String appId) {
        jdbc.update(
                "INSERT INTO case_documents (doc_id, app_id, document_type, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), appId, row.letterType(), Timestamp.from(clock.instant()));
        jdbc.update(
                "UPDATE letter_request_queue SET generated_flag = 'Y' WHERE queue_key = ? AND generated_flag = 'N'",
                row.queueKey());
    }

    /**
     * The queue key is {@code appid_lettertype}. Letter type codes themselves contain underscores
     * (LETTER_A), so the app ID is what remains after removing the known {@code _lettertype}
     * suffix, rather than the text before the first underscore.
     */
    private static String appIdFrom(QueueRow row) {
        String suffix = "_" + row.letterType();
        if (!row.queueKey().endsWith(suffix)) {
            throw new IllegalStateException("Unexpected queue key " + row.queueKey() + " for " + row.letterType());
        }
        return row.queueKey().substring(0, row.queueKey().length() - suffix.length());
    }
}
