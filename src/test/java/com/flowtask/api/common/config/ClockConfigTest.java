package com.flowtask.api.common.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void truncatesToMicrosecondsToMatchDatabasePrecision() {
        Clock nanosecondClock = Clock.fixed(Instant.parse("2026-09-30T16:12:33.363216789Z"), ZoneOffset.UTC);

        Clock clock = ClockConfig.withDatabasePrecision(nanosecondClock);

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-30T16:12:33.363216Z"));
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
