package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A month marked as paid to an ambassador (amounts frozen when marked). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ambassador_payouts", uniqueConstraints = @UniqueConstraint(name = "uk_ambassador_payout", columnNames = {"ambassador_id", "month"}))
public class AmbassadorPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID ambassadorId;

    /** "2026-10". */
    @Column(nullable = false, length = 7)
    private String month;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal revenue;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal commission;

    @Column(nullable = false)
    private long paidSubscribers;

    @Column(length = 120)
    private String paidBy;

    @Column(nullable = false)
    private LocalDateTime paidAt;
}
