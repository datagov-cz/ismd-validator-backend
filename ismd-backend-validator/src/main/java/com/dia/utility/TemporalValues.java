package com.dia.utility;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Formats vocabulary dates (vytvořeno / aktualizováno) as {@code xsd:dateTimeStamp} values,
 * which require a timezone offset. Source timestamps without a zone are read as Czech local time.
 */
@Slf4j
public final class TemporalValues {

    public static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/Prague");

    private static final DateTimeFormatter EA_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TemporalValues() {
    }

    /** E.g. {@code 2025-06-27T14:23:30+02:00}. */
    public static String toDateTimeStamp(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS)
                .atZone(SOURCE_ZONE)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Parses an Enterprise Architect timestamp ({@code 2025-06-27 14:23:30}, Czech local time). */
    public static Optional<Instant> parseEaTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value.trim(), EA_TIMESTAMP).atZone(SOURCE_ZONE).toInstant());
        } catch (DateTimeParseException e) {
            log.warn("Ignoring unparseable EA timestamp '{}'", value);
            return Optional.empty();
        }
    }
}
