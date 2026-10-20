package com.example.letters.run;

import java.net.URI;
import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Roles for these endpoints are declared in SecurityConfig. */
@RestController
@RequestMapping("/api/letter-runs")
@Tag(name = "Letter Run API", description = "Generate letters for a letter type, follow progress, release to external users")
public class LetterRunController {

    private final LetterRunService letterRunService;

    public LetterRunController(LetterRunService letterRunService) {
        this.letterRunService = letterRunService;
    }

    @GetMapping(version = "1")
    @Operation(summary = "List runs, newest first (ADMIN, STAFF)")
    public ResponseEntity<LetterRunPageResponse> getLetterRuns(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(letterRunService.getLetterRuns(page, size));
    }

    @GetMapping(value = "/{id}", version = "1")
    @Operation(summary = "Get one run (ADMIN, STAFF)")
    public ResponseEntity<LetterRunResponse> getLetterRunById(@PathVariable Long id) {
        return ResponseEntity.ok(letterRunService.getLetterRunById(id));
    }

    @GetMapping(value = "/{id}/pending", version = "1")
    @Operation(summary = "Applications in the run with no document yet (ADMIN, STAFF)")
    public ResponseEntity<LetterRunPendingResponse> getPendingApplications(@PathVariable Long id) {
        return ResponseEntity.ok(letterRunService.getPendingApplications(id));
    }

    @PostMapping(version = "1")
    @Operation(summary = "Generate letters for a letter type (ADMIN)",
            description = "201 with Location. Errors: 409 RUN_IN_PROGRESS, 422 NO_ELIGIBLE_APPLICATIONS, "
                    + "400 UNKNOWN_LETTER_TYPE / VALIDATION_FAILED.")
    public ResponseEntity<LetterRunResponse> generateLetters(@Valid @RequestBody LetterRunRequest letterRunRequest,
            @Parameter(hidden = true) Principal principal) {
        LetterRunResponse newRun = letterRunService.generateLetters(letterRunRequest, principal.getName());
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()    // "http://localhost:8080/api/letter-runs"
                .path("/{id}")           // appends "/{id}"
                .buildAndExpand(newRun.runId())
                .toUri();
        return ResponseEntity.created(location).body(newRun);
    }

    @PostMapping(value = "/{id}/release", version = "1")
    @Operation(summary = "Enable a run for external users (STAFF)",
            description = "Only for a COMPLETE, STAFF_ONLY run. Errors: 409 NOT_COMPLETE / ALREADY_RELEASED.")
    public ResponseEntity<LetterRunResponse> releaseLetterRun(@PathVariable Long id,
            @Parameter(hidden = true) Principal principal) {
        return ResponseEntity.ok(letterRunService.releaseLetterRun(id, principal.getName()));
    }
}
