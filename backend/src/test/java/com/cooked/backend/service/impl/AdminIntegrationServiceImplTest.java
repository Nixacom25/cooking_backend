package com.cooked.backend.service.impl;

import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.service.impl.AdminIntegrationServiceImpl.Counts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminIntegrationServiceImplTest {

    @Test
    void statusRules() {
        assertEquals("NOT_CONFIGURED", AdminIntegrationServiceImpl.status(IntegrationKey.STRIPE, false, new Counts(5, 0), new Counts(5, 0), null).getStatus());
        assertEquals("WARNING", AdminIntegrationServiceImpl.status(IntegrationKey.REVENUECAT, true, new Counts(10, 3), new Counts(50, 3), null).getStatus());
        assertEquals("CONNECTED", AdminIntegrationServiceImpl.status(IntegrationKey.REVENUECAT, true, new Counts(10, 1), new Counts(50, 1), null).getStatus());
        assertEquals("IDLE", AdminIntegrationServiceImpl.status(IntegrationKey.BREVO, true, new Counts(0, 0), new Counts(0, 0), null).getStatus());
    }

    @Test
    void countsAndKeys() {
        assertEquals(90.0, new Counts(10, 1).successRate());
        assertNull(new Counts(0, 0).successRate());
        assertEquals(new Counts(5, 1), new Counts(15, 3).minus(new Counts(10, 2)));
        assertEquals(IntegrationKey.GOOGLE_PLAY, AdminIntegrationServiceImpl.parse("google-play"));
        assertThrows(ResourceNotFoundException.class, () -> AdminIntegrationServiceImpl.parse("nope"));
    }
}
