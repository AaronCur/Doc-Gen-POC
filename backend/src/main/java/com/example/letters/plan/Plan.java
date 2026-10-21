package com.example.letters.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** STAND-IN for the existing admin screen's plans. Each application belongs to exactly one plan. */
@Entity
@Table(name = "plan")
@Getter
@Setter
@NoArgsConstructor
public class Plan {

    @Id
    @Column(name = "plan_id")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_type_id", nullable = false)
    private PlanType planType;

    @Column(nullable = false)
    private String name;

    public Plan(String id, PlanType planType, String name) {
        this.id = id;
        this.planType = planType;
        this.name = name;
    }
}
