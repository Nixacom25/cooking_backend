package com.cooked.backend.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Gift plans. Each purchase produces one single-use COOK- code.
 *
 * Web prices ({@link #webPriceCents}) are the only prices the server charges
 * through Stripe - the website only sends a plan name, never an amount.
 * They mirror the in-app subscription prices (monthly $9.99, yearly $29.99).
 */
public enum GiftPlan {
    ONE_MONTH("gift_1_month", "monthly", 1, "1 Month", 999),
    /** Legacy (in-app consumable), no longer sold on the web. */
    THREE_MONTHS("gift_3_months", "three_month", 3, "3 Months", null),
    ONE_YEAR("gift_1_year", "yearly", 12, "1 Year", 2999);

    /** Store product id (in-app purchases). */
    private final String productId;
    /** RevenueCat promotional entitlement duration. */
    private final String revenueCatDuration;
    private final int months;
    private final String label;
    /** USD cents charged on the website, or null when not sold there. */
    private final Integer webPriceCents;

    GiftPlan(String productId, String revenueCatDuration, int months, String label, Integer webPriceCents) {
        this.productId = productId;
        this.revenueCatDuration = revenueCatDuration;
        this.months = months;
        this.label = label;
        this.webPriceCents = webPriceCents;
    }

    public String getProductId() { return productId; }
    public String getRevenueCatDuration() { return revenueCatDuration; }
    public int getMonths() { return months; }
    public String getLabel() { return label; }
    public Integer getWebPriceCents() { return webPriceCents; }
    public boolean isSoldOnWeb() { return webPriceCents != null; }

    public static Optional<GiftPlan> fromProductId(String productId) {
        if (productId == null) return Optional.empty();
        // Google product ids can come back as "gift_1_year:base-plan".
        String base = productId.split(":")[0];
        return Arrays.stream(values()).filter(p -> p.productId.equals(base)).findFirst();
    }

    /** Plan chosen on the website ("ONE_MONTH" / "ONE_YEAR"), only if sold there. */
    public static Optional<GiftPlan> webPlan(String name) {
        if (name == null) return Optional.empty();
        try {
            GiftPlan plan = GiftPlan.valueOf(name.trim().toUpperCase());
            return plan.isSoldOnWeb() ? Optional.of(plan) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
