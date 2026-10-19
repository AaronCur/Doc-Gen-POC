package com.example.letters.lettertype;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface LetterTypeLockRepository extends JpaRepository<LetterTypeLock, String> {

    /**
     * Locks the type's row until the transaction ends. Hibernate renders the right syntax for the
     * database: {@code SELECT ... FOR UPDATE} on H2, PostgreSQL and Oracle, {@code WITH (UPDLOCK)}
     * on SQL Server. Empty if the letter type does not exist.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LetterTypeLock> findWithLockByLetterType(String letterType);
}
