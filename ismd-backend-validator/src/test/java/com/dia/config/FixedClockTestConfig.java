package com.dia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Pins "now" in tests so conversion-time dates (vytvořeno / aktualizováno fallback) are stable
 * and expected outputs can contain them.
 */
@Configuration
@Profile("test")
public class FixedClockTestConfig {

    /** 2026-01-15T10:30:00+01:00 in Europe/Prague. */
    public static final Instant FIXED_NOW = Instant.parse("2026-01-15T09:30:00Z");

    @Bean
    @Primary
    public Clock fixedTestClock() {
        return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
}
