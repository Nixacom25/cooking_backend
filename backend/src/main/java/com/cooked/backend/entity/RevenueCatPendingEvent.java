package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A RevenueCat webhook event for an app user id we couldn't match yet - most
 * often a purchase made in the onboarding, before the account existed
 * ($RCAnonymousID). Applied to the account as soon as the app reports that id.
 */
@Entity
@Table(name = "revenuecat_pending_events", indexes = @Index(name = "idx_rc_pending_resolved", columnList = "resolvedAt"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueCatPendingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 40)
    private String eventType;

    /** ",id1,id2," - app_user_id, original_app_user_id and aliases, for LIKE matching. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String appUserIds;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    private UUID resolvedUserId;
}
