package com.example.letters.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts and clears test rows directly, so each test states exactly the data it relies on. */
public class TestData {

    private static final Timestamp CREATED = Timestamp.valueOf(LocalDateTime.of(2026, 1, 15, 9, 0));

    /** The plan applications go into unless a test says otherwise. */
    public static final String PLAN = "PLAN-T1";

    private final JdbcTemplate jdbc;

    public TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Deletes everything except reference data (letter types and their lock rows). */
    public void reset() {
        jdbc.update("DELETE FROM letter_run_item");
        jdbc.update("DELETE FROM letter_run");
        jdbc.update("DELETE FROM letter_request_queue");
        jdbc.update("DELETE FROM case_documents");
        jdbc.update("DELETE FROM application_letter_eligibility");
        jdbc.update("DELETE FROM case_applications");
    }

    /** Creates the application in {@link #PLAN} and makes it eligible for each given letter type. */
    public void eligibleApplication(String appId, String... letterTypes) {
        eligibleApplicationInPlan(PLAN, appId, letterTypes);
    }

    /** Creates the application in the given plan and makes it eligible for each given letter type. */
    public void eligibleApplicationInPlan(String planId, String appId, String... letterTypes) {
        jdbc.update("INSERT INTO case_applications (app_id, status, plan_id) VALUES (?, 'SUBMITTED', ?)", appId,
                planId);
        for (String letterType : letterTypes) {
            jdbc.update(
                    "INSERT INTO application_letter_eligibility (app_id, letter_type) VALUES (?, ?)",
                    appId, letterType);
        }
    }

    public void document(String docId, String appId, String documentType) {
        jdbc.update(
                "INSERT INTO case_documents (doc_id, app_id, document_type, created_at) VALUES (?, ?, ?, ?)",
                docId, appId, documentType, CREATED);
    }

    public void deleteDocument(String docId) {
        jdbc.update("DELETE FROM case_documents WHERE doc_id = ?", docId);
    }

    public void queueRow(String appId, String letterType, char flag) {
        jdbc.update(
                "INSERT INTO letter_request_queue (queue_key, letter_type, generated_flag) VALUES (?, ?, ?)",
                appId + "_" + letterType, letterType, String.valueOf(flag));
    }
}
