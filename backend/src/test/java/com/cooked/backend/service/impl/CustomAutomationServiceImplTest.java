package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CustomAutomationRequest;
import com.cooked.backend.entity.CustomAutomation;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.CustomAutomationFireRepository;
import com.cooked.backend.repository.CustomAutomationRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomAutomationServiceImplTest {

    @Mock private CustomAutomationRepository automations;
    @Mock private CustomAutomationFireRepository fires;
    @Mock private UserRepository users;
    @Mock private ActivityLogService activityLog;
    @InjectMocks private CustomAutomationServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(automations.save(any())).thenAnswer(i -> { CustomAutomation a = i.getArgument(0); if (a.getId() == null) a.setId(UUID.randomUUID()); return a; });
    }

    private static CustomAutomationRequest trialNudge() {
        return CustomAutomationServiceImpl.TEMPLATES.get(0).preset();
    }

    @Test
    void everyAvailableTemplateIsAValidFlow() {
        for (var t : CustomAutomationServiceImpl.TEMPLATES) {
            if (!t.available()) { assertNotNull(t.reason()); continue; }
            CustomAutomation a = CustomAutomation.builder().build();
            CustomAutomationServiceImpl.apply(a, t.preset());
            assertTrue(CustomAutomationServiceImpl.problems(a).isEmpty(), t.key() + " " + CustomAutomationServiceImpl.problems(a));
        }
    }

    @Test
    void teamTriggersCantMessageUsersAndValuesHaveRanges() {
        CustomAutomationRequest bad = new CustomAutomationRequest("x", null, "WEEKLY_MONDAY", 9, "ANY", "ANY",
                true, "t", "b", false, null, null, null, null, null, false, null, false);
        assertThrows(BadRequestException.class, () -> CustomAutomationServiceImpl.apply(CustomAutomation.builder().build(), bad));
        assertThrows(BadRequestException.class, () -> CustomAutomationServiceImpl.value(CustomAutomation.Trigger.INACTIVE_DAYS, 1));
        assertEquals(14, CustomAutomationServiceImpl.value(CustomAutomation.Trigger.INACTIVE_DAYS, null));
    }

    @Test
    void activationNeedsAFreshDryRunAndEditsPause() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        var created = service.create(trialNudge(), "admin@x.com");
        CustomAutomation a = CustomAutomation.builder().build();
        CustomAutomationServiceImpl.apply(a, trialNudge());
        a.setId(created.id());
        a.setStatus(CustomAutomation.Status.DRAFT);
        a.setCreatedBy("admin@x.com");
        when(automations.findById(a.getId())).thenReturn(Optional.of(a));
        assertThrows(BadRequestException.class, () -> service.activate(a.getId(), "admin@x.com"));

        service.dryRun(a.getId());
        assertNotNull(a.getDryRunAt());
        service.activate(a.getId(), "admin@x.com");
        assertEquals(CustomAutomation.Status.ACTIVE, a.getStatus());

        service.update(a.getId(), trialNudge(), "admin@x.com");
        assertEquals(CustomAutomation.Status.PAUSED, a.getStatus());
        assertNull(a.getDryRunAt());
    }

    @Test
    void crashSpikesAreCountedOncePerBurst() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 10, 12, 0);
        List<LocalDateTime> times = List.of(t, t.plusMinutes(1), t.plusMinutes(2), t.plusMinutes(3), t.plusMinutes(4), t.plusMinutes(5),
                t.plusHours(2), t.plusHours(3));
        assertEquals(1, CustomAutomationServiceImpl.spikes(times, 5));
        assertEquals(0, CustomAutomationServiceImpl.spikes(times, 7));
    }
}
