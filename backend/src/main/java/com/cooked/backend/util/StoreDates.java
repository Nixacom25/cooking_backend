package com.cooked.backend.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Dates coming from the stores / RevenueCat are ISO-8601 instants
 * ("2027-10-02T05:23:44Z", sometimes with millis or an offset). Our entities
 * store LocalDateTime in the server zone (same as LocalDateTime.now() and the
 * webhook), so every store date must go through here - LocalDateTime.parse()
 * rejects the trailing "Z" and silently left paying users on their trial end.
 */
public final class StoreDates {

    private StoreDates() {}

    /** Parses an ISO date from a store; returns null when blank or unparseable. */
    public static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        try {
            return toLocal(Instant.parse(v));
        } catch (DateTimeParseException ignored) {}
        try {
            return toLocal(OffsetDateTime.parse(v).toInstant());
        } catch (DateTimeParseException ignored) {}
        try {
            // No zone given: treat as UTC, which is what the stores send.
            return toLocal(LocalDateTime.parse(v).atZone(ZoneId.of("UTC")).toInstant());
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    public static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
