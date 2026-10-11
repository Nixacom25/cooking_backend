package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmailCampaignResponse(UUID id, String audience, String subject, String preheader, String template,
                                    String title, String body, String buttonLabel, String buttonUrl, String heroImageUrl,
                                    String status, LocalDateTime scheduledAt, LocalDateTime sentAt,
                                    Integer recipients, Integer sentCount, Integer skippedCount, Integer failedCount,
                                    String testSentTo, LocalDateTime testSentAt, String createdBy, LocalDateTime createdAt) {
}
