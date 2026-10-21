package com.example.letters.plan;

import java.util.List;

import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;

/** STAND-IN: in the real system plan types and plans come from the existing admin screen. */
@Service
public class PlanService {

    private final PlanTypeRepository planTypeRepository;
    private final PlanRepository planRepository;

    public PlanService(PlanTypeRepository planTypeRepository, PlanRepository planRepository) {
        this.planTypeRepository = planTypeRepository;
        this.planRepository = planRepository;
    }

    public List<PlanTypeResponse> getAllPlanTypes() {
        return planTypeRepository.findAllByOrderByNameAsc().stream()
                .map(planType -> new PlanTypeResponse(planType.getId(), planType.getName()))
                .toList();
    }

    public List<PlanResponse> getPlansForPlanType(String planTypeId) {
        if (!planTypeRepository.existsById(planTypeId)) {
            throw new EntityNotFoundException("Plan type not found with id: " + planTypeId);
        }
        return planRepository.findAllByPlanType_IdOrderByNameAsc(planTypeId).stream()
                .map(plan -> mapToResponse(plan, planTypeId))
                .toList();
    }

    private PlanResponse mapToResponse(Plan plan, String planTypeId) {
        return new PlanResponse(plan.getId(), planTypeId, plan.getName());
    }
}
