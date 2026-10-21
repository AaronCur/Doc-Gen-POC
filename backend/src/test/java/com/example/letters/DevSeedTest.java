package com.example.letters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
    void seedsTwoPlanTypesAndThreePlans() {
        assertThat(count("SELECT COUNT(*) FROM plan_type")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM plan WHERE plan_type_id = 'PT-1'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM plan WHERE plan_type_id = 'PT-2'")).isEqualTo(1);
    }

    @Test
    void seedsSixtyApplicationsSpreadOverThePlans() {
        assertThat(count("SELECT COUNT(*) FROM case_applications")).isEqualTo(60);
        assertThat(count("SELECT COUNT(*) FROM case_applications WHERE plan_id = 'PLAN-1A'")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM case_applications WHERE plan_id = 'PLAN-1B'")).isEqualTo(20);
        assertThat(count("SELECT COUNT(*) FROM case_applications WHERE plan_id = 'PLAN-2A'")).isEqualTo(20);
    }

    @Test
    void eligibilityIsSpreadAcrossPlansSoSomeHaveNone() {
        // letter type -> eligible count in PLAN-1A, PLAN-1B, PLAN-2A
        Map<String, List<Integer>> expected = Map.of(
                "LETTER_A", List.of(18, 0, 0),
                "LETTER_B", List.of(10, 15, 0),
                "LETTER_C", List.of(0, 10, 20),
                "LETTER_D", List.of(6, 7, 7),
                "LETTER_E", List.of(0, 0, 5),
                "LETTER_F", List.of(0, 0, 0),
                "LETTER_G", List.of(0, 0, 0),
                "LETTER_H", List.of(0, 0, 11),
                "LETTER_I", List.of(0, 0, 0));
        List<String> plans = List.of("PLAN-1A", "PLAN-1B", "PLAN-2A");

        expected.forEach((type, counts) -> {
            for (int i = 0; i < plans.size(); i++) {
                assertThat(eligibility.countEligible(type, plans.get(i)))
                        .as(type + " in " + plans.get(i))
                        .isEqualTo(counts.get(i).longValue());
            }
        });
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
