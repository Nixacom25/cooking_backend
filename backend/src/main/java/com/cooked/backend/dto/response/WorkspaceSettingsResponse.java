package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceSettingsResponse {
    private String workspaceName;
    private String timezone;
    private boolean criticalAlertsEmail;
    private boolean dailySummary;
    private boolean weeklyReview;
    private boolean slackConfigured;
    /** "https://hooks.slack.com/…/abcd" — the secret part is never returned. */
    private String slackWebhookHint;
    private boolean anomalyCards;
    private LocalDate lastDailySummaryOn;
    private LocalDate lastWeeklyReviewOn;
    /** Where critical alerts are emailed. */
    private String supportEmail;
}
