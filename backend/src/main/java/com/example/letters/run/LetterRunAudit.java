package com.example.letters.run;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Who generated, released or revoked a run, and when. Rows are only ever added. */
@Entity
@Table(name = "letter_run_audit")
@Getter
@Setter
@NoArgsConstructor
public class LetterRunAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private LetterRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private Instant at;

    public LetterRunAudit(LetterRun run, AuditAction action, String actor, Instant at) {
        this.run = run;
        this.action = action;
        this.actor = actor;
        this.at = at;
    }
}
