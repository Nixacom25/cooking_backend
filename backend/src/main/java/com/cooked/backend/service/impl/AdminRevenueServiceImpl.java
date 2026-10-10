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

    /** Store prices reach us in USD (RevenueCat converts them), so revenue is reported in dollars. */
    static final String CURRENCY = "USD";
    static final int DAYS = 30;
    static final int MAX_DAYS = 365;
    static final int MAX_SERIES_DAYS = 90;
    static final int MONTHS = 8;
    private static final List<SubscriptionStatus> PAID = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PREMIUM);

    private final UserRepository userRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final com.cooked.backend.service.ActivityLogService activityLogService;

    @Override
    public RevenueSummaryResponse getSummary(int days) {
        return summaryAt(LocalDateTime.now(), days);
    }

    /** 30-day summary at a fixed instant (tests). */
    RevenueSummaryResponse summaryAt(LocalDateTime now) {
        return summaryAt(now, DAYS);
    }

    /** Same as {@link #getSummary(int)} at a fixed instant (tests). */
    RevenueSummaryResponse summaryAt(LocalDateTime now, int requestedDays) {
        int days = clampDays(requestedDays);
        int seriesDays = Math.min(Math.max(days, 7), MAX_SERIES_DAYS);
        LocalDateTime from30 = now.minusDays(days);
        LocalDateTime from60 = now.minusDays(2L * days);
        LocalDate firstDay = now.toLocalDate().minusDays(seriesDays - 1L);
        YearMonth firstMonth = YearMonth.from(now).minusMonths(MONTHS - 1L);

        long monthlyPlans = userRepository.countByRoleAndSubscriptionStatusInAndSubscriptionType(Role.CLIENT, PAID, SubscriptionType.MONTHLY);
        long yearlyPlans = userRepository.countByRoleAndSubscriptionStatusInAndSubscriptionType(Role.CLIENT, PAID, SubscriptionType.YEARLY);
        long cancelled = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.CANCELLED));
        long expired = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.EXPIRED));
        double mrr = monthlyRecurringRevenue(monthlyPlans, yearlyPlans, subscriptionService.getPlan());

        long everPaid = paymentRepository.countDistinctPayers();

        return RevenueSummaryResponse.builder()
                .currency(CURRENCY)
                .periodDays(days)
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
                .daily(dailySeries(firstDay, seriesDays, paymentRepository.sumSuccessByDaySince(firstDay.atStartOfDay())))
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

    static int clampDays(int days) {
        return Math.min(Math.max(days, 1), MAX_DAYS);
    }

    static List<DailyAmount> dailySeries(LocalDate firstDay, List<SubscriptionPaymentRepository.DayAmount> rows) {
        return dailySeries(firstDay, DAYS, rows);
    }

    static List<DailyAmount> dailySeries(LocalDate firstDay, int length, List<SubscriptionPaymentRepository.DayAmount> rows) {
        Map<LocalDate, Double> byDay = new HashMap<>();
        rows.forEach(r -> byDay.merge(r.getDay(), toDouble(r.getAmount()), Double::sum));
        List<DailyAmount> out = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
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

    @Override
    @Transactional
    public com.cooked.backend.dto.response.PaymentStatusResponse setPaymentStatus(java.util.UUID id, String status, String adminEmail) {
        return setPaymentStatus(id, status, null, adminEmail);
    }

    @Override
    @Transactional
    public com.cooked.backend.dto.response.PaymentStatusResponse setPaymentStatus(java.util.UUID id, String status, String reason, String adminEmail) {
        com.cooked.backend.entity.SubscriptionPayment p = paymentRepository.findById(id)
                .orElseThrow(() -> new com.cooked.backend.exception.ResourceNotFoundException("Payment not found"));
        String previous = p.getStatus();
        p.setStatus(status);
        paymentRepository.save(p);
        org.slf4j.LoggerFactory.getLogger(AdminRevenueServiceImpl.class)
                .info("Payment {} status {} -> {} by {}", id, previous, status, adminEmail);
        // Audit trail: who changed which payment, from what to what, and why.
        userRepository.findByEmail(adminEmail).ifPresent(admin -> {
            String customer = p.getUser() != null ? p.getUser().getEmail() : "unknown customer";
            String why = reason == null || reason.isBlank() ? "" : " · Reason: " + reason.trim();
            activityLogService.logActivity(admin, "Payment status changed",
                    previous + " → " + status + " · " + p.getAmount() + " " + CURRENCY + " · " + customer + why, id, "PAYMENT");
        });
        return new com.cooked.backend.dto.response.PaymentStatusResponse(id, previous, status);
    }

    @Override
    public com.cooked.backend.dto.response.PaymentListResponse listPayments(int days, LocalDate from, LocalDate to,
            String store, String plan, String status, String q, int page, int size) {
        LocalDateTime start;
        LocalDateTime end;
        if (from != null) {
            start = from.atStartOfDay();
            end = (to != null && !to.isBefore(from) ? to : from).plusDays(1).atStartOfDay();
        } else {
            int d = Math.max(1, Math.min(days, MAX_DAYS));
            end = LocalDateTime.now();
            start = end.minusDays(d);
        }
        java.time.Duration span = java.time.Duration.between(start, end);
        String st = code(store, "ALL", "APPLE", "GOOGLE", "WEB");
        String pl = plan == null || plan.isBlank() ? "ALL" : plan.trim().toUpperCase();
        String stat = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();
        String like = q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase() + "%";

        org.springframework.data.domain.Page<com.cooked.backend.entity.SubscriptionPayment> result = paymentRepository.searchPayments(
                start, end, st, pl, like, stat,
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)),
                        org.springframework.data.domain.Sort.by("createdAt").descending()));
        List<com.cooked.backend.dto.response.PaymentListResponse.Row> rows = result.getContent().stream().map(p -> {
            com.cooked.backend.entity.User u = p.getUser();
            String name = u == null ? null : (String.join(" ",
                    u.getFirstname() == null ? "" : u.getFirstname(), u.getLastname() == null ? "" : u.getLastname())).trim();
            return new com.cooked.backend.dto.response.PaymentListResponse.Row(p.getId(), p.getCreatedAt(),
                    u == null ? null : u.getId(), name == null || name.isEmpty() ? null : name, u == null ? null : u.getEmail(),
                    p.getStore(), p.getPlanType(), p.getAmount(), p.getStatus() == null ? null : p.getStatus().toUpperCase(),
                    p.getStripePaymentId());
        }).toList();

        Map<String, SubscriptionPaymentRepository.StatusTotal> now = byLabel(paymentRepository.totalsByStatus(start, end, st, pl, like));
        Map<String, SubscriptionPaymentRepository.StatusTotal> prev = byLabel(paymentRepository.totalsByStatus(start.minus(span), start, st, pl, like));
        Map<String, Long> counts = new LinkedHashMap<>();
        long all = 0;
        for (String s : List.of("SUCCESS", "SANDBOX", "DUPLICATE", "REFUNDED", "FAILED")) counts.put(s, count(now, s));
        for (SubscriptionPaymentRepository.StatusTotal t : now.values()) all += t.getCount() == null ? 0 : t.getCount();
        counts.put("ALL", all);

        return new com.cooked.backend.dto.response.PaymentListResponse(rows, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), counts,
                amount(now, "SUCCESS"), amount(prev, "SUCCESS"), count(now, "SUCCESS"), count(prev, "SUCCESS"),
                amount(now, "REFUNDED"), count(now, "REFUNDED"), amount(prev, "REFUNDED"), count(now, "SANDBOX"), CURRENCY);
    }

    private static String code(String v, String... allowed) {
        String up = v == null ? "" : v.trim().toUpperCase();
        for (String a : allowed) if (a.equals(up)) return a;
        return allowed[0];
    }

    private static Map<String, SubscriptionPaymentRepository.StatusTotal> byLabel(List<SubscriptionPaymentRepository.StatusTotal> totals) {
        Map<String, SubscriptionPaymentRepository.StatusTotal> m = new HashMap<>();
        for (SubscriptionPaymentRepository.StatusTotal t : totals) if (t.getLabel() != null) m.put(t.getLabel(), t);
        return m;
    }

    private static long count(Map<String, SubscriptionPaymentRepository.StatusTotal> m, String s) {
        SubscriptionPaymentRepository.StatusTotal t = m.get(s);
        return t == null || t.getCount() == null ? 0 : t.getCount();
    }

    private static BigDecimal amount(Map<String, SubscriptionPaymentRepository.StatusTotal> m, String s) {
        SubscriptionPaymentRepository.StatusTotal t = m.get(s);
        return t == null || t.getAmount() == null ? BigDecimal.ZERO : t.getAmount();
    }
}
