package com.cooked.backend.controller;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SubscriptionPayment;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Revenue & subscription summary for the new backoffice (Subscriptions and
 * Revenue screens). Computed from recorded payments and users' current
 * subscription state; amounts are in the payments' currency (EUR).
 */
@RestController
@RequestMapping("/api/admin/revenue")
@RequiredArgsConstructor
@Tag(name = "Admin Revenue", description = "Revenue and subscription metrics for administrators")
public class AdminRevenueController {

    private static final List<SubscriptionStatus> PAID = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PREMIUM);

    private final UserRepository userRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;

    @Operation(summary = "Revenue and subscription summary (last 30 days vs previous 30)")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> summary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from30 = now.minusDays(30);
        LocalDateTime from60 = now.minusDays(60);

        long active = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, PAID);
        long trials = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.TRIAL));
        long monthlyPlans = userRepository.countByRoleAndSubscriptionStatusInAndSubscriptionType(Role.CLIENT, PAID, SubscriptionType.MONTHLY);
        long yearlyPlans = userRepository.countByRoleAndSubscriptionStatusInAndSubscriptionType(Role.CLIENT, PAID, SubscriptionType.YEARLY);
        long cancelled = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.CANCELLED));
        long expired = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, List.of(SubscriptionStatus.EXPIRED));

        SubscriptionPlan plan = subscriptionService.getPlan();
        double monthlyPrice = plan.getMonthlyPrice() != null ? plan.getMonthlyPrice().doubleValue() : 0;
        double yearlyPrice = plan.getYearlyPrice() != null ? plan.getYearlyPrice().doubleValue() : 0;
        double mrr = monthlyPlans * monthlyPrice + yearlyPlans * yearlyPrice / 12.0;

        List<SubscriptionPayment> payments = paymentRepository.findAll();

        double revenue30 = 0, revenuePrev30 = 0;
        long renewals30 = 0, failures30 = 0, refunds30 = 0, paid30 = 0;
        Map<String, Double> byStore = new LinkedHashMap<>();
        Map<String, Double> byPlan = new LinkedHashMap<>();
        Map<LocalDate, Double> daily = new TreeMap<>();
        for (int i = 29; i >= 0; i--) daily.put(now.toLocalDate().minusDays(i), 0.0);
        Map<YearMonth, Double> monthly = new TreeMap<>();
        for (int i = 7; i >= 0; i--) monthly.put(YearMonth.from(now).minusMonths(i), 0.0);
        Set<UUID> payersBefore = new HashSet<>();

        payments.sort(Comparator.comparing(SubscriptionPayment::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (SubscriptionPayment p : payments) {
            if (p.getCreatedAt() == null) continue;
            String status = p.getStatus() == null ? "" : p.getStatus().toUpperCase();
            double amount = p.getAmount() != null ? p.getAmount().doubleValue() : 0;
            boolean in30 = p.getCreatedAt().isAfter(from30);
            boolean inPrev = !in30 && p.getCreatedAt().isAfter(from60);
            UUID userId = p.getUser() != null ? p.getUser().getId() : null;

            if (status.contains("FAIL")) { if (in30) failures30++; continue; }
            if (status.contains("REFUND")) { if (in30) refunds30++; continue; }
            if (!"SUCCESS".equals(status)) continue;

            if (in30) {
                revenue30 += amount;
                paid30++;
                if (userId != null && payersBefore.contains(userId)) renewals30++;
                byStore.merge(p.getStore() == null ? "Other" : p.getStore(), amount, Double::sum);
                byPlan.merge(planLabel(p.getPlanType()), amount, Double::sum);
                daily.computeIfPresent(p.getCreatedAt().toLocalDate(), (d, v) -> v + amount);
            } else if (inPrev) {
                revenuePrev30 += amount;
            }
            monthly.computeIfPresent(YearMonth.from(p.getCreatedAt()), (m, v) -> v + amount);
            if (userId != null) payersBefore.add(userId);
        }

        long everPaid = payersBefore.size();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("currency", "EUR");
        res.put("activeSubscriptions", active);
        res.put("activeTrials", trials);
        res.put("monthlyPlans", monthlyPlans);
        res.put("yearlyPlans", yearlyPlans);
        res.put("cancelled", cancelled);
        res.put("expired", expired);
        res.put("mrr", round(mrr));
        res.put("arr", round(mrr * 12));
        res.put("revenue30", round(revenue30));
        res.put("revenuePrev30", round(revenuePrev30));
        res.put("payments30", paid30);
        res.put("renewals30", renewals30);
        res.put("billingFailures30", failures30);
        res.put("refunds30", refunds30);
        // Share of people who ever paid that are now cancelled or expired.
        res.put("churnRate", everPaid > 0 ? round(100.0 * (cancelled + expired) / everPaid) : null);
        res.put("ltv", everPaid > 0 ? round(sumSuccess(payments) / everPaid) : null);
        res.put("byStore", roundMap(byStore));
        res.put("byPlan", roundMap(byPlan));
        List<Map<String, Object>> dailyList = new ArrayList<>();
        daily.forEach((d, v) -> dailyList.add(Map.of("date", d.toString(), "amount", round(v))));
        res.put("daily", dailyList);
        List<Map<String, Object>> monthlyList = new ArrayList<>();
        monthly.forEach((m, v) -> monthlyList.add(Map.of("month", m.toString(), "amount", round(v))));
        res.put("monthly", monthlyList);
        return ResponseEntity.ok(res);
    }

    private static String planLabel(String planType) {
        if (planType == null) return "Other";
        String t = planType.toUpperCase();
        if (t.startsWith("GIFT")) return "Gift";
        if (t.contains("YEAR") || t.contains("ANNUAL")) return "Annual";
        if (t.contains("MONTH")) return "Monthly";
        return "Other";
    }

    private static double sumSuccess(List<SubscriptionPayment> payments) {
        return payments.stream()
                .filter(p -> "SUCCESS".equalsIgnoreCase(p.getStatus()) && p.getAmount() != null)
                .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Map<String, Double> roundMap(Map<String, Double> m) {
        Map<String, Double> out = new LinkedHashMap<>();
        m.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> out.put(e.getKey(), round(e.getValue())));
        return out;
    }
}
