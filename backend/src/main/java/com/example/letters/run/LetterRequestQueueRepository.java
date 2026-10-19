package com.example.letters.run;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * case-service's only access to the shared letter_request_queue table. By agreement we only INSERT
 * rows and read generated_flag: no updates, no deletes, no new columns.
 *
 * <p>Plain JdbcTemplate rather than a JpaRepository: the table has no primary key (and its key
 * column is not unique), so it cannot be mapped as a JPA entity. It still joins the surrounding
 * JPA transaction, so generate stays one local transaction.
 */
@Repository
public class LetterRequestQueueRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public LetterRequestQueueRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Rows for the type that the letter-generator has not processed yet, from any source. */
    public int countPending(String letterType) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM letter_request_queue WHERE letter_type = :letterType AND generated_flag = 'N'",
                Map.of("letterType", letterType),
                Integer.class);
        return count == null ? 0 : count;
    }

    /** Queues one request per run item. The run items are the source of truth for membership. */
    public int insertPendingForRun(long runId) {
        return jdbc.update("""
                INSERT INTO letter_request_queue (queue_key, letter_type, generated_flag)
                SELECT queue_key, letter_type, 'N'
                FROM   letter_run_item
                WHERE  run_id = :runId
                """, Map.of("runId", runId));
    }
}
