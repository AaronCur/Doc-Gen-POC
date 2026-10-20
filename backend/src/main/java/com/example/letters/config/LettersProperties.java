package com.example.letters.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for letter generation runs, under {@code letters.*}.
 *
 * @param needsAttentionAfter a run that isn't complete this long after it was requested shows as
 *                            NEEDS_ATTENTION (and release stays blocked)
 * @param checkInterval       how often the completion job runs
 */
@ConfigurationProperties("letters")
public record LettersProperties(
        @DefaultValue("4h") Duration needsAttentionAfter,
        @DefaultValue("1m") Duration checkInterval) {
}
