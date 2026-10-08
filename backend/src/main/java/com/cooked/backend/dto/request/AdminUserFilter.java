package com.cooked.backend.dto.request;

/**
 * Filters of the admin Users table. Every field is optional (null = no filter).
 *
 * @param q            name, email, user id or RevenueCat id (contains, case-insensitive)
 * @param platform     IOS, ANDROID, APP (mobile app, OS not reported) or WEB (any login session from that platform)
 * @param subscription a SubscriptionStatus name (FREE, TRIAL, ACTIVE…)
 * @param signupDays   signed up in the last N days
 * @param source       discovery source, as answered in onboarding
 * @param status       an account Status name (ACTIVE, BLOCKED…)
 * @param trial        true = on trial only
 * @param partner      true = creators and ambassadors instead of regular clients
 * @param version      app version last reported (exact, e.g. "1.0.5+107")
 * @param country      ISO country last reported by the app
 */
public record AdminUserFilter(String q, String platform, String subscription, Integer signupDays,
                              String source, String status, Boolean trial, Boolean partner, String version, String country) {

    public AdminUserFilter(String q, String platform, String subscription, Integer signupDays,
                           String source, String status, Boolean trial, Boolean partner) {
        this(q, platform, subscription, signupDays, source, status, trial, partner, null, null);
    }
}
