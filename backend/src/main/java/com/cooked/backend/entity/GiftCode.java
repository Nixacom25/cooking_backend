package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gift_codes", indexes = {
        @Index(name = "idx_gift_codes_purchaser", columnList = "purchaser_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GiftPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GiftCodeStatus status = GiftCodeStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchaser_id", nullable = false)
    private User purchaser;

    /** Idempotency key from the store event (RevenueCat event id). */
    @Column(name = "purchase_ref", nullable = false, unique = true)
    private String purchaseRef;

    /** Store transaction id, used to void the code on refund. */
    @Column(name = "transaction_id")
    private String transactionId;

    private String store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redeemed_by_id")
    private User redeemedBy;

    private LocalDateTime redeemedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
