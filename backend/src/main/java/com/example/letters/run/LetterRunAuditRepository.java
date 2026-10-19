package com.example.letters.run;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LetterRunAuditRepository extends JpaRepository<LetterRunAudit, Long> {
}
