package com.example.letters.plan;

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

@WebMvcTest(PlanController.class)
@Import(SecurityConfig.class)
public class PlanControllerTest {

    private static final String VERSION = "Api-Version";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanService planService;

    @Test
    void shouldReturnAllPlanTypes() throws Exception {
        when(planService.getAllPlanTypes()).thenReturn(List.of(new PlanTypeResponse("PT-1", "[Plan type 1]")));

        mockMvc.perform(get("/api/plan-types").header(VERSION, "1").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("PT-1"))
                .andExpect(jsonPath("$[0].name").value("[Plan type 1]"));
    }

    @Test
    void shouldReturnThePlansOfAPlanType() throws Exception {
        when(planService.getPlansForPlanType("PT-1"))
                .thenReturn(List.of(new PlanResponse("PLAN-1A", "PT-1", "[Plan 1A]")));

        mockMvc.perform(get("/api/plan-types/PT-1/plans").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("PLAN-1A"))
                .andExpect(jsonPath("$[0].planTypeId").value("PT-1"));
    }

    @Test
    void shouldReturn404ForAnUnknownPlanType() throws Exception {
        when(planService.getPlansForPlanType("PT-9"))
                .thenThrow(new EntityNotFoundException("Plan type not found with id: PT-9"));

        mockMvc.perform(get("/api/plan-types/PT-9/plans").header(VERSION, "1").with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void externalCannotListPlanTypes() throws Exception {
        mockMvc.perform(get("/api/plan-types").header(VERSION, "1").with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(planService);
    }

    @Test
    void externalCannotListPlans() throws Exception {
        mockMvc.perform(get("/api/plan-types/PT-1/plans").header(VERSION, "1")
                        .with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(planService);
    }
}
