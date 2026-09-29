package com.cooked.backend.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Gift cards sold in the app as consumable in-app purchases (Apple / Google
 * via RevenueCat). Each purchase produces one single-use gift code.
 */
public enum GiftPlan {
    THREE_MONTHS("gift_3_months", "three_month", 3, "3 Months"),
    ONE_YEAR("gift_1_year", "yearly", 12, "1 Year");

    /** Store product id (App Store Connect + Google Play Console). */
    private final String productId;
    /** RevenueCat promotional entitlement duration. */
    private final String revenueCatDuration;
    private final int months;
    private final String label;

    GiftPlan(String productId, String revenueCatDuration, int months, String label) {
        this.productId = productId;
        this.revenueCatDuration = revenueCatDuration;
        this.months = months;
        this.label = label;
    }

    public String getProductId() { return productId; }
    public String getRevenueCatDuration() { return revenueCatDuration; }
    public int getMonths() { return months; }
    public String getLabel() { return label; }

    public static Optional<GiftPlan> fromProductId(String productId) {
        if (productId == null) return Optional.empty();
        // Google product ids can come back as "gift_1_year:base-plan".
        String base = productId.split(":")[0];
        return Arrays.stream(values()).filter(p -> p.productId.equals(base)).findFirst();
    }
}
