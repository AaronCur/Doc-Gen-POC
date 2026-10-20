package com.example.letters.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LetterRunRepository extends JpaRepository<LetterRun, Long> {

    /** Newest first. The letter type is fetched in the same query, so a page needs no extra selects. */
    @EntityGraph(attributePaths = "letterType")
    Page<LetterRun> findAllByOrderByRequestedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "letterType")
    Optional<LetterRun> findWithLetterTypeById(Long id);

    /** Runs that are not complete yet, for the scheduled completion check. */
    @EntityGraph(attributePaths = "letterType")
    List<LetterRun> findAllByCompletedAtIsNullOrderByIdAsc();

    /**
     * Sets completed_at the first time. Guarded, so concurrent callers (a list read and the
     * scheduled job) can't overwrite it. Returns the number of rows updated (0 or 1).
     */
    @Modifying
    @Query("update LetterRun r set r.completedAt = :now where r.id = :id and r.completedAt is null")
    int markCompleted(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Release in one guarded statement: only a completed run that is still staff-only changes.
     * Returns 0 when the run is already released, not complete, or doesn't exist.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update LetterRun r
            set r.visibility = com.example.letters.run.RunVisibility.STAFF_AND_EXTERNAL,
                r.releasedBy = :releasedBy, r.releasedAt = :now
            where r.id = :id
            and r.visibility = com.example.letters.run.RunVisibility.STAFF_ONLY
            and r.completedAt is not null
            """)
    int release(@Param("id") Long id, @Param("releasedBy") String releasedBy, @Param("now") Instant now);
}
