package com.example.letters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.example.letters.eligibility.ApplicationLetterEligibilityRepository;

/** Checks the dev seed supports the demo walkthrough described in the README. */
@SpringBootTest(properties = "letters.scheduling.enabled=false")
@ActiveProfiles("dev")
class DevSeedTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationLetterEligibilityRepository eligibility;

    @Test
    void seedsLetterTypesWithALockRowEach() {
        assertThat(count("SELECT COUNT(*) FROM letter_types")).isEqualTo(9);
        assertThat(count("""
                SELECT COUNT(*) FROM letter_types t
                JOIN letter_type_lock l ON l.letter_type = t.letter_type
                """)).isEqualTo(9);
    }

    @Test
    void seedsSixtyApplications() {
        assertThat(count("SELECT COUNT(*) FROM case_applications")).isEqualTo(60);
    }

    @Test
    void eligibilityIsSpreadSoSomeTypesHaveNone() {
        Map<String, Integer> expected = Map.of(
                "LETTER_A", 18, "LETTER_B", 25, "LETTER_C", 30,
                "LETTER_D", 20, "LETTER_E", 5, "LETTER_F", 0,
                "LETTER_G", 0, "LETTER_H", 11, "LETTER_I", 0);

        expected.forEach((type, eligibleCount) ->
                assertThat(eligibility.countEligible(type)).as(type).isEqualTo(eligibleCount.longValue()));
    }

    @Test
    void existingLettersHaveNoRunItem() {
        assertThat(count("SELECT COUNT(*) FROM case_documents")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM letter_run_item")).isZero();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
