package com.example.letters.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.letters.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * The staged reveal, checked through the real external endpoint. If any of these fail, an external
 * user can see a letter staff have not released, or has lost one they could see before.
 */
@AutoConfigureMockMvc
class DocumentVisibilityTest extends IntegrationTest {

    private static final String TYPE = "LETTER_A";

    @Autowired
    MockMvc mvc;

    @Test
    void existingLetterWithNoRunIsVisible() throws Exception {
        data.eligibleApplication("APP-1", TYPE);
        data.document("DOC-OLD", "APP-1", TYPE);

        assertThat(externalDocIds("APP-1")).containsExactly("DOC-OLD");
    }

    @Test
    void lettersOfAStaffOnlyRunAreHidden() throws Exception {
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);
        produceLetters(runId);

        assertThat(externalDocIds("APP-1")).isEmpty();
        assertThat(staffDocIds("APP-1")).containsExactly(documentId(runId, "APP-1"));
    }

    @Test
    void lettersOfAReleasedRunAreVisible() throws Exception {
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);
        produceLetters(runId);
        release(runId);

        assertThat(externalDocIds("APP-1")).containsExactly(documentId(runId, "APP-1"));
    }

    @Test
    void revokingHidesTheLettersAgain() throws Exception {
        data.eligibleApplication("APP-1", TYPE);
        long runId = generateRun(TYPE);
        produceLetters(runId);
        release(runId);

        mvc.perform(post("/api/letter-runs/{id}/revoke", runId).with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());

        assertThat(externalDocIds("APP-1")).isEmpty();
    }

    @Test
    void aRunForOneTypeDoesNotAffectLettersOfAnotherType() throws Exception {
        data.eligibleApplication("APP-1", TYPE);
        data.document("DOC-B", "APP-1", "LETTER_B"); // existing letter of another type
        long runId = generateRun(TYPE);
        produceLetters(runId);

        assertThat(externalDocIds("APP-1")).containsExactly("DOC-B");
    }

    @Test
    void regenerationGivesANewLetterHiddenUntilReleasedWhileOthersAreUnaffected() throws Exception {
        // Run 1 for two applications, released.
        data.eligibleApplication("APP-1", TYPE);
        data.eligibleApplication("APP-2", TYPE);
        long run1 = generateRun(TYPE);
        produceLetters(run1);
        release(run1);
        assertThat(externalDocIds("APP-1")).containsExactly(documentId(run1, "APP-1"));

        // Staff delete APP-1's letter; the next run picks up APP-1 only.
        mvc.perform(delete("/api/staff/documents/{docId}", documentId(run1, "APP-1")).with(httpBasic("staff", "staff")))
                .andExpect(status().isNoContent());
        long run2 = generateRun(TYPE);
        assertThat(jdbc.queryForList("SELECT app_id FROM letter_run_item WHERE run_id = ?", String.class, run2))
                .containsExactly("APP-1");
        produceLetters(run2);

        // The regenerated letter is hidden until run 2 is released; APP-2's letter is unaffected.
        assertThat(externalDocIds("APP-1")).isEmpty();
        assertThat(externalDocIds("APP-2")).containsExactly(documentId(run1, "APP-2"));

        release(run2);
        assertThat(externalDocIds("APP-1")).containsExactly(documentId(run2, "APP-1"));
        assertThat(externalDocIds("APP-2")).containsExactly(documentId(run1, "APP-2"));
    }

    @Test
    void staffListFlagsWhatExternalUsersCanSee() throws Exception {
        data.eligibleApplication("APP-1", TYPE);
        data.document("DOC-B", "APP-1", "LETTER_B");
        long runId = generateRun(TYPE);
        produceLetters(runId);

        mvc.perform(get("/api/staff/applications/APP-1/documents").with(httpBasic("staff", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.docId == 'DOC-B')].visibleToExternal").value(true))
                .andExpect(jsonPath("$[?(@.documentType == 'LETTER_A')].visibleToExternal").value(false));
    }

    private void release(long runId) throws Exception {
        mvc.perform(post("/api/letter-runs/{id}/release", runId).with(httpBasic("staff", "staff")))
                .andExpect(status().isOk());
    }

    private List<String> externalDocIds(String appId) throws Exception {
        return docIds(get("/api/external/applications/{appId}/documents", appId), "external");
    }

    private List<String> staffDocIds(String appId) throws Exception {
        return docIds(get("/api/staff/applications/{appId}/documents", appId), "staff");
    }

    private List<String> docIds(MockHttpServletRequestBuilder request, String user) throws Exception {
        String json = mvc.perform(request.with(httpBasic(user, user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$[*].docId");
    }
}
