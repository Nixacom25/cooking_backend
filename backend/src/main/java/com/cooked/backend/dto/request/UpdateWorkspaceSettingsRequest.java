package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Partial update: null fields are left unchanged; slackWebhookUrl "" removes the webhook. */
@Data
public class UpdateWorkspaceSettingsRequest {
    @Size(min = 1, max = 60)
    private String workspaceName;
    @Size(max = 60)
    private String timezone;
    private Boolean criticalAlertsEmail;
    private Boolean dailySummary;
    private Boolean weeklyReview;
    @Size(max = 300)
    private String slackWebhookUrl;
    private Boolean anomalyCards;
    private Boolean dripEnabled;
    private Boolean require2fa;
}
