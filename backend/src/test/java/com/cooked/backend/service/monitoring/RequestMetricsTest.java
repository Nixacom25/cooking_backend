package com.cooked.backend.service.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestMetricsTest {

    @Test
    void percentilesErrorRateAndWindow() {
        RequestMetrics m = new RequestMetrics();
        long now = 1_000_000;
        for (int i = 1; i <= 100; i++) m.record(now, i <= 2 ? 503 : 200, i);   // 100 requests this minute, 2 errors
        m.record(now - 30, 200, 9999);                                         // outside a 15-minute window

        RequestMetrics.Snapshot s = m.snapshotAt(now, 15);
        assertEquals(100, s.requests());
        assertEquals(2, s.errors());
        assertEquals(50, s.p50Ms());
        assertEquals(95, s.p95Ms());
        assertEquals(2.0, s.errorRate());
        assertEquals(6.7, s.requestsPerMinute());
        assertEquals(101, m.snapshotAt(now, 60).requests());
    }

    @Test
    void perMinuteSeries() {
        RequestMetrics m = new RequestMetrics();
        m.record(100, 200, 10);
        m.record(100, 500, 30);
        m.record(102, 200, 5);
        var series = m.seriesAt(102, 3);
        assertEquals(3, series.size());
        assertEquals(2, series.get(0).requests());
        assertEquals(1, series.get(0).errors());
        assertEquals(30, series.get(0).p95Ms());
        assertEquals(0, series.get(1).requests());
        assertNull(series.get(1).p95Ms());
        assertEquals(1, series.get(2).requests());
    }

    @Test
    void emptyAndAiDetection() {
        RequestMetrics.Snapshot s = new RequestMetrics().snapshotAt(5, 15);
        assertNull(s.p95Ms());
        assertNull(s.errorRate());
        assertTrue(RequestMetrics.isAiRequest("POST", "/recipes/import"));
        assertTrue(RequestMetrics.isAiRequest("POST", "/recipes/scan-typed"));
        assertFalse(RequestMetrics.isAiRequest("GET", "/recipes/imports"));
    }
}
