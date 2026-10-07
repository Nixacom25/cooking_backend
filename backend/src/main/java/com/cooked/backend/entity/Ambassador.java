package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A partner with a referral code entered by new users in the app (onboarding). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ambassadors", uniqueConstraints = @UniqueConstraint(name = "uk_ambassador_code", columnNames = "code"))
public class Ambassador {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String email;

    /** Upper-case, letters and digits, 3-20 chars. */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 40)
    private String platform;

    @Column(length = 80)
    private String handle;

    @Column(length = 40)
    private String audience;

    /** % of the revenue from referred users paid as commission. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AmbassadorStatus status;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
