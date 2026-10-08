package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Sign-ups over the last {@link #days} days and the "how did you hear about Cooked?" answers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquisitionResponse {
    private int days;
    private long newUsers;
    private long newUsersPrev;
    private List<DayCount> signupsDaily;
    private List<SourceCount> bySource;
    /** Distinct website visitors (page views sent by cookedapp.com). */
    private long visitors;
    private long visitorsPrev;
    /** Sign-ups of the window currently on trial. */
    private long trials;
    /** Users whose first successful payment is in the window. */
    private long paid;
    private long paidPrev;
    /** Ad spend (Cost Center, category Advertising) and payments of the window, in USD. */
    private double adSpendUsd;
    private double revenueUsd;
    /** adSpend / paid (null without ad spend or payers). */
    private Double cac;
    /** revenue / adSpend (null without ad spend). */
    private Double roas;
    /** Website visitors per referrer host ("direct" when none). */
    private List<SourceCount> visitorsByReferrer;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class DayCount {
        private String date;
        private long total;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class SourceCount {
        private String source;
        private long total;
    }
}
