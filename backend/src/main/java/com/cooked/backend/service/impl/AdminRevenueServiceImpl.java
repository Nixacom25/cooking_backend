package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.RevenueSummaryResponse;
import com.cooked.backend.dto.response.RevenueSummaryResponse.DailyAmount;
import com.cooked.backend.dto.response.RevenueSummaryResponse.MonthlyAmount;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.SubscriptionPaymentRepository.LabelAmount;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AdminRevenueService;
import com.cooked.backend.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the admin revenue summary from aggregate queries (the database does
 * the sums and counts), so the cost doesn't grow with the number of payments
 * loaded in memory.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRevenueServiceImpl implements AdminRevenueService {

    static final String CURRENCY = "EUR";
    static final int DAYS = 30;
    static final int MONTHS = 8;
    private static final List<SubscriptionStatus> PAID = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PREMIUM);

    private final UserRepository userRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;

    @Override
    public RevenueSummaryResponse getSummary() {
        return summaryAt(LocalDateTime.now());
    }

    /** Same as {@link #getSummary()} at a fixed instant (tests). */
    RevenueSummaryResponse summaryAt(LocalDateTime now) {
        LocalDateTime from30 = now.minusDays(DAYS);
        LocalDateTime from60 = now.minusDays(2L * DAYS);
        LocalDate firstDay = now.toLocalDate().minusDays(DAYS - 1L);
        YearMonth firstMonth = YearMonth.from(now).minusMonths(MONTHS - 1L);

        long monthlyPlans = userRepository.countByRoleAndSubscriptionStatusInAndSubscriptionType(Role.CLIENT, PAID, SubscriptionType.MONTHLY);
        long yearlyPlans = userRepository.countByRoleAndSubscriptionStatusInAndSubscriptionType(Role.CLIENT, PAID, SubscriptionType.YEARLY);
        long cancelled = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.CANCELLED));
        long expired = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.EXPIRED));
        double mrr = monthlyRecurringRevenue(monthlyPlans, yearlyPlans, subscriptionService.getPlan());

        long everPaid = paymentRepository.countDistinctPayers();

        return RevenueSummaryResponse.builder()
                .currency(CURRENCY)
                .activeSubscriptions(userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, PAID))
                .activeTrials(userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.TRIAL)))
                .monthlyPlans(monthlyPlans)
                .yearlyPlans(yearlyPlans)
                .cancelled(cancelled)
                .expired(expired)
                .mrr(round(mrr))
                .arr(round(mrr * 12))
                .revenue30(round(toDouble(paymentRepository.sumSuccessBetween(from30, now))))
                .revenuePrev30(round(toDouble(paymentRepository.sumSuccessBetween(from60, from30))))
                .payments30(paymentRepository.countSuccessSince(from30))
                .renewals30(paymentRepository.countRenewalsSince(from30))
                .billingFailures30(paymentRepository.countByStatusKeywordSince("FAIL", from30))
                .refunds30(paymentRepository.countByStatusKeywordSince("REFUND", from30))
                .churnRate(everPaid > 0 ? round(100.0 * (cancelled + expired) / everPaid) : null)
                .ltv(everPaid > 0 ? round(toDouble(paymentRepository.sumSuccess()) / everPaid) : null)
                .byStore(sortedByAmount(paymentRepository.sumSuccessByStoreSince(from30), label -> label))
                .byPlan(sortedByAmount(paymentRepository.sumSuccessByPlanTypeSince(from30), AdminRevenueServiceImpl::planLabel))
                .daily(dailySeries(firstDay, paymentRepository.sumSuccessByDaySince(firstDay.atStartOfDay())))
                .monthly(monthlySeries(firstMonth, paymentRepository.sumSuccessByMonthSince(firstMonth.atDay(1).atStartOfDay())))
                .build();
    }

    static double monthlyRecurringRevenue(long monthlyPlans, long yearlyPlans, SubscriptionPlan plan) {
        double monthly = plan != null && plan.getMonthlyPrice() != null ? plan.getMonthlyPrice().doubleValue() : 0;
        double yearly = plan != null && plan.getYearlyPrice() != null ? plan.getYearlyPrice().doubleValue() : 0;
        return monthlyPlans * monthly + yearlyPlans * yearly / 12.0;
    }

    /** Groups raw labels (merging those that map to the same display label), highest amount first. */
    static Map<String, Double> sortedByAmount(List<LabelAmount> rows, java.util.function.Function<String, String> label) {
        Map<String, Double> merged = new HashMap<>();
        for (LabelAmount row : rows) {
            merged.merge(label.apply(row.getLabel()), toDouble(row.getAmount()), Double::sum);
        }
        Map<String, Double> out = new LinkedHashMap<>();
        merged.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> out.put(e.getKey(), round(e.getValue())));
        return out;
    }

    static List<DailyAmount> dailySeries(LocalDate firstDay, List<SubscriptionPaymentRepository.DayAmount> rows) {
        Map<LocalDate, Double> byDay = new HashMap<>();
        rows.forEach(r -> byDay.merge(r.getDay(), toDouble(r.getAmount()), Double::sum));
        List<DailyAmount> out = new ArrayList<>(DAYS);
        for (int i = 0; i < DAYS; i++) {
            LocalDate d = firstDay.plusDays(i);
            out.add(new DailyAmount(d.toString(), round(byDay.getOrDefault(d, 0.0))));
        }
        return out;
    }

    static List<MonthlyAmount> monthlySeries(YearMonth firstMonth, List<SubscriptionPaymentRepository.MonthAmount> rows) {
        Map<YearMonth, Double> byMonth = new HashMap<>();
        rows.forEach(r -> byMonth.merge(YearMonth.of(r.getYear(), r.getMonth()), toDouble(r.getAmount()), Double::sum));
        List<MonthlyAmount> out = new ArrayList<>(MONTHS);
        for (int i = 0; i < MONTHS; i++) {
            YearMonth m = firstMonth.plusMonths(i);
            out.add(new MonthlyAmount(m.toString(), round(byMonth.getOrDefault(m, 0.0))));
        }
        return out;
    }

    static String planLabel(String planType) {
        if (planType == null) return "Other";
        String t = planType.toUpperCase();
        if (t.startsWith("GIFT")) return "Gift";
        if (t.contains("YEAR") || t.contains("ANNUAL")) return "Annual";
        if (t.contains("MONTH")) return "Monthly";
        return "Other";
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
