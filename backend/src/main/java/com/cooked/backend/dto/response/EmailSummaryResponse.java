package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Emails sent by the backend through Brevo over the last {@link #days} days. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSummaryResponse {
    private int days;
    private String provider;
    private boolean configured;
    private long sent;
    private long sentPrev;
    private long failed;
    /** Accepted by the provider, 0-100 (null without sends). */
    private Double acceptedRate;
    private Double acceptedRatePrev;
    private LocalDateTime trackingSince;
    private List<TemplateStats> templates;
    /** Provider statistics (delivery, opens, clicks, spam) for the period / previous one; null when unavailable. */
    private EmailProviderStats providerStats;
    private EmailProviderStats providerStatsPrev;

    @Data @Builder @AllArgsConstructor @NoArgsConstructor
    public static class TemplateStats {
        private String template;
        private String label;
        private String trigger;
        private boolean automation;
        private long sent;
        private long failed;
        private LocalDateTime lastSentAt;
        /** Provider statistics for this template's tag (emails sent since tagging started). */
        private EmailProviderStats providerStats;
    }
}
