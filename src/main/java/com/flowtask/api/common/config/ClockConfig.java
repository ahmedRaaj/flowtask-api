package com.flowtask.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return withDatabasePrecision(Clock.systemDefaultZone());
    }

    /**
     * PostgreSQL {@code timestamptz} stores microseconds and rounds anything finer. Truncating here keeps
     * timestamps returned straight after a write identical to the values later read back.
     */
    static Clock withDatabasePrecision(Clock clock) {
        return Clock.tick(clock, Duration.ofNanos(1_000));
    }
}
