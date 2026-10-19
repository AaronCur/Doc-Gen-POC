package com.example.letters.eligibility;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link ApplicationLetterEligibility}: (app_id, letter_type). */
public class ApplicationLetterEligibilityId implements Serializable {

    private String appId;
    private String letterType;

    public ApplicationLetterEligibilityId() {
    }

    public ApplicationLetterEligibilityId(String appId, String letterType) {
        this.appId = appId;
        this.letterType = letterType;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApplicationLetterEligibilityId id
                && Objects.equals(appId, id.appId) && Objects.equals(letterType, id.letterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, letterType);
    }
}
