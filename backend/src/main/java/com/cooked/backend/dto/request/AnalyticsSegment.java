package com.cooked.backend.dto.request;

/**
 * User segment for the analytics screens (all optional, same values as the Users filters).
 *
 * @param platform     IOS, ANDROID or WEB
 * @param subscription a SubscriptionStatus name
 * @param source       onboarding discovery source
 */
public record AnalyticsSegment(String platform, String subscription, String source, String version, String country) {

    public static final AnalyticsSegment ALL = new AnalyticsSegment(null, null, null);

    public AnalyticsSegment(String platform, String subscription, String source) {
        this(platform, subscription, source, null, null);
    }

    public boolean all() {
        return blank(platform) && blank(subscription) && blank(source) && blank(version) && blank(country);
    }

    public AdminUserFilter toUserFilter() {
        return new AdminUserFilter(null, platform, subscription, null, source, null, null, null, version, country);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
