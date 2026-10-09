package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.IntegrationStatusResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemStatusServiceImplTest {

    @Test
    void featureStatusFromSuccessRate() {
        assertEquals("IDLE", SystemStatusServiceImpl.featureStatus("Scan AI", 0, 0, 0).status());
        assertEquals("UP", SystemStatusServiceImpl.featureStatus("Scan AI", 10, 1, 0).status());
        assertEquals("DEGRADED", SystemStatusServiceImpl.featureStatus("Scan AI", 10, 3, 0).status());
        assertEquals("DOWN", SystemStatusServiceImpl.featureStatus("Scan AI", 10, 6, 0).status());
        assertEquals("90% success · 10 in 24 h", SystemStatusServiceImpl.featureStatus("Scan AI", 10, 1, 0).detail());
        // a link without a recipe is a refused input, not an outage (the 3 imports / 1 TikTok photo case)
        assertEquals("UP", SystemStatusServiceImpl.featureStatus("Import", 3, 0, 1).status());
        assertEquals("100% success · 3 in 24 h · 1 unusable input", SystemStatusServiceImpl.featureStatus("Import", 3, 0, 1).detail());
    }

    @Test
    void integrationStatus() {
        IntegrationStatusResponse brevo = new IntegrationStatusResponse();
        brevo.setConfigured(true);
        brevo.setStatus("CONNECTED");
        brevo.setEvents24h(20);
        brevo.setFailures24h(1);
        assertEquals("UP", SystemStatusServiceImpl.integration("Email", brevo).status());
        brevo.setFailures24h(5);
        assertEquals("DEGRADED", SystemStatusServiceImpl.integration("Email", brevo).status());
        brevo.setEvents24h(0);
        assertEquals("IDLE", SystemStatusServiceImpl.integration("Email", brevo).status());
        brevo.setConfigured(false);
        assertEquals("NOT_CONFIGURED", SystemStatusServiceImpl.integration("Email", brevo).status());
    }

    @Test
    void samplesAreThrottledAndCapped() {
        SystemStatusServiceImpl svc = new SystemStatusServiceImpl(null, null, null, null, null, null);
        java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 10, 9, 12, 0);
        assertEquals(1, svc.sample(t, 40, 0).size());
        assertEquals(1, svc.sample(t.plusSeconds(5), 41, 1).size()); // too soon
        assertEquals(2, svc.sample(t.plusSeconds(25), 42, 2).size());
        for (int i = 2; i < 50; i++) svc.sample(t.plusSeconds(25L * i), 50, 0);
        var all = svc.sample(t.plusHours(1), 60, 0);
        assertEquals(SystemStatusServiceImpl.MAX_SAMPLES, all.size());
        assertEquals(60, all.get(all.size() - 1).dbLatencyMs());
    }
}
