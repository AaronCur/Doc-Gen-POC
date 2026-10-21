package com.example.letters.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.letters.support.IntegrationTest;

class ApplicationLetterEligibilityRepositoryTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";
    private static final String OTHER_TYPE = "LETTER_B";

    @Autowired
    ApplicationLetterEligibilityRepository eligibility;

    @Test
    void includesEligibleApplicationsWithNoLetterAndNoPendingRequest() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);

        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-1", "APP-2");
    }

    @Test
    void includesOnlyApplicationsInTheGivenPlan() {
        data.eligibleApplicationInPlan("PLAN-A", "APP-1", TYPE);
        data.eligibleApplicationInPlan("PLAN-B", "APP-2", TYPE);
        data.eligibleApplicationInPlan("PLAN-A", "APP-3", TYPE);

        assertThat(eligibility.findEligibleAppIds(TYPE, "PLAN-A")).containsExactly("APP-1", "APP-3");
        assertThat(eligibility.findEligibleAppIds(TYPE, "PLAN-B")).containsExactly("APP-2");
        assertThat(eligibility.countEligible(TYPE, "PLAN-A")).isEqualTo(2);
        assertThat(eligibility.countEligible(TYPE, "PLAN-C")).isZero();
    }

    @Test
    void includesOnlyApplicationsEligibleForTheType() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", OTHER_TYPE);

        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-1");
    }

    @Test
    void excludesApplicationsThatHaveADocumentOfTheType() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        data.document("DOC-1", "APP-1", TYPE);

        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-2");
    }

    @Test
    void documentOfAnotherTypeDoesNotExclude() {
        data.eligibleApplication("APP-1", TYPE, OTHER_TYPE);
        data.document("DOC-1", "APP-1", OTHER_TYPE);

        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-1");
    }

    @Test
    void excludesApplicationsWithAPendingQueueRow() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        data.queueRow("APP-1", TYPE, 'N');

        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-2");
    }

    @Test
    void pendingQueueRowForAnotherTypeDoesNotExclude() {
        data.eligibleApplication("APP-1", TYPE, OTHER_TYPE);
        data.queueRow("APP-1", OTHER_TYPE, 'N');

        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-1");
    }

    @Test
    void includesApplicationAgainAfterItsDocumentIsDeleted() {
        // A letter was generated earlier: the queue row is processed and the document exists.
        data.eligibleApplication("APP-1", TYPE);
        data.queueRow("APP-1", TYPE, 'Y');
        data.document("DOC-1", "APP-1", TYPE);
        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).isEmpty();

        // Staff delete the letter; the next run picks the application up again.
        data.deleteDocument("DOC-1");

        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-1");
    }

    @Test
    void countUsesTheSameRuleAsFind() {
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        data.eligibleApplication("APP-3", TYPE);
        data.document("DOC-1", "APP-1", TYPE);
        data.queueRow("APP-2", TYPE, 'N');

        assertThat(eligibility.countEligible(TYPE, PLAN)).isEqualTo(1);
        assertThat(eligibility.findEligibleAppIds(TYPE, PLAN)).containsExactly("APP-3");
    }

    @Test
    void countIsZeroWhenNothingIsEligible() {
        assertThat(eligibility.countEligible(TYPE, PLAN)).isZero();
    }
}
