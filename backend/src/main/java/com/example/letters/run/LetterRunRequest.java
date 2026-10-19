package com.example.letters.run;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LetterRunRequest(
        @Schema(example = "LETTER_A") @NotBlank String letterType,
        @Schema(example = "2026-02-01") @NotNull LocalDate claimSubmittedDate
) { }
