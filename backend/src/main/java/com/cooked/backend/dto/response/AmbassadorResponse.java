package com.cooked.backend.dto.response;

import com.cooked.backend.entity.AmbassadorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** An ambassador and its attribution over the requested period. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmbassadorResponse {
    private UUID id;
    private String name;
    private String email;
    private String code;
    private String platform;
    private String handle;
    private String audience;
    private BigDecimal commissionPercent;
    private AmbassadorStatus status;
    private String notes;
    private LocalDateTime since;
    /** Shareable link (counts clicks, then opens the website). */
    private String link;
    private long clicks;
    /** Users who entered the code during onboarding. */
    private long signups;
    /** Of those, currently on a trial / paying. */
    private long trials;
    private long paid;
    /** Paid / sign-ups, 0-100 (null without sign-ups). */
    private Double conversion;
    private double revenue;
    private double commission;
}
