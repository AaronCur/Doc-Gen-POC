package com.example.letters.run;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.letters.eligibility.ApplicationLetterEligibilityRepository;

@Repository
public interface LetterRunItemRepository extends JpaRepository<LetterRunItem, LetterRunItemId> {

    /**
     * Records the run's membership in one statement: one item per application in the plan that is
     * eligible right now, using the same SQL as the eligible count. Returns the number of items inserted.
     * Parameters in the SELECT list are CAST so every database can infer their types.
     */
    @Modifying
    @Query(value = """
            INSERT INTO letter_run_item (run_id, app_id, letter_type)
            SELECT CAST(:runId AS BIGINT), eligible.app_id, CAST(:letterType AS VARCHAR(40))
            FROM (
            """ + ApplicationLetterEligibilityRepository.ELIGIBLE_APP_IDS + """
            ) eligible
            """, nativeQuery = true)
    int insertForEligibleApplications(@Param("runId") Long runId, @Param("letterType") String letterType,
            @Param("planId") String planId);

    /**
     * Items that have a document of their type. Unambiguous, because the eligibility rule never
     * puts an application in a run while it still has a document of that type.
     */
    @Query("""
            select count(i) from LetterRunItem i
            where i.runId = :runId
            and exists (select 1 from CaseDocument d where d.appId = i.appId and d.documentType = i.letterType)
            """)
    long countGeneratedByRunId(@Param("runId") Long runId);

    /** App IDs in the run that have no document of their type yet. */
    @Query("""
            select i.appId from LetterRunItem i
            where i.runId = :runId
            and not exists (select 1 from CaseDocument d where d.appId = i.appId and d.documentType = i.letterType)
            order by i.appId
            """)
    List<String> findPendingAppIdsByRunId(@Param("runId") Long runId);
}
