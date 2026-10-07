package com.cooked.backend.service.automation;

import com.cooked.backend.entity.EmailTemplate;

import java.util.List;

/**
 * Every automation the backend runs: scheduled jobs (runs come from
 * automation_runs) and event-triggered flows (runs come from the emails they
 * send). Add an entry when adding a job or a triggered flow.
 */
public final class AutomationCatalog {

    public enum Kind { SCHEDULED, EVENT }

    public record Automation(String key, String name, Kind kind, String trigger, List<String> actions,
                             String job, EmailTemplate email, String note) {}

    private AutomationCatalog() {}

    public static final List<Automation> ALL = List.of(
            new Automation("payment_failed", "Payment failed", Kind.EVENT, "RevenueCat BILLING_ISSUE webhook",
                    List.of("Flag the subscription's billing issue", "Send the payment recovery email"), null, EmailTemplate.PAYMENT_FAILED, null),
            new Automation("trial_reminder", "Trial ending reminder", Kind.SCHEDULED, "Every hour at :15",
                    List.of("Find trials ending in 23–25 h", "Send the trial ending email (once per user)"), "TrialReminderService.sendTrialEndingReminders", EmailTemplate.TRIAL_ENDS_TOMORROW, null),
            new Automation("critical_error", "Critical crash detected", Kind.EVENT, "Critical error reported by the app",
                    List.of("Store the error", "Email the support team (Settings)", "Post to Slack (if connected)"), null, EmailTemplate.CRITICAL_ERROR_ALERT, null),
            new Automation("expired_subscriptions", "Expire ended subscriptions", Kind.SCHEDULED, "Daily at 00:00",
                    List.of("Find subscriptions past their end date", "Mark them expired"), "SubscriptionServiceImpl.processExpiredSubscriptions", null, null),
            new Automation("subscription_sync", "Store subscription sync", Kind.SCHEDULED, "Daily at 02:00",
                    List.of("Re-check active subscriptions with the stores", "Update statuses"), "SubscriptionServiceImpl.syncAllActiveSubscriptions", null, null),
            new Automation("recipe_cleanup", "Expired suggestions cleanup", Kind.SCHEDULED, "Daily at 01:00",
                    List.of("Delete expired suggested recipes"), "RecipeCleanupService.cleanupExpiredSuggestions", null, null),
            new Automation("trending_dishes", "Daily trending dishes", Kind.SCHEDULED, "Daily at 00:00",
                    List.of("Ask the AI service for trending dishes", "Replace the trending list shown in search"), "TrendingService.generateDailyTrendingDishes", null, null),
            new Automation("ingredient_pricing", "Ingredient prices update", Kind.SCHEDULED, "Daily at 00:00",
                    List.of("Ask the AI service for current prices", "Update ingredient prices"), "IngredientPricingJob.scheduledPricingUpdate", null, null),
            new Automation("billing_sync", "Provider billing sync", Kind.SCHEDULED, "Daily at 04:30 UTC",
                    List.of("Pull OpenAI spend (when OPENAI_ADMIN_KEY is set)", "Update the Cost Center"), "ProviderBillingSyncJob.syncNightly", null, null),
            new Automation("admin_summaries", "Admin daily summary & weekly review", Kind.SCHEDULED, "Hourly check · sends at 8 AM (Settings timezone)",
                    List.of("Collect yesterday's / last week's key figures", "Email every admin", "Post to Slack (if connected)"), "AdminSummaryJob.sendDueSummaries", EmailTemplate.ADMIN_SUMMARY,
                    "Turn on in Settings → Your alerts."),
            new Automation("drip_funnel", "Free users drip", Kind.SCHEDULED, "Daily at 10:00",
                    List.of("Free users who signed up 3 / 7 days ago", "Push (or email without a push token), once per step"), "DripFunnelService.processDripCampaigns", EmailTemplate.DRIP,
                    "Off until turned on in Settings → Your alerts."),
            new Automation("scheduled_campaigns", "Scheduled push campaigns", Kind.SCHEDULED, "Every minute",
                    List.of("Send push campaigns whose time has come"), null, null,
                    "Polls every minute; runs are not logged (see Notifications for sends).")
    );
}
