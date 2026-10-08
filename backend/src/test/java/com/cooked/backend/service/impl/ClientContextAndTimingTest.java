package com.cooked.backend.service.impl;

import com.cooked.backend.repository.RecipeAssignmentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientContextAndTimingTest {

    @Test
    void clientContextIsValidated() {
        assertEquals("1.0.5+107", ClientContext.version(" 1.0.5+107 "));
        assertNull(ClientContext.version("1.0<script>"));
        assertEquals("SN", ClientContext.country("sn"));
        assertNull(ClientContext.country("SEN"));
        assertNull(ClientContext.country(null));
        assertTrue(AdminUserServiceImpl.versionKey("1.0.10+2").compareTo(AdminUserServiceImpl.versionKey("1.0.9+200")) > 0);
    }

    record T(UUID getUserId, LocalDateTime getAssignedDate, LocalDateTime getSubmittedDate, LocalDateTime getValidatedDate)
            implements RecipeAssignmentRepository.Timing {
    }

    @Test
    void averageHoursToSubmitAndValidate() {
        UUID u = UUID.randomUUID();
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 1, 8, 0);
        var times = RecipeAssignmentServiceImpl.timingByUser(List.of(
                new T(u, t0, t0.plusHours(2), t0.plusHours(5)),
                new T(u, t0, t0.plusHours(4), null)));
        assertEquals(3.0, RecipeAssignmentServiceImpl.avgHours(times.get(u), 0));
        assertEquals(3.0, RecipeAssignmentServiceImpl.avgHours(times.get(u), 2));
        assertNull(RecipeAssignmentServiceImpl.avgHours(null, 0));
    }
}
