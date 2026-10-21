package com.example.letters.run;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LetterRunRequest(
        @Schema(example = "PLAN-1A") @NotBlank String planId,
        @Schema(example = "LETTER_A") @NotBlank String letterType,
        @Schema(description = "Deadline for claim submission", example = "2026-02-01") @NotNull LocalDate claimDeadlineDate,
        @Schema(description = "Why this run is being generated; passed to the comment store, not returned",
                example = "Annual decision letters") @NotBlank @Size(max = 500) String comment
) { }
