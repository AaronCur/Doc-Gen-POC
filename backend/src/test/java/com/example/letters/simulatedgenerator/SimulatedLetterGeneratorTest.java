package com.example.letters.simulatedgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.letters.support.IntegrationTest;

/**
 * The simulator is dev-profile only; here it is built directly against the test database, so
 * the test doesn't need the dev profile or its seed data.
 */
class SimulatedLetterGeneratorTest extends IntegrationTest {

    @Autowired
    PlatformTransactionManager transactions;

    SimulatedLetterGenerator generator;

    @BeforeEach
    void createGenerator() {
        generator = new SimulatedLetterGenerator(jdbc, transactions, clock,
                new SimulatedGeneratorProperties(Duration.ofSeconds(10), 5, List.of("APP-FAIL")));
    }

    @Test
    void createsADocumentAndFlagsTheRowY() {
        data.queueRow("APP-1", "LETTER_A", 'N');

        assertThat(generator.tick()).isEqualTo(1);

        var doc = jdbc.queryForMap("SELECT * FROM case_documents");
        assertThat(doc.get("APP_ID")).isEqualTo("APP-1");
        assertThat(doc.get("DOCUMENT_TYPE")).isEqualTo("LETTER_A");
        assertThat(((Timestamp) doc.get("CREATED_AT")).toInstant()).isEqualTo(START);
        assertThat(jdbc.queryForObject("SELECT generated_flag FROM letter_request_queue", String.class))
                .isEqualTo("Y");
    }

    @Test
    void processesAtMostBatchSizeRowsPerTick() {
        for (int i = 1; i <= 7; i++) {
            data.queueRow("APP-" + i, "LETTER_A", 'N');
        }

        assertThat(generator.tick()).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM letter_request_queue WHERE generated_flag = 'N'")).isEqualTo(2);

        assertThat(generator.tick()).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM case_documents")).isEqualTo(7);
    }

    @Test
    void configuredFailingApplicationStaysPendingEveryTick() {
        data.queueRow("APP-1", "LETTER_A", 'N');
        data.queueRow("APP-FAIL", "LETTER_A", 'N');

        generator.tick();
        generator.tick();

        assertThat(jdbc.queryForList(
                "SELECT queue_key FROM letter_request_queue WHERE generated_flag = 'N'", String.class))
                .containsExactly("APP-FAIL_LETTER_A");
        assertThat(jdbc.queryForList("SELECT app_id FROM case_documents", String.class)).containsExactly("APP-1");
    }

    @Test
    void ignoresRowsAlreadyFlaggedY() {
        data.queueRow("APP-1", "LETTER_A", 'Y');

        assertThat(generator.tick()).isZero();
        assertThat(count("SELECT COUNT(*) FROM case_documents")).isZero();
    }

    @Test
    void parsesAppIdUsingTheLetterTypeSuffixNotTheFirstUnderscore() {
        data.queueRow("APP_WITH_UNDERSCORES", "LETTER_B", 'N');

        generator.tick();

        assertThat(jdbc.queryForObject("SELECT app_id FROM case_documents", String.class))
                .isEqualTo("APP_WITH_UNDERSCORES");
    }
}
