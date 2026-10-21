package com.example.letters.plan;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * STAND-IN endpoints for the POC. In the real system the plan type and plan lists come from the
 * existing admin screen. Roles are declared in SecurityConfig.
 */
@RestController
@RequestMapping("/api/plan-types")
@Tag(name = "Plan API (stand-in)", description = "Stub plan types and plans; the real ones come from the existing admin screen")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping(version = "1")
    @Operation(summary = "List plan types (ADMIN, STAFF; stand-in)")
    public ResponseEntity<List<PlanTypeResponse>> getAllPlanTypes() {
        return ResponseEntity.ok(planService.getAllPlanTypes());
    }

    @GetMapping(value = "/{id}/plans", version = "1")
    @Operation(summary = "List the plans of a plan type (ADMIN, STAFF; stand-in)")
    public ResponseEntity<List<PlanResponse>> getPlansForPlanType(@PathVariable String id) {
        return ResponseEntity.ok(planService.getPlansForPlanType(id));
    }
}
