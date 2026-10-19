package com.example.letters.run;

import java.net.URI;
import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

    /** 201 with the new run, or 200 with the original run when the Idempotency-Key repeats. */
    @PostMapping(version = "1")
    @Operation(summary = "Generate letters for a letter type (ADMIN)",
            description = "201 with Location for a new run; 200 with the original run for a repeated Idempotency-Key. "
                    + "Errors: 409 RUN_IN_PROGRESS / IDEMPOTENCY_KEY_REUSED, 422 NO_ELIGIBLE_APPLICATIONS, "
                    + "400 UNKNOWN_LETTER_TYPE / VALIDATION_FAILED.")
    public ResponseEntity<LetterRunResponse> generateLetters(
            @Parameter(description = "Client-generated key; send the same key when retrying")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody LetterRunRequest letterRunRequest,
            @Parameter(hidden = true) Principal principal) {
        LetterRunService.GenerateResult result =
                letterRunService.generateLetters(letterRunRequest, idempotencyKey, principal.getName());
        if (!result.created()) {
            return ResponseEntity.ok(result.run());
        }
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()    // "http://localhost:8080/api/letter-runs"
                .path("/{id}")           // appends "/{id}"
                .buildAndExpand(result.run().runId())
                .toUri();
        return ResponseEntity.created(location).body(result.run());
    }

    @PostMapping(value = "/{id}/release", version = "1")
    @Operation(summary = "Enable a run for external users (STAFF)",
            description = "Only for a COMPLETE, STAFF_ONLY run. Errors: 409 NOT_COMPLETE / ALREADY_RELEASED.")
    public ResponseEntity<LetterRunResponse> releaseLetterRun(@PathVariable Long id,
            @Parameter(hidden = true) Principal principal) {
        return ResponseEntity.ok(letterRunService.releaseLetterRun(id, principal.getName()));
    }

    /** No UI on purpose: an audited procedure for undoing a mistaken release. */
    @PostMapping(value = "/{id}/revoke", version = "1")
    @Operation(summary = "Revoke a mistaken release (ADMIN, no UI)", description = "Errors: 409 NOT_RELEASED.")
    public ResponseEntity<LetterRunResponse> revokeLetterRun(@PathVariable Long id,
            @Parameter(hidden = true) Principal principal) {
        return ResponseEntity.ok(letterRunService.revokeLetterRun(id, principal.getName()));
    }
}
