package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A no-code flow built in Growth → Automations: When (trigger) → If (conditions) → Then (actions). */
@Entity
@Table(name = "custom_automations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomAutomation {

    public enum Trigger {
        TRIAL_ENDS_IN, SIGNED_UP_DAYS_AGO, INACTIVE_DAYS, PAYMENT_FAILED, IMPORT_FAILED, // about one user
        WEEKLY_MONDAY, CREATOR_APPLIED, ERRORS_SPIKE;                                      // for the team

        public boolean aboutUser() {
            return this == TRIAL_ENDS_IN || this == SIGNED_UP_DAYS_AGO || this == INACTIVE_DAYS || this == PAYMENT_FAILED || this == IMPORT_FAILED;
        }
    }

    public enum Audience { ANY, TRIAL, FREE, PAYING }

    public enum Opened { ANY, WITHIN_7_DAYS, NOT_IN_7_DAYS }

    public enum Status { DRAFT, ACTIVE, PAUSED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 40)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Trigger trigger;

    /** Days (TRIAL_ENDS_IN, SIGNED_UP_DAYS_AGO, INACTIVE_DAYS), hour (WEEKLY_MONDAY) or errors in 15 min (ERRORS_SPIKE). */
    private Integer triggerValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Audience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Opened opened;

    private boolean pushEnabled;
    @Column(length = 80)
    private String pushTitle;
    @Column(length = 200)
    private String pushBody;

    private boolean emailEnabled;
    @Column(length = 150)
    private String emailSubject;
    @Column(length = 120)
    private String emailTitle;
    @Column(columnDefinition = "TEXT")
    private String emailBody;
    @Column(length = 40)
    private String emailButton;
    @Column(length = 500)
    private String emailButtonUrl;

    private boolean slackEnabled;
    @Column(length = 300)
    private String slackText;

    /** Email every admin (team triggers, or a daily digest for user triggers). */
    private boolean teamEmailEnabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    private LocalDateTime dryRunAt;
    private Integer dryTriggered;
    private Integer dryPassed;
    private Integer dryPush;
    private Integer dryEmail;

    private LocalDateTime lastRunAt;

    @Column(nullable = false, length = 160)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
