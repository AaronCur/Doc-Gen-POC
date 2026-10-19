package com.example.letters.simulatedgenerator;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the simulated letter-generator (dev profile only).
 *
 * @param interval      delay between ticks
 * @param batchSize     maximum queue rows processed per tick
 * @param failingAppIds applications that always fail and stay flagged N (stuck-run demo)
 */
@ConfigurationProperties("simulated-generator")
public record SimulatedGeneratorProperties(
        @DefaultValue("10s") Duration interval,
        @DefaultValue("5") int batchSize,
        @DefaultValue List<String> failingAppIds) {
}
