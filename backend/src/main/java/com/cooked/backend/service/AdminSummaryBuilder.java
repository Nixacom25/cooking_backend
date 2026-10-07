package com.cooked.backend.service;

import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.ProductEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Key figures for the admin summaries, read from the same services as the backoffice. */
@Component
@RequiredArgsConstructor
public class AdminSummaryBuilder {

    public record Summary(String period, List<String[]> rows) {}

    private final AdminAnalyticsService analytics;
    private final AdminCostService costs;
    private final AdminRevenueService revenue;
    private final AdminIntegrationService integrations;

    /** Yesterday (complete day). */
    public Summary daily() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        AcquisitionResponse acq = analytics.acquisition(2);
        EngagementResponse eng = analytics.engagement(2);
        ProductAnalyticsResponse prod = analytics.product(2);
        CostOverviewResponse cost = costs.overview(2);
        RevenueSummaryResponse rev = revenue.getSummary();
        String day = yesterday.toString();

        List<String[]> rows = new ArrayList<>();
        rows.add(row("New sign-ups", acq.getSignupsDaily().stream().filter(d -> d.getDate().equals(day)).mapToLong(AcquisitionResponse.DayCount::getTotal).sum()));
        rows.add(row("Active users", eng.getDaily().stream().filter(d -> d.getDate().equals(day)).mapToLong(AcquisitionResponse.DayCount::getTotal).sum()));
        prod.getDaily().stream().filter(d -> d.getDate().equals(day)).findFirst().ifPresent(d -> {
            rows.add(new String[] {"Scans", d.getScans() + fail(d.getScanFailures())});
            rows.add(new String[] {"Imports", d.getImports() + fail(d.getImportFailures())});
            rows.add(new String[] {"Web searches", d.getSearches() + fail(d.getSearchFailures())});
        });
        double revenueDay = rev.getDaily() == null ? 0 : rev.getDaily().stream().filter(d -> d.getDate().equals(day)).mapToDouble(RevenueSummaryResponse.DailyAmount::getAmount).sum();
        rows.add(new String[] {"Revenue", money(revenueDay, rev.getCurrency())});
        rows.add(new String[] {"Active subscriptions", String.valueOf(rev.getActiveSubscriptions())});
        rows.add(new String[] {"Operating cost", money(cost.getCostYesterday(), "USD")});
        rows.add(new String[] {"Integration failures (24 h)", String.valueOf(integrations.integrations().stream().mapToLong(IntegrationStatusResponse::getFailures24h).sum())});
        return new Summary(yesterday.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), rows);
    }

    /** Last 7 days vs the 7 before. */
    public Summary weekly() {
        AcquisitionResponse acq = analytics.acquisition(7);
        EngagementResponse eng = analytics.engagement(7);
        ProductAnalyticsResponse prod = analytics.product(7);
        CostOverviewResponse cost = costs.overview(7);
        RevenueSummaryResponse rev = revenue.getSummary();

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"New sign-ups", acq.getNewUsers() + " (prev. " + acq.getNewUsersPrev() + ")"});
        rows.add(new String[] {"Weekly active users", eng.getWau() + " (prev. " + eng.getWauPrev() + ")"});
        for (ProductAnalyticsResponse.TypeStats t : prod.getSummaries()) {
            String label = t.getType() == ProductEventType.SCAN ? "Scans" : t.getType() == ProductEventType.IMPORT ? "Imports" : "Web searches";
            rows.add(new String[] {label, t.getTotal() + (t.getSuccessRate() == null ? "" : " · " + t.getSuccessRate() + "% success")});
        }
        rows.add(new String[] {"Revenue (30 days)", money(rev.getRevenue30(), rev.getCurrency())});
        rows.add(new String[] {"MRR", money(rev.getMrr(), rev.getCurrency())});
        rows.add(new String[] {"Operating cost (7 days)", money(cost.getDaily().stream().mapToDouble(CostOverviewResponse.DayAmount::getAmount).sum(), "USD")});
        rows.add(new String[] {"Cost this month / projection", money(cost.getMonthToDate(), "USD") + " / " + money(cost.getProjectedMonth(), "USD")});
        LocalDate end = LocalDate.now().minusDays(1);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        return new Summary(end.minusDays(6).format(f) + " – " + end.format(f), rows);
    }

    private static String[] row(String label, long value) {
        return new String[] {label, String.valueOf(value)};
    }

    private static String fail(long failures) {
        return failures == 0 ? "" : " (" + failures + " failed)";
    }

    static String money(Double v, String currency) {
        if (v == null) return "—";
        return String.format(Locale.US, "%,.2f %s", v, currency == null ? "" : currency).trim();
    }
}
