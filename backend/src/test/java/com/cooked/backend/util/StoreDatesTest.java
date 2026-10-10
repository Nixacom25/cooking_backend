package com.cooked.backend.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class StoreDatesTest {

    private static LocalDateTime local(String instant) {
        return LocalDateTime.ofInstant(Instant.parse(instant), ZoneId.systemDefault());
    }

    @Test
    void parsesRevenueCatUtcInstant() {
        // Exact value that broke production: LocalDateTime.parse rejects the "Z".
        assertEquals(local("2027-10-02T05:23:44Z"), StoreDates.parse("2027-10-02T05:23:44Z"));
    }

    @Test
    void parsesMillisAndOffsets() {
        assertEquals(local("2027-10-02T05:23:44.123Z"), StoreDates.parse("2027-10-02T05:23:44.123Z"));
        assertEquals(local("2027-10-02T03:23:44Z"), StoreDates.parse("2027-10-02T05:23:44+02:00"));
    }

    @Test
    void zonelessDateIsUtc() {
        assertEquals(local("2027-10-02T05:23:44Z"), StoreDates.parse("2027-10-02T05:23:44"));
    }

    @Test
    void blankOrGarbageIsNull() {
        assertNull(StoreDates.parse(null));
        assertNull(StoreDates.parse(""));
        assertNull(StoreDates.parse("not a date"));
    }
}
