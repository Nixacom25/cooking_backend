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
}
