package com.example.letters.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs. Tests set {@code letters.scheduling.enabled=false} and call
 * the jobs directly, so background ticks never race with test data.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "letters.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
