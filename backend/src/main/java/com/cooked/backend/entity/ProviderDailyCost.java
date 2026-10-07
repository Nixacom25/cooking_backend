package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Daily spend imported from a provider's billing API (e.g. OpenAI costs), per line item. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "provider_daily_costs", indexes = @Index(name = "idx_provider_daily_cost", columnList = "provider, costDay"))
public class ProviderDailyCost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String provider;

    @Column(nullable = false)
    private LocalDate costDay;

    /** Model / SKU as reported by the provider ("gpt-4o-mini, input"). */
    @Column(nullable = false, length = 160)
    private String lineItem;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal amountUsd;
}
