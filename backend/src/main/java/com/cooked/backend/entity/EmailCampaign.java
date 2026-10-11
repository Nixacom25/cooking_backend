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

/** A marketing email sent through Brevo to one audience (Growth → Email → New campaign). */
@Entity
@Table(name = "email_campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCampaign {

    public enum Audience { TRIAL, TRIAL_ENDING, FREE, PAYING, INACTIVE_30 }

    public enum Status { DRAFT, SCHEDULED, SENDING, SENT, FAILED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Audience audience;

    @Column(length = 150)
    private String subject;

    @Column(length = 200)
    private String preheader;

    /** TRIAL_ENDING or BLANK. */
    @Column(length = 20)
    private String template;

    @Column(length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(length = 40)
    private String buttonLabel;

    @Column(length = 500)
    private String buttonUrl;

    @Column(length = 500)
    private String heroImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime sentAt;

    private Integer recipients;
    private Integer sentCount;
    private Integer skippedCount;
    private Integer failedCount;

    @Column(length = 160)
    private String testSentTo;
    private LocalDateTime testSentAt;

    @Column(nullable = false, length = 160)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
