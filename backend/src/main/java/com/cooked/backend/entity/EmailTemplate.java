package com.cooked.backend.entity;

/** Emails the backend sends, with what triggers them (shown on the Email screen). */
public enum EmailTemplate {
    OTP("Verification code", "Sign-up or password reset", false),
    ACCOUNT_UPDATE("Account security update", "Password or email changed", false),
    WELCOME("Welcome", "Account created", true),
    ACCOUNT_DELETED("Account deleted", "User deleted their account", false),
    NEW_DEVICE_SIGN_IN("New sign-in", "Sign-in from a new device", false),
    SUPPORT_RECEIVED("Support request received", "User opened a support ticket", false),
    SUPPORT_TEAM_NOTIFICATION("Support request → team", "User opened a support ticket", false),
    PAYMENT_FAILED("Payment failed recovery", "RevenueCat BILLING_ISSUE webhook", true),
    GIFT_CODE("Gift code", "Gift purchased for oneself", false),
    GIFT_RECEIPT("Gift receipt", "Gift purchased on the website", false),
    GIFT_RECEIVED("Gift received", "Gift sent to a friend", false),
    TRIAL_ENDS_TOMORROW("Trial ending reminder", "Trial ends in 1 day (hourly job)", true),
    CRITICAL_ERROR_ALERT("Critical error alert", "Critical crash reported by the app", false),
    ADMIN_SUMMARY("Admin summary", "Daily 8 AM summary / Monday review (Settings)", false),
    DRIP("Free users drip", "Free user at day 3 / day 7 without a push token (Settings)", true),
    CREATOR_DECISION("Creator application decision", "Application approved, rejected or more info requested", false),
    SUPPORT_REPLY("Support reply", "Admin answered a support ticket", false),
    TEAM_INVITE("Team invitation", "Admin invited a team member", false);

    private final String label;
    private final String trigger;
    /** Lifecycle email (as opposed to account / security / internal ones). */
    private final boolean automation;

    EmailTemplate(String label, String trigger, boolean automation) {
        this.label = label;
        this.trigger = trigger;
        this.automation = automation;
    }

    public String getLabel() { return label; }

    public String getTrigger() { return trigger; }

    public boolean isAutomation() { return automation; }
}
