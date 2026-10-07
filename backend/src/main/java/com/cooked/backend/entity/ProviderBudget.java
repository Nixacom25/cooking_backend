package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Monthly budget and prepaid credit balance of a provider (entered by an admin). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "provider_budgets", uniqueConstraints = @UniqueConstraint(name = "uk_provider_budget", columnNames = "provider"))
public class ProviderBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private CostCategory category;

    /** USD per month, null when no budget is set. */
    @Column(precision = 12, scale = 2)
    private BigDecimal monthlyBudgetUsd;

    /** Prepaid balance, in {@link #creditUnit} ("USD" enables the exhaustion forecast). */
    @Column(precision = 14, scale = 2)
    private BigDecimal creditBalance;

    @Column(length = 40)
    private String creditUnit;

    private LocalDateTime creditUpdatedAt;
}
