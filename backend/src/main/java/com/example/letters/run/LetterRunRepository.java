package com.example.letters.run;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface LetterRunRepository extends JpaRepository<LetterRun, Long> {

    @EntityGraph(attributePaths = "letterType")
    Optional<LetterRun> findByIdempotencyKey(String idempotencyKey);

    /** Newest first. The letter type is fetched in the same query, so a page needs no extra selects. */
    @EntityGraph(attributePaths = "letterType")
    Page<LetterRun> findAllByOrderByRequestedAtDescIdDesc(Pageable pageable);

    /**
     * Locks the run until the transaction ends, so release, revoke and progress updates on the
     * same run happen one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "letterType")
    Optional<LetterRun> findWithLockById(Long id);

    /** Runs whose progress is still tracked (GENERATING, NEEDS_ATTENTION), locked for updating. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<LetterRun> findWithLockByStatusInOrderByIdAsc(Collection<RunStatus> statuses);
}
