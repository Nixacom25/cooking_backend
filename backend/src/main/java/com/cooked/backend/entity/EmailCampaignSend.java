package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One campaign email delivered to one user: the frequency cap (2 per 7 days) counts these. */
@Entity
@Table(name = "email_campaign_sends", indexes = {
        @Index(name = "idx_campaign_send_user_time", columnList = "userId, sentAt"),
        @Index(name = "idx_campaign_send_campaign", columnList = "campaignId")
}, uniqueConstraints = @UniqueConstraint(name = "uk_campaign_send", columnNames = {"campaignId", "userId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCampaignSend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID campaignId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime sentAt;
}
