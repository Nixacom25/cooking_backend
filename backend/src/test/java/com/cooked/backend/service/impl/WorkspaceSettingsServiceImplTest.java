package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.UpdateWorkspaceSettingsRequest;
import com.cooked.backend.entity.WorkspaceSettings;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.WorkspaceSettingsRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkspaceSettingsServiceImplTest {

    private final WorkspaceSettingsRepository repo = mock(WorkspaceSettingsRepository.class);
    private final WorkspaceSettingsServiceImpl service = new WorkspaceSettingsServiceImpl(repo, "contact@cookedapp.com");

    @Test
    void defaultsAndPartialUpdate() {
        when(repo.findById(1L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertTrue(service.get().isCriticalAlertsEmail());
        assertFalse(service.get().isDailySummary());

        UpdateWorkspaceSettingsRequest r = new UpdateWorkspaceSettingsRequest();
        r.setDailySummary(true);
        r.setTimezone("Europe/Paris");
        r.setSlackWebhookUrl("https://hooks.slack.com/services/T000/B000/secretXYZ1");
        var out = service.update(r);
        assertTrue(out.isDailySummary());
        assertTrue(out.isCriticalAlertsEmail());                       // untouched
        assertEquals("Europe/Paris", out.getTimezone());
        assertTrue(out.isSlackConfigured());
        assertFalse(out.getSlackWebhookHint().contains("secret"));      // never returned in full
        assertTrue(out.getSlackWebhookHint().endsWith("XYZ1"));
    }

    @Test
    void rejectsBadValues() {
        when(repo.findById(1L)).thenReturn(Optional.of(WorkspaceSettings.defaults()));
        UpdateWorkspaceSettingsRequest tz = new UpdateWorkspaceSettingsRequest();
        tz.setTimezone("Mars/Base");
        assertThrows(BadRequestException.class, () -> service.update(tz));
        UpdateWorkspaceSettingsRequest slack = new UpdateWorkspaceSettingsRequest();
        slack.setSlackWebhookUrl("https://evil.example.com/hook");
        assertThrows(BadRequestException.class, () -> service.update(slack));
    }
}
