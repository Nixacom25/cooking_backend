package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One exchange with an external service: a webhook received, an email sent,
 * a billing sync. Never stores payloads or secrets, only the outcome.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "integration_events", indexes = @Index(name = "idx_integration_events_key_created", columnList = "integration, createdAt"))
public class IntegrationEvent {

    public static final int TEXT_MAX = 160;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IntegrationKey integration;

    /** Event type ("RENEWAL", "checkout.session.completed") or email template. */
    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean success;

    private Integer httpStatus;

    private Integer latencyMs;

    /** Short error, when not successful. */
    @Column(length = TEXT_MAX)
    private String detail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
