package com.example.letters.run;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.letters.common.exception.BusinessRuleException;
import com.example.letters.security.SecurityConfig;

import jakarta.persistence.EntityNotFoundException;

@WebMvcTest(LetterRunController.class)
@Import(SecurityConfig.class)
public class LetterRunControllerTest {

    private static final String VERSION = "Api-Version";

    private static final String REQUEST_BODY = """
            {
                "letterType": "LETTER_A",
                "claimSubmittedDate": "2026-02-01"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LetterRunService letterRunService;

    private static LetterRunResponse run(RunStatus status, RunVisibility visibility) {
        return new LetterRunResponse(1L, "LETTER_A", "Letter type A", LocalDate.of(2026, 2, 1), status, visibility,
                18, status == RunStatus.COMPLETE ? 18 : 0, "admin", Instant.parse("2026-03-02T09:00:00Z"),
                visibility == RunVisibility.STAFF_AND_EXTERNAL ? "staff" : null,
                visibility == RunVisibility.STAFF_AND_EXTERNAL ? Instant.parse("2026-03-02T10:00:00Z") : null);
    }

    // --- Happy paths ---

    @Test
    void shouldReturnLetterRuns() throws Exception {
        when(letterRunService.getLetterRuns(0, 20))
                .thenReturn(new LetterRunPageResponse(List.of(run(RunStatus.IN_PROGRESS, RunVisibility.STAFF_ONLY)), 1));

        mockMvc.perform(get("/api/letter-runs").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].runId").value(1))
                .andExpect(jsonPath("$.items[0].letterTypeName").value("Letter type A"))
                .andExpect(jsonPath("$.items[0].claimSubmittedDate").value("2026-02-01"))
                .andExpect(jsonPath("$.items[0].requestedAt").value("2026-03-02T09:00:00Z"))
                .andExpect(jsonPath("$.items[0].status").value("IN_PROGRESS"));
    }

    @Test
    void shouldPassPagingToTheService() throws Exception {
        when(letterRunService.getLetterRuns(2, 10)).thenReturn(new LetterRunPageResponse(List.of(), 25));

        mockMvc.perform(get("/api/letter-runs").param("page", "2").param("size", "10").header(VERSION, "1")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25));
    }

    @Test
    void shouldReturnLetterRunById() throws Exception {
        when(letterRunService.getLetterRunById(1L)).thenReturn(run(RunStatus.COMPLETE, RunVisibility.STAFF_ONLY));

        mockMvc.perform(get("/api/letter-runs/1").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETE"));
    }

    @Test
    void shouldReturnPendingApplications() throws Exception {
        when(letterRunService.getPendingApplications(1L))
                .thenReturn(new LetterRunPendingResponse(1L, List.of("APP-0043")));

        mockMvc.perform(get("/api/letter-runs/1/pending").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(1))
                .andExpect(jsonPath("$.pendingAppIds[0]").value("APP-0043"));
    }

    @Test
    void shouldGenerateLetters() throws Exception {
        when(letterRunService.generateLetters(any(LetterRunRequest.class), eq("admin")))
                .thenReturn(run(RunStatus.QUEUED, RunVisibility.STAFF_ONLY));

        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/letter-runs/1"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.visibility").value("STAFF_ONLY"));

        verify(letterRunService).generateLetters(new LetterRunRequest("LETTER_A", LocalDate.of(2026, 2, 1)), "admin");
    }

    @Test
    void shouldReleaseLetterRun() throws Exception {
        when(letterRunService.releaseLetterRun(1L, "staff"))
                .thenReturn(run(RunStatus.COMPLETE, RunVisibility.STAFF_AND_EXTERNAL));

        mockMvc.perform(post("/api/letter-runs/1/release").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("STAFF_AND_EXTERNAL"))
                .andExpect(jsonPath("$.releasedBy").value("staff"));
    }

    @Test
    void thereIsNoRevokeEndpoint() throws Exception {
        // Revoke is a manual, change-controlled SQL procedure (README). Unlisted paths are denied.
        mockMvc.perform(post("/api/letter-runs/1/revoke").header(VERSION, "1").with(httpBasic("admin", "admin")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }

    // --- Validation errors ---

    @Test
    void shouldRejectAMissingClaimSubmittedDate() throws Exception {
        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "letterType": "LETTER_A" }
                                """)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0]").value("claimSubmittedDate: must not be null"));
        verifyNoInteractions(letterRunService);
    }

    @Test
    void shouldRejectAMalformedDate() throws Exception {
        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "letterType": "LETTER_A", "claimSubmittedDate": "01/02/2026" }
                                """)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldRejectAnOutOfRangePageSize() throws Exception {
        mockMvc.perform(get("/api/letter-runs").param("size", "500").header(VERSION, "1")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectAnUnsupportedApiVersion() throws Exception {
        mockMvc.perform(get("/api/letter-runs").header(VERSION, "2").with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void shouldDefaultToVersion1WithoutTheHeader() throws Exception {
        when(letterRunService.getLetterRuns(anyInt(), anyInt())).thenReturn(new LetterRunPageResponse(List.of(), 0));

        mockMvc.perform(get("/api/letter-runs").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectANonNumericRunId() throws Exception {
        mockMvc.perform(get("/api/letter-runs/abc").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Parameter 'id' must be of type Long"));
        verifyNoInteractions(letterRunService);
    }

    @Test
    void shouldReturn400ForAnUnknownLetterType() throws Exception {
        when(letterRunService.generateLetters(any(), any())).thenThrow(new BusinessRuleException(
                HttpStatus.BAD_REQUEST, "UNKNOWN_LETTER_TYPE", "Unknown letter type: LETTER_Z"));

        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_LETTER_TYPE"));
    }

    // --- 404, 409, 422 ---

    @Test
    void shouldReturn404ForAnUnknownRun() throws Exception {
        when(letterRunService.getLetterRunById(99L))
                .thenThrow(new EntityNotFoundException("Letter run not found with id: 99"));

        mockMvc.perform(get("/api/letter-runs/99").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Letter run not found with id: 99"));
    }

    @Test
    void shouldReturn404ForPendingOfAnUnknownRun() throws Exception {
        when(letterRunService.getPendingApplications(99L))
                .thenThrow(new EntityNotFoundException("Letter run not found with id: 99"));

        mockMvc.perform(get("/api/letter-runs/99/pending").header(VERSION, "1").with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn409WhenARunIsInProgress() throws Exception {
        when(letterRunService.generateLetters(any(), any())).thenThrow(new BusinessRuleException(
                HttpStatus.CONFLICT, "RUN_IN_PROGRESS", "A run for this letter type is still generating."));

        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("RUN_IN_PROGRESS"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn422WhenNothingIsEligible() throws Exception {
        when(letterRunService.generateLetters(any(), any())).thenThrow(new BusinessRuleException(
                HttpStatus.UNPROCESSABLE_CONTENT, "NO_ELIGIBLE_APPLICATIONS", "No new applications."));

        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_ELIGIBLE_APPLICATIONS"));
    }

    @Test
    void shouldReturn409WhenReleasingAnAlreadyReleasedRun() throws Exception {
        when(letterRunService.releaseLetterRun(1L, "staff")).thenThrow(new BusinessRuleException(
                HttpStatus.CONFLICT, "ALREADY_RELEASED", "This run is already visible to external users."));

        mockMvc.perform(post("/api/letter-runs/1/release").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RELEASED"));
    }

    @Test
    void shouldReturn409WhenReleasingAnIncompleteRun() throws Exception {
        when(letterRunService.releaseLetterRun(1L, "staff")).thenThrow(new BusinessRuleException(
                HttpStatus.CONFLICT, "NOT_COMPLETE", "Available when generation completes."));

        mockMvc.perform(post("/api/letter-runs/1/release").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_COMPLETE"));
    }

    // --- Authorisation (wrong role for every endpoint) ---

    @Test
    void externalCannotListRuns() throws Exception {
        mockMvc.perform(get("/api/letter-runs").header(VERSION, "1").with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }

    @Test
    void externalCannotGetARun() throws Exception {
        mockMvc.perform(get("/api/letter-runs/1").header(VERSION, "1").with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }

    @Test
    void externalCannotSeePendingApplications() throws Exception {
        mockMvc.perform(get("/api/letter-runs/1/pending").header(VERSION, "1")
                        .with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }

    @Test
    void staffCannotGenerate() throws Exception {
        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY)
                        .with(httpBasic("staff", "staff")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }

    @Test
    void externalCannotGenerate() throws Exception {
        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY)
                        .with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }

    @Test
    void anonymousCannotGenerate() throws Exception {
        mockMvc.perform(post("/api/letter-runs").header(VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(letterRunService);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/letter-runs").header(VERSION, "1").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOnlyRoleCannotRelease() throws Exception {
        mockMvc.perform(post("/api/letter-runs/1/release").header(VERSION, "1")
                        .with(user("admin-only").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }

    @Test
    void externalCannotRelease() throws Exception {
        mockMvc.perform(post("/api/letter-runs/1/release").header(VERSION, "1")
                        .with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterRunService);
    }
}
