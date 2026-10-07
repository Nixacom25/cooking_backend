package com.cooked.backend.service;

import com.cooked.backend.entity.User;
import com.cooked.backend.entity.WorkspaceSettings;
import com.cooked.backend.repository.DripSendRepository;
import com.cooked.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DripFunnelServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final DripSendRepository sends = mock(DripSendRepository.class);
    private final PushNotificationService push = mock(PushNotificationService.class);
    private final EmailService email = mock(EmailService.class);
    private final WorkspaceSettingsService settings = mock(WorkspaceSettingsService.class);
    private final DripFunnelService drip = new DripFunnelService(users, sends, push, email, settings);

    private static User user(String token, String lang) {
        User u = User.builder().email("u@test.com").build();
        u.setId(UUID.randomUUID());
        u.setFcmToken(token);
        u.setLanguage(lang);
        return u;
    }

    @Test
    void offByDefault() {
        when(settings.current()).thenReturn(WorkspaceSettings.defaults());
        drip.processDripCampaigns();
        verifyNoInteractions(users, push, email);
    }

    @Test
    void pushFirstEmailOtherwiseOncePerStep() {
        WorkspaceSettings s = WorkspaceSettings.defaults();
        s.setDripEnabled(true);
        when(settings.current()).thenReturn(s);
        User withToken = user("tok", "FR Français");
        User noToken = user(null, "US English");
        when(users.findDripCohort(any(), any(), any())).thenReturn(List.of(withToken, noToken), List.of());

        drip.processDripCampaigns();
        verify(push).sendPush(eq("tok"), eq("Cuisinez plus avec Cooked Premium"), anyString(), argThat(m -> "drip".equals(m.get("type"))));
        verify(email).sendDripEmail(eq("u@test.com"), any(), eq("Cook more with Cooked Premium"), anyString());
        verify(sends, times(2)).save(any());

        when(sends.existsByUserIdAndStep(withToken.getId(), "DAY3")).thenReturn(true);
        assertFalse(drip.send(withToken, DripFunnelService.Step.DAY3));
    }
}
