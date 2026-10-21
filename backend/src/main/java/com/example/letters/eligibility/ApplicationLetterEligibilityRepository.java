package com.example.letters.eligibility;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The single place that decides which applications a run of a letter type picks up.
 *
 * <p>An application is included if it is in the given plan, is eligible for the type, has no
 * document of the type in case-documents, and has no queue row for the type still flagged N. So a
 * repeat run only picks up applications without a letter, and deleting a letter makes the
 * application eligible again.
 *
 * <p>Native SQL because the rule reads the shared queue table, which has no entity (no primary
 * key). The eligibility table is a stand-in for the real rule: when porting, replace that part of
 * {@link #ELIGIBLE_APP_IDS} and keep the two NOT EXISTS checks.
 */
@Repository
public interface ApplicationLetterEligibilityRepository
        extends JpaRepository<ApplicationLetterEligibility, ApplicationLetterEligibilityId> {

    /**
     * App IDs in plan {@code :planId} eligible for a run of {@code :letterType}. Also used by the
     * generate flow's {@code INSERT ... SELECT}, so the count shown to the admin and the rows
     * inserted always use the same rule. Nested two-argument CONCAT builds the {@code appid_lettertype} key portably.
     */
    String ELIGIBLE_APP_IDS = """
            SELECT e.app_id
            FROM   application_letter_eligibility e
            JOIN   case_applications a ON a.app_id = e.app_id AND a.plan_id = :planId
            WHERE  e.letter_type = :letterType
            AND NOT EXISTS (SELECT 1 FROM case_documents d
                            WHERE d.app_id = e.app_id AND d.document_type = :letterType)
            AND NOT EXISTS (SELECT 1 FROM letter_request_queue q
                            WHERE q.queue_key = CONCAT(CONCAT(e.app_id, '_'), :letterType)
                              AND q.generated_flag = 'N')
            """;

    @Query(value = ELIGIBLE_APP_IDS + " ORDER BY e.app_id", nativeQuery = true)
    List<String> findEligibleAppIds(@Param("letterType") String letterType, @Param("planId") String planId);

    @Query(value = "SELECT COUNT(*) FROM (" + ELIGIBLE_APP_IDS + ") eligible", nativeQuery = true)
    long countEligible(@Param("letterType") String letterType, @Param("planId") String planId);
}
