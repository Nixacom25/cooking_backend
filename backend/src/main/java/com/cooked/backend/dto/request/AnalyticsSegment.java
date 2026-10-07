package com.cooked.backend.dto.request;

/**
 * User segment for the analytics screens (all optional, same values as the Users filters).
 *
 * @param platform     IOS, ANDROID or WEB
 * @param subscription a SubscriptionStatus name
 * @param source       onboarding discovery source
 */
public record AnalyticsSegment(String platform, String subscription, String source) {

    public static final AnalyticsSegment ALL = new AnalyticsSegment(null, null, null);

    public boolean all() {
        return blank(platform) && blank(subscription) && blank(source);
    }

    public AdminUserFilter toUserFilter() {
        return new AdminUserFilter(null, platform, subscription, null, source, null, null, null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
