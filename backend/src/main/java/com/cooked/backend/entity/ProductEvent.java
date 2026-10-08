package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One product action (scan, import, web search) as observed by the backend:
 * outcome, duration and a short detail (import domain, search query).
 * Written by {@link com.cooked.backend.service.ProductEventTracker}, read only
 * through aggregate queries.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_events", indexes = {
        @Index(name = "idx_product_events_type_created", columnList = "type, createdAt")
})
public class ProductEvent {

    public static final int DETAIL_MAX = 160;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductEventType type;

    @Column(nullable = false)
    private boolean success;

    /** Server time spent handling the request. */
    private Integer durationMs;

    /** Recipes / results returned (null when not applicable). */
    private Integer resultCount;

    /** Import domain, search query (lower-cased), or scan variant. */
    @Column(length = DETAIL_MAX)
    private String detail;

    /** Short reason when {@link #success} is false. */
    @Column(length = DETAIL_MAX)
    private String failureReason;

    private UUID userId;

    /** Full target of the action (import URL), kept for failed events so an admin can retry. */
    @Column(columnDefinition = "TEXT")
    private String target;

    /** Failure handled by an admin (Import failures → Mark resolved). */
    private LocalDateTime resolvedAt;

    /** Last admin retry of a failed event, and whether it worked. */
    private LocalDateTime retriedAt;
    private Boolean retrySucceeded;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
