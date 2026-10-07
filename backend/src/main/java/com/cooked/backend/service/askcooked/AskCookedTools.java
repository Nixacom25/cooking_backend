package com.cooked.backend.service.askcooked;

import com.cooked.backend.dto.response.ProductFailuresResponse;
import com.cooked.backend.dto.response.StagiaireLeaderboardResponse;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The data Ask Cooked can read: thin adapters over the existing admin services (read-only). */
@Configuration
public class AskCookedTools {

    record SimpleTool(String name, String description, String sourceLabel, Map<String, Object> parameters,
                      Function<JsonNode, Object> fn) implements AskCookedTool {
        @Override
        public Object run(JsonNode arguments) {
            return fn.apply(arguments);
        }
    }

    @Bean
    AskCookedTool productUsageTool(AdminAnalyticsService analytics) {
        return new SimpleTool("product_usage",
                "Scans, imports and web searches: totals, success rates, durations, daily counts, import sources, top and zero-result searches, failure reasons, recipes created from scans/imports.",
                "Product analytics", AskCookedTool.DAYS_PARAMETERS, a -> analytics.product(AskCookedTool.days(a)));
    }

    @Bean
    AskCookedTool acquisitionTool(AdminAnalyticsService analytics) {
        return new SimpleTool("acquisition", "New sign-ups (current vs previous period), daily sign-ups and declared discovery sources.",
                "Acquisition", AskCookedTool.DAYS_PARAMETERS, a -> analytics.acquisition(AskCookedTool.days(a)));
    }

    @Bean
    AskCookedTool activeUsersTool(AdminAnalyticsService analytics) {
        return new SimpleTool("active_users", "Active users (app opened): DAU average, WAU, MAU with previous windows, daily active users, total client accounts.",
                "Active users", AskCookedTool.DAYS_PARAMETERS, a -> analytics.engagement(AskCookedTool.days(a)));
    }

    @Bean
    AskCookedTool importFailuresTool(AdminAnalyticsService analytics) {
        return new SimpleTool("import_failures", "The 50 most recent failed recipe imports: source domain, failure reason, duration, time (no user identity).",
                "Failed imports", AskCookedTool.DAYS_PARAMETERS, a -> {
                    ProductFailuresResponse r = analytics.failures(ProductEventType.IMPORT, AskCookedTool.days(a), 0, 50);
                    r.getItems().forEach(i -> { i.setUserId(null); i.setUserName(null); i.setUserEmail(null); });
                    return r;
                });
    }

    @Bean
    AskCookedTool costsTool(AdminCostService costs) {
        return new SimpleTool("costs",
                "Operating costs in USD: today, month to date, projection, cost per active user / subscriber, daily spend, categories, providers with budgets, credits and alerts.",
                "Cost Center", AskCookedTool.DAYS_PARAMETERS, a -> costs.overview(AskCookedTool.days(a)));
    }

    @Bean
    AskCookedTool revenueTool(AdminRevenueService revenue) {
        return new SimpleTool("revenue", "Subscriptions and revenue: MRR, ARR, churn, LTV, active subscriptions and trials, revenue last 30 days vs previous, by store and plan, daily and monthly series.",
                "Revenue", AskCookedTool.NO_PARAMETERS, a -> revenue.getSummary(30));
    }

    @Bean
    AskCookedTool trendsTool(AdminTrendsService trends) {
        return new SimpleTool("trends", "Rising Cooked searches, ingredients in scanned recipes, most-saved recipes and growing categories vs the previous period; today's trending dishes.",
                "Trend intelligence", AskCookedTool.DAYS_PARAMETERS, a -> trends.trends(AskCookedTool.days(a)));
    }

    @Bean
    AskCookedTool integrationsTool(AdminIntegrationService integrations) {
        return new SimpleTool("integrations", "External services (RevenueCat, Stripe, App Store, Google Play, Brevo, OpenAI, Markhor AI…): configured or not, 24h/30d activity and failures.",
                "Integrations", AskCookedTool.NO_PARAMETERS, a -> integrations.integrations());
    }

    @Bean
    AskCookedTool emailsTool(AdminIntegrationService integrations) {
        return new SimpleTool("emails", "Emails sent by the backend: totals, failures, per template, and Brevo delivery / open / click / spam rates.",
                "Email", AskCookedTool.DAYS_PARAMETERS, a -> integrations.emails(AskCookedTool.days(a)));
    }

    @Bean
    AskCookedTool recipeOperationsTool(RecipeAssignmentService assignments) {
        return new SimpleTool("recipe_operations",
                "Recipe operations: library totals, assigned / pending validation / validated / sent back for correction, processed today, and each data intern's assigned, validated, returned-for-correction counts and validation rate.",
                "Recipe operations", AskCookedTool.NO_PARAMETERS, a -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("stats", assignments.getRecipeStats());
                    List<Map<String, Object>> interns = assignments.getStagiairesLeaderboard().stream().map(AskCookedTools::intern).toList();
                    out.put("interns", interns);
                    return out;
                });
    }

    private static Map<String, Object> intern(StagiaireLeaderboardResponse s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", ((s.getFirstname() == null ? "" : s.getFirstname()) + " " + (s.getLastname() == null ? "" : s.getLastname())).trim());
        m.put("assigned", s.getTotalAssigned());
        m.put("validated", s.getTotalValidated());
        m.put("pendingValidation", s.getTotalPendingValidation());
        m.put("returnedForCorrection", s.getTotalReturnedForCorrection());
        m.put("remaining", s.getTotalRemaining());
        m.put("validationRate", s.getValidationRate());
        return m;
    }
}
