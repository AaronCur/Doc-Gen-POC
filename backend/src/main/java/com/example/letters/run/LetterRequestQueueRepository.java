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

    /**
     * Queues one request per run item. The run items are the source of truth for membership; the
     * queue key ({@code appid_lettertype}) is built here rather than stored on the item. Nested
     * two-argument CONCAT is the portable form of {@code CONCAT(i.app_id, '_', i.letter_type)}
     * (Oracle's CONCAT takes only two arguments).
     */
    public int insertPendingForRun(long runId) {
        return jdbc.update("""
                INSERT INTO letter_request_queue (queue_key, letter_type, generated_flag)
                SELECT CONCAT(CONCAT(i.app_id, '_'), i.letter_type), i.letter_type, 'N'
                FROM   letter_run_item i
                WHERE  i.run_id = :runId
                """, Map.of("runId", runId));
    }
}
