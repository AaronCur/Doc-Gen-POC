package com.example.letters.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for letter generation runs, under {@code letters.*}.
 *
 * @param stuckCycles        how many letter-generator cycles may pass with no new letters before a
 *                           run is marked NEEDS_ATTENTION
 * @param generatorInterval  how often the letter-generator polls the queue. Set this to the real
 *                           generator's schedule; it is not used to drive anything here.
 * @param stuckCheckInterval how often the stuck detector runs
 */
@ConfigurationProperties("letters")
public record LettersProperties(
        @DefaultValue("3") int stuckCycles,
        @DefaultValue("10s") Duration generatorInterval,
        @DefaultValue("1m") Duration stuckCheckInterval) {

    /** A run with no progress for longer than this needs attention. */
    public Duration stuckThreshold() {
        return generatorInterval.multipliedBy(stuckCycles);
    }
}
