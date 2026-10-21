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

/**
 * One admin Generate click. Only facts that can't be calculated are stored: the generated count
 * and the status are derived on read (see {@link LetterRunStatusService}).
 */
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

    /** The plan the run belongs to. Plan type is derived from the plan, so it isn't stored. */
    @Column(name = "plan_id", nullable = false)
    private String planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "letter_type", nullable = false)
    private LetterType letterType;

    /** Deadline for claim submission, as entered by the admin. */
    @Column(name = "claim_deadline_date", nullable = false)
    private LocalDate claimDeadlineDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunVisibility visibility;

    /** Number of run items, set once at Generate. */
    @Column(name = "total_requested", nullable = false)
    private int totalRequested;

    @Column(name = "requested_by", nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    /** Set the first time every item has a document; a completed run always shows total / total. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "released_by")
    private String releasedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    /** A new run, visible to staff only. The total is set once the items are recorded. */
    public LetterRun(String planId, LetterType letterType, LocalDate claimDeadlineDate, String requestedBy,
            Instant now) {
        this.planId = planId;
        this.letterType = letterType;
        this.claimDeadlineDate = claimDeadlineDate;
        this.visibility = RunVisibility.STAFF_ONLY;
        this.totalRequested = 0;
        this.requestedBy = requestedBy;
        this.requestedAt = now;
    }
}
