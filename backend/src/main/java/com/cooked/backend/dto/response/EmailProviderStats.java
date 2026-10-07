package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated delivery statistics reported by the email provider for a period (and optionally a tag). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailProviderStats {
    private long requests;
    private long delivered;
    private long uniqueOpens;
    private long uniqueClicks;
    private long hardBounces;
    private long softBounces;
    private long blocked;
    private long spamReports;
    private long unsubscribed;

    /** % of delivered emails, null when nothing was delivered. */
    public Double rate(long count) {
        return delivered == 0 ? null : Math.round(1000.0 * count / delivered) / 10.0;
    }

    public Double getDeliveryRate() {
        return requests == 0 ? null : Math.round(1000.0 * delivered / requests) / 10.0;
    }

    public Double getOpenRate() { return rate(uniqueOpens); }

    public Double getClickRate() { return rate(uniqueClicks); }

    public Double getSpamRate() { return rate(spamReports); }
}
