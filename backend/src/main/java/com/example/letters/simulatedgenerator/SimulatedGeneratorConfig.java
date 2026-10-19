package com.example.letters.simulatedgenerator;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Registers the simulated generator in the dev profile only. */
@Configuration
@Profile("dev")
@EnableConfigurationProperties(SimulatedGeneratorProperties.class)
public class SimulatedGeneratorConfig {

    @Bean
    SimulatedLetterGenerator simulatedLetterGenerator(JdbcTemplate jdbc, PlatformTransactionManager transactions,
            Clock clock, SimulatedGeneratorProperties properties) {
        return new SimulatedLetterGenerator(jdbc, transactions, clock, properties);
    }
}
