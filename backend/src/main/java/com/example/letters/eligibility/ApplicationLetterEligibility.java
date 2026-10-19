package com.example.letters.eligibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stand-in for "is this application eligible for letter type X". The real rule lives elsewhere;
 * it is isolated behind {@link ApplicationLetterEligibilityRepository}.
 */
@Entity
@Table(name = "application_letter_eligibility")
@IdClass(ApplicationLetterEligibilityId.class)
@Getter
@Setter
@NoArgsConstructor
public class ApplicationLetterEligibility {

    @Id
    @Column(name = "app_id")
    private String appId;

    @Id
    @Column(name = "letter_type")
    private String letterType;
}
