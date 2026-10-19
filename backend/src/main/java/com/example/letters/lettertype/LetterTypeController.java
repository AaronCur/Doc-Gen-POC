package com.example.letters.lettertype;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Roles for these endpoints are declared in SecurityConfig. */
@RestController
@RequestMapping("/api/letter-types")
@Tag(name = "Letter Type API", description = "Letter types and how many applications a run would pick up")
public class LetterTypeController {

    private final LetterTypeService letterTypeService;

    public LetterTypeController(LetterTypeService letterTypeService) {
        this.letterTypeService = letterTypeService;
    }

    @GetMapping(version = "1")
    @Operation(summary = "List letter types (ADMIN, STAFF)")
    public ResponseEntity<List<LetterTypeResponse>> getAllLetterTypes() {
        return ResponseEntity.ok(letterTypeService.getAllLetterTypes());
    }

    @GetMapping(value = "/{code}/eligible-count", version = "1")
    @Operation(summary = "Count applications a run would include now (ADMIN)")
    public ResponseEntity<EligibleCountResponse> getEligibleCount(@PathVariable String code) {
        return ResponseEntity.ok(letterTypeService.getEligibleCount(code));
    }
}
