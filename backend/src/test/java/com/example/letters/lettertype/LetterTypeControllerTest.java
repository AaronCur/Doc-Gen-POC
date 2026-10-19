package com.example.letters.lettertype;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.letters.security.SecurityConfig;

import jakarta.persistence.EntityNotFoundException;

@WebMvcTest(LetterTypeController.class)
@Import(SecurityConfig.class)
public class LetterTypeControllerTest {

    private static final String VERSION = "Api-Version";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LetterTypeService letterTypeService;

    @Test
    void shouldReturnAllLetterTypes() throws Exception {
        when(letterTypeService.getAllLetterTypes()).thenReturn(List.of(new LetterTypeResponse("LETTER_A", "Letter type A")));

        mockMvc.perform(get("/api/letter-types").header(VERSION, "1").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("LETTER_A"))
                .andExpect(jsonPath("$[0].name").value("Letter type A"));
    }

    @Test
    void staffCanListLetterTypes() throws Exception {
        when(letterTypeService.getAllLetterTypes()).thenReturn(List.of());

        mockMvc.perform(get("/api/letter-types").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnEligibleCount() throws Exception {
        when(letterTypeService.getEligibleCount("LETTER_A")).thenReturn(new EligibleCountResponse("LETTER_A", 18));

        mockMvc.perform(get("/api/letter-types/LETTER_A/eligible-count").header(VERSION, "1")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LETTER_A"))
                .andExpect(jsonPath("$.eligibleCount").value(18));
    }

    @Test
    void shouldReturn404ForAnUnknownLetterType() throws Exception {
        when(letterTypeService.getEligibleCount("LETTER_Z"))
                .thenThrow(new EntityNotFoundException("Letter type not found with code: LETTER_Z"));

        mockMvc.perform(get("/api/letter-types/LETTER_Z/eligible-count").header(VERSION, "1")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Letter type not found with code: LETTER_Z"));
    }

    // --- Authorisation ---

    @Test
    void externalCannotListLetterTypes() throws Exception {
        mockMvc.perform(get("/api/letter-types").header(VERSION, "1").with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterTypeService);
    }

    @Test
    void staffCannotGetEligibleCount() throws Exception {
        mockMvc.perform(get("/api/letter-types/LETTER_A/eligible-count").header(VERSION, "1")
                        .with(httpBasic("staff", "staff")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterTypeService);
    }

    @Test
    void externalCannotGetEligibleCount() throws Exception {
        mockMvc.perform(get("/api/letter-types/LETTER_A/eligible-count").header(VERSION, "1")
                        .with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(letterTypeService);
    }

    @Test
    void anonymousCannotListLetterTypes() throws Exception {
        mockMvc.perform(get("/api/letter-types").header(VERSION, "1")).andExpect(status().isUnauthorized());
    }
}
