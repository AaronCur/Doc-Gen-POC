package com.example.letters.lettertype;

public record EligibleCountResponse(
        String code,
        String planId,
        long eligibleCount
) { }
