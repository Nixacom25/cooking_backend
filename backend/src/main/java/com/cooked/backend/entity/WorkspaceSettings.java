package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Backoffice-wide settings (single row, id = 1). Every field drives a real behaviour. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "workspace_settings")
public class WorkspaceSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Used in summary emails / Slack messages. */
    @Column(nullable = false, length = 60)
    private String workspaceName;

    /** IANA zone: when the 8 AM summaries go out. */
    @Column(nullable = false, length = 60)
    private String timezone;

    /** Email the support team on each critical app error. */
    @Column(nullable = false)
    private boolean criticalAlertsEmail;

    /** Daily summary email to admins at 8 AM. */
    @Column(nullable = false)
    private boolean dailySummary;

    /** Weekly review email to admins on Mondays at 8 AM. */
    @Column(nullable = false)
    private boolean weeklyReview;

    /** Slack incoming webhook (critical alerts + summaries); never returned in full. */
    @Column(length = 300)
    private String slackWebhookUrl;

    /** Show the "Something changed" / alert cards in the backoffice. */
    @Column(nullable = false)
    private boolean anomalyCards;

    /** Day-3 / day-7 nudges to free users (off until turned on in Settings). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean dripEnabled;

    private LocalDate lastDailySummaryOn;
    private LocalDate lastWeeklyReviewOn;

    public static WorkspaceSettings defaults() {
        return WorkspaceSettings.builder().id(SINGLETON_ID).workspaceName("Cooked").timezone("America/Toronto")
                .criticalAlertsEmail(true).dailySummary(false).weeklyReview(false).anomalyCards(true).build();
    }
}
