package com.cooked.backend.entity;

/** External services Cooked talks to (labels and descriptions shown in the backoffice). */
public enum IntegrationKey {
    REVENUECAT("RevenueCat", "Subscriptions, trials, entitlements, webhooks"),
    STRIPE("Stripe", "Gift purchases on the website"),
    APPLE("App Store", "Server notifications for iOS purchases"),
    GOOGLE_PLAY("Google Play", "Real-time developer notifications for Android purchases"),
    BREVO("Brevo", "Transactional emails"),
    OPENAI("OpenAI", "Image ranking, image generation, billing sync"),
    MARKHOR("Markhor AI", "Scan, import, web search and recipe generation"),
    FIREBASE("Firebase", "Push notifications (FCM)"),
    CLOUDINARY("Cloudinary", "Image storage and CDN"),
    SLACK("Slack", "Critical alerts and admin summaries (Settings)");

    private final String label;
    private final String description;

    IntegrationKey(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }

    public String getDescription() { return description; }
}
