package com.example.letters.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock PlanTypeRepository planTypeRepository;
    @Mock PlanRepository planRepository;

    @InjectMocks PlanService planService;

    @Test
    void mapsPlanTypesToResponses() {
        when(planTypeRepository.findAllByOrderByNameAsc()).thenReturn(List.of(new PlanType("PT-1", "[Plan type 1]")));

        assertThat(planService.getAllPlanTypes()).containsExactly(new PlanTypeResponse("PT-1", "[Plan type 1]"));
    }

    @Test
    void mapsThePlansOfAPlanType() {
        PlanType planType = new PlanType("PT-1", "[Plan type 1]");
        when(planTypeRepository.existsById("PT-1")).thenReturn(true);
        when(planRepository.findAllByPlanType_IdOrderByNameAsc("PT-1"))
                .thenReturn(List.of(new Plan("PLAN-1A", planType, "[Plan 1A]"), new Plan("PLAN-1B", planType, "[Plan 1B]")));

        assertThat(planService.getPlansForPlanType("PT-1")).containsExactly(
                new PlanResponse("PLAN-1A", "PT-1", "[Plan 1A]"),
                new PlanResponse("PLAN-1B", "PT-1", "[Plan 1B]"));
    }

    @Test
    void anUnknownPlanTypeIsNotFound() {
        when(planTypeRepository.existsById("PT-9")).thenReturn(false);

        assertThatThrownBy(() -> planService.getPlansForPlanType("PT-9"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Plan type not found with id: PT-9");
        verify(planRepository, never()).findAllByPlanType_IdOrderByNameAsc(anyString());
    }
}
