package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftRedeemResponse {
    /** GIFT (premium unlocked) or AMBASSADOR (referral code applied, nothing unlocked). */
    @lombok.Builder.Default
    private String kind = "GIFT";
    /** For AMBASSADOR: who referred the user. */
    private String ambassadorName;
    private String planLabel;
    private int months;
    private LocalDateTime premiumUntil;
}
