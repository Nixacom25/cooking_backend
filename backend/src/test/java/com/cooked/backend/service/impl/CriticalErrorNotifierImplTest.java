package com.cooked.backend.service.impl;

import com.cooked.backend.entity.CriticalError;
import com.cooked.backend.entity.WorkspaceSettings;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.SlackNotifier;
import com.cooked.backend.service.WorkspaceSettingsService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CriticalErrorNotifierImplTest {

    @Test
    void emailFollowsSettingsAndSlackNeverGetsUserIdentity() {
        EmailService email = mock(EmailService.class);
        SlackNotifier slack = mock(SlackNotifier.class);
        WorkspaceSettingsService settings = mock(WorkspaceSettingsService.class);
        WorkspaceSettings s = WorkspaceSettings.defaults();
        when(settings.current()).thenReturn(s);
        CriticalErrorNotifierImpl n = new CriticalErrorNotifierImpl(email, slack, settings, "team@cooked.app");
        CriticalError e = CriticalError.builder().errorType("CRASH").errorMessage("boom").userEmail("user@x.com").platform("ios").build();

        n.notify(e, "ERR-1");
        verify(email).sendCriticalErrorAlert(eq("team@cooked.app"), eq("ERR-1"), eq("CRASH"), any(), any(), any(), any(), any(), any(), any());
        verify(slack).post(argThat(t -> t.contains("ERR-1") && !t.contains("user@x.com")));

        s.setCriticalAlertsEmail(false);
        n.notify(e, "ERR-2");
        verifyNoMoreInteractions(email);
    }
}
