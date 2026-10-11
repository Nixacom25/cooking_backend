package com.cooked.backend.service.impl;

import com.cooked.backend.entity.*;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomAutomationEngineTest {

    private CustomAutomationRepository automations;
    private CustomAutomationFireRepository fires;
    private UserRepository users;
    private EmailCampaignSendRepository sends;
    private PushNotificationService push;
    private EmailService email;
    private EmailCampaignService campaigns;
    private SlackNotifier slack;
    private CustomAutomationEngine engine;

    @BeforeEach
    void setUp() {
        automations = mock(CustomAutomationRepository.class);
        fires = mock(CustomAutomationFireRepository.class);
        users = mock(UserRepository.class);
        sends = mock(EmailCampaignSendRepository.class);
        push = mock(PushNotificationService.class);
        email = mock(EmailService.class);
        campaigns = mock(EmailCampaignService.class);
        slack = mock(SlackNotifier.class);
        engine = new CustomAutomationEngine(automations, fires, users, mock(SubscriptionPaymentRepository.class), sends, push, email, campaigns, slack, mock(WorkspaceSettingsService.class));
        when(campaigns.unsubscribeLink(any())).thenReturn("https://u");
    }

    private static CustomAutomation flow() {
        return CustomAutomation.builder().id(UUID.randomUUID()).name("Trial").trigger(CustomAutomation.Trigger.TRIAL_ENDS_IN).triggerValue(1)
                .audience(CustomAutomation.Audience.ANY).opened(CustomAutomation.Opened.ANY)
                .pushEnabled(true).pushTitle("Hi {firstname}").pushBody("Body")
                .emailEnabled(true).emailSubject("Subject").emailTitle("Title").emailBody("Body").status(CustomAutomation.Status.ACTIVE).build();
    }

    @Test
    void pushNeedsATokenAndConsentEmailRespectsUnsubscribeAndCap() {
        User withToken = User.builder().id(UUID.randomUUID()).email("a@x.com").firstname("Ana").fcmToken("tok").build();
        User unsubscribed = User.builder().id(UUID.randomUUID()).email("b@x.com").emailMarketingEnabled(false).build();
        User capped = User.builder().id(UUID.randomUUID()).email("c@x.com").build();
        when(sends.countByUserIdAndSentAtAfter(eq(capped.getId()), any())).thenReturn(2L);
        when(email.sendCampaignEmail(any(), any(), any(), any())).thenReturn(true);

        engine.act(flow(), withToken);
        engine.act(flow(), unsubscribed);
        engine.act(flow(), capped);

        verify(push, times(1)).sendPush(eq("tok"), eq("Hi Ana"), eq("Body"), anyMap());
        verify(email, times(1)).sendCampaignEmail(eq("a@x.com"), any(), any(), any());
        verify(sends, times(1)).save(any());
        verify(fires, times(3)).save(any()); // each user reached once, even without a channel
    }

    @Test
    void creatorApplicationAlertsTheTeam() {
        CustomAutomation team = CustomAutomation.builder().id(UUID.randomUUID()).name("Creator applied").trigger(CustomAutomation.Trigger.CREATOR_APPLIED)
                .audience(CustomAutomation.Audience.ANY).opened(CustomAutomation.Opened.ANY).slackEnabled(true).slackText("New creator application")
                .teamEmailEnabled(true).status(CustomAutomation.Status.ACTIVE).build();
        when(automations.findAllByStatusAndTrigger(CustomAutomation.Status.ACTIVE, CustomAutomation.Trigger.CREATOR_APPLIED)).thenReturn(List.of(team));
        when(users.findAllByRole(Role.ADMIN)).thenReturn(List.of(User.builder().email("admin@x.com").build()));

        engine.onEvent(new AutomationTriggerEvent("CREATOR_APPLIED", null, null, "Awa · AMBASSADOR"));

        verify(slack).post("New creator application: Awa · AMBASSADOR");
        verify(email).sendAdminSummaryEmail(eq("admin@x.com"), any(), any(), anyList());
        verifyNoInteractions(push);
    }
}
