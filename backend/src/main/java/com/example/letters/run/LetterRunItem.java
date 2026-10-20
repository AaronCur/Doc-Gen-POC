package com.example.letters.run;

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
 * One application in a run. Rows are written once, at Generate, by a set-based insert
 * ({@link LetterRunItemRepository#insertForEligibleApplications}). The item links to the queue
 * (key {@code appid_lettertype}) and to case-documents (app_id + letter_type) logically, not by
 * foreign key. letter_type is copied from the run so the visibility check needs no join.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", insertable = false, updatable = false)
    private LetterRun run;
}
