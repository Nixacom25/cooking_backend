package com.cooked.backend.service;

import com.cooked.backend.dto.request.EmailCampaignRequest;
import com.cooked.backend.dto.request.EmailCampaignScheduleRequest;
import com.cooked.backend.dto.response.EmailAudienceCount;
import com.cooked.backend.dto.response.EmailCampaignResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/**
 * Marketing email campaigns sent through Brevo. Unsubscribed users are never
 * emailed and nobody gets more than 2 campaign emails in 7 days.
 */
public interface EmailCampaignService {

    List<EmailAudienceCount> audiences();

    Page<EmailCampaignResponse> list(int page, int size);

    EmailCampaignResponse get(UUID id);

    EmailCampaignResponse create(EmailCampaignRequest request, String adminEmail);

    /** Only drafts can be edited. */
    EmailCampaignResponse update(UUID id, EmailCampaignRequest request);

    /** The email as recipients will see it (first name "Alex"). */
    String preview(UUID id);

    /** Sends the campaign to the admin's own address, marked [Test]. */
    EmailCampaignResponse sendTest(UUID id, String adminEmail);

    /** Needs a complete campaign, a test send and the confirmation box. */
    EmailCampaignResponse schedule(UUID id, EmailCampaignScheduleRequest request, String adminEmail);

    EmailCampaignResponse cancel(UUID id, String adminEmail);

    /** Job: sends the campaigns whose time has come (and resumes interrupted ones). */
    void sendDue();

    /** Public unsubscribe link: true when the signature matches and the user was found. */
    boolean unsubscribe(UUID userId, String token);
}
