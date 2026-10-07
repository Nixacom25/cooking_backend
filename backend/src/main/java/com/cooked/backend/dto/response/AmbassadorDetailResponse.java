package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmbassadorDetailResponse {
    private AmbassadorResponse ambassador;
    private List<AcquisitionResponse.DayCount> dailyClicks;
    private List<AcquisitionResponse.DayCount> dailyClicksPrev;
    private List<Payout> payouts;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Payout {
        /** "2026-10". */
        private String month;
        private long paidSubscribers;
        private double revenue;
        private double commission;
        /** PAID (marked), DUE (month over, not paid) or IN_PROGRESS (current month). */
        private String status;
        private LocalDateTime paidAt;
    }
}
