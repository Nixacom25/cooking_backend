package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.EmailCampaignScheduleRequest;
import com.cooked.backend.entity.EmailCampaign;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.EmailCampaignRepository;
import com.cooked.backend.repository.EmailCampaignSendRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailCampaignServiceImplTest {

    private EmailCampaignRepository campaigns;
    private EmailCampaignSendRepository sends;
    private UserRepository users;
    private EmailService email;
    private EmailCampaignServiceImpl service;

    @BeforeEach
    void setUp() {
        campaigns = mock(EmailCampaignRepository.class);
        sends = mock(EmailCampaignSendRepository.class);
        users = mock(UserRepository.class);
        email = mock(EmailService.class);
        service = new EmailCampaignServiceImpl(campaigns, sends, users, email, mock(ActivityLogService.class), "test-secret", "https://api.test/");
        when(campaigns.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private EmailCampaign complete(EmailCampaign.Status st) {
        return EmailCampaign.builder().id(UUID.randomUUID()).status(st).audience(EmailCampaign.Audience.TRIAL)
                .subject("Your trial ends soon").title("Hi {firstname}").body("Keep cooking.\n\nSee you <b>soon</b>").buttonLabel("Keep Premium")
                .createdBy("admin@x.com").build();
    }

    @Test
    void unsubscribeNeedsTheRightSignature() {
        UUID id = UUID.randomUUID();
        User u = User.builder().id(id).email("a@x.com").build();
        when(users.findById(id)).thenReturn(Optional.of(u));
        assertFalse(service.unsubscribe(id, "forged"));
        assertTrue(u.isEmailMarketingEnabled());
        assertTrue(service.unsubscribe(id, service.token(id)));
        assertFalse(u.isEmailMarketingEnabled());
        assertTrue(service.unsubscribeUrl(id).startsWith("https://api.test/public/email/unsubscribe?u=" + id + "&t="));
    }

    @Test
    void schedulingNeedsATestAndTheConfirmation() {
        EmailCampaign c = complete(EmailCampaign.Status.DRAFT);
        when(campaigns.findById(c.getId())).thenReturn(Optional.of(c));
        assertThrows(BadRequestException.class, () -> service.schedule(c.getId(), new EmailCampaignScheduleRequest(null, true), "admin@x.com"));
        c.setTestSentAt(LocalDateTime.now());
        assertThrows(BadRequestException.class, () -> service.schedule(c.getId(), new EmailCampaignScheduleRequest(null, false), "admin@x.com"));
        service.schedule(c.getId(), new EmailCampaignScheduleRequest(null, true), "admin@x.com");
        assertEquals(EmailCampaign.Status.SCHEDULED, c.getStatus());
        assertThrows(BadRequestException.class, () -> service.update(c.getId(), null));
    }

    @Test
    void htmlIsEscapedPersonalisedAndHasTheUnsubscribeLink() {
        String html = EmailCampaignServiceImpl.html(complete(EmailCampaign.Status.DRAFT), "Awa", "https://u/x?u=1&t=2");
        assertTrue(html.contains("Hi Awa"));
        assertTrue(html.contains("&lt;b&gt;soon&lt;/b&gt;"));
        assertFalse(html.contains("<b>soon</b>"));
        assertTrue(html.contains("https://u/x?u=1&amp;t=2"));
        assertTrue(html.contains("Keep Premium"));
    }

    @Test
    void sendingRecordsEachDeliveryAndFinishes() {
        EmailCampaign c = complete(EmailCampaign.Status.SCHEDULED);
        c.setScheduledAt(LocalDateTime.now().minusMinutes(1));
        UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID();
        when(campaigns.findById(c.getId())).thenReturn(Optional.of(c));
        when(campaigns.countAudience(eq("TRIAL"), any(), any(), any())).thenReturn(3L);
        when(campaigns.countAudienceUnderCap(eq("TRIAL"), any(), any(), any(), any())).thenReturn(2L);
        when(campaigns.nextRecipients(eq("TRIAL"), any(), any(), any(), any(), eq(c.getId()), any()))
                .thenReturn(List.<Object[]>of(new Object[]{u1, "a@x.com", "Ana"}, new Object[]{u2, "b@x.com", null}))
                .thenReturn(List.<Object[]>of(new Object[]{u2, "b@x.com", null}))
                .thenReturn(List.<Object[]>of());
        when(email.sendCampaignEmail(eq("a@x.com"), any(), any(), any())).thenReturn(true);
        when(email.sendCampaignEmail(eq("b@x.com"), any(), any(), any())).thenReturn(false);

        service.send(c.getId());

        assertEquals(EmailCampaign.Status.SENT, c.getStatus());
        assertEquals(1, c.getSentCount());
        assertEquals(1, c.getFailedCount());
        assertEquals(1, c.getSkippedCount());
        verify(sends, times(1)).save(any());
        verify(email, times(1)).sendCampaignEmail(eq("b@x.com"), any(), any(), any()); // a failed address isn't retried in the same run
    }
}
