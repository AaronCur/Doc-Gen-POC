package com.example.letters.run;

import java.time.Instant;
import java.time.LocalDate;

import com.example.letters.lettertype.LetterType;

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

/** One admin request to generate letters of a type, and where it has got to. */
@Entity
@Table(name = "letter_run")
@Getter
@Setter
@NoArgsConstructor
public class LetterRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "letter_type", nullable = false)
    private LetterType letterType;

    @Column(name = "claim_submitted_date", nullable = false)
    private LocalDate claimSubmittedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunVisibility visibility;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "last_progress_at", nullable = false)
    private Instant lastProgressAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "released_by")
    private String releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** A new run: GENERATING, visible to staff only, with nothing counted yet. */
    public LetterRun(LetterType letterType, LocalDate claimSubmittedDate, String requestedBy, String idempotencyKey,
            Instant now) {
        this.letterType = letterType;
        this.claimSubmittedDate = claimSubmittedDate;
        this.status = RunStatus.GENERATING;
        this.visibility = RunVisibility.STAFF_ONLY;
        this.totalCount = 0;
        this.generatedCount = 0;
        this.lastProgressAt = now;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
        this.idempotencyKey = idempotencyKey;
    }

    /** Still being worked on by the letter-generator (or stalled). */
    public boolean isActive() {
        return status == RunStatus.GENERATING || status == RunStatus.NEEDS_ATTENTION;
    }
}
