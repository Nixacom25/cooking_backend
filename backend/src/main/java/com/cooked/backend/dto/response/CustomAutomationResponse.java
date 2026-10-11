package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomAutomationResponse(UUID id, String name, String templateKey, String trigger, Integer triggerValue,
                                       String audience, String opened,
                                       boolean pushEnabled, String pushTitle, String pushBody,
                                       boolean emailEnabled, String emailSubject, String emailTitle, String emailBody, String emailButton, String emailButtonUrl,
                                       boolean slackEnabled, String slackText, boolean teamEmailEnabled,
                                       String status, DryRun dryRun, boolean dryRunCurrent, LocalDateTime lastRunAt, long fired30d,
                                       String createdBy, LocalDateTime createdAt) {

    /** label explains the window ("last 7 days", "next 7 days"). */
    public record DryRun(LocalDateTime at, Integer triggered, Integer passed, Integer push, Integer email, String label) {
    }
}
