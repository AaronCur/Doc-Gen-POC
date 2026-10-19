package com.example.letters.run;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One application in a run. Rows are written once, at generate time, by a set-based insert
 * ({@link LetterRunItemRepository#insertForEligibleApplications}); the shared queue is populated
 * from them. Progress and visibility link back through (app_id, letter_type), because the queue
 * has no row ID.
 */
@Entity
@Table(name = "letter_run_item")
@IdClass(LetterRunItemId.class)
@Getter
@Setter
@NoArgsConstructor
public class LetterRunItem {

    @Id
    @Column(name = "run_id")
    private Long runId;

    @Id
    @Column(name = "app_id")
    private String appId;

    @Column(name = "letter_type", nullable = false)
    private String letterType;

    @Column(name = "queue_key", nullable = false)
    private String queueKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", insertable = false, updatable = false)
    private LetterRun run;
}
