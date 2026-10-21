package com.example.letters.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** STAND-IN for the existing admin screen's plan types. The POC only needs an id and a name. */
@Entity
@Table(name = "plan_type")
@Getter
@Setter
@NoArgsConstructor
public class PlanType {

    @Id
    @Column(name = "plan_type_id")
    private String id;

    @Column(nullable = false)
    private String name;

    public PlanType(String id, String name) {
        this.id = id;
        this.name = name;
    }
}
