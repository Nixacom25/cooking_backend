package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmbassadorsResponse {
    private int days;
    private Totals totals;
    private Totals totalsPrev;
    /** Commission of finished months not marked as paid yet. */
    private double pendingPayout;
    private BigDecimal defaultCommissionPercent;
    private List<AmbassadorResponse> ambassadors;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Totals {
        private long clicks;
        private long signups;
        private long trials;
        private long paid;
        private Double conversion;
        private double revenue;
        private double commission;
    }
}
