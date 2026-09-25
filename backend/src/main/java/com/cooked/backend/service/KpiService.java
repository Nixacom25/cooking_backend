package com.cooked.backend.service;

import com.cooked.backend.repository.AnalyticsEventRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.entity.SubscriptionPayment;
import com.cooked.backend.entity.SubscriptionStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;

@Service
public class KpiService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserRepository userRepository;
    private final SubscriptionPaymentRepository paymentRepository;

    public KpiService(AnalyticsEventRepository analyticsEventRepository,
                      UserRepository userRepository,
                      SubscriptionPaymentRepository paymentRepository) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    public Map<String, Object> getGlobalKpis() {
        long totalUsers = userRepository.count();
        long premiumUsers = userRepository.countBySubscriptionStatus(SubscriptionStatus.PREMIUM);
        
        // Calcul simplifié du taux de conversion
        double conversionRate = totalUsers > 0 ? (double) premiumUsers / totalUsers * 100 : 0;
        
        // Calcul de l'ARPU et LTV (Estimé sur les transactions SUCCESS)
        List<SubscriptionPayment> allPayments = paymentRepository.findAll();
        double totalRevenue = allPayments.stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()))
                .mapToDouble(p -> p.getAmount().doubleValue())
                .sum();
                
        double arpu = premiumUsers > 0 ? totalRevenue / premiumUsers : 0;

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalUsers", totalUsers);
        kpis.put("premiumUsers", premiumUsers);
        kpis.put("conversionRate", String.format("%.2f%%", conversionRate));
        kpis.put("arpu", String.format("%.2f€", arpu));
        kpis.put("ltv", String.format("%.2f€", arpu * 12));
        // Real churn needs a subscription-status-change history this app
        // doesn't keep (only the user's *current* status is stored) - "N/A"
        // is honest here; a specific-looking percentage was never actually
        // computed from anything and would be indistinguishable from a real
        // metric to whoever reads this dashboard.
        kpis.put("churn", "N/A");
        
        // Récupération des transactions récentes formattées
        List<Map<String, Object>> recentTransactions = allPayments.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    String first = p.getUser().getFirstname() == null ? "" : p.getUser().getFirstname().trim();
                    String last = p.getUser().getLastname() == null ? "" : p.getUser().getLastname().trim();
                    String fullName = (first + " " + last).trim();
                    map.put("customer", fullName.isEmpty() ? "Unknown" : fullName);
                    map.put("store", p.getStore());
                    map.put("product", p.getPlanType() + " Plan");
                    map.put("date", p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
                    map.put("revenue", "€" + p.getAmount());
                    map.put("status", p.getStatus());
                    return map;
                })
                .collect(Collectors.toList());
                
        kpis.put("recentTransactions", recentTransactions);
        
        // Chart Data (Derniers 6 mois)
        Map<String, Double> monthlyRevenue = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthDate = LocalDateTime.now().minusMonths(i);
            String monthName = monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            monthlyRevenue.put(monthName, 0.0);
        }
        
        for (SubscriptionPayment p : allPayments) {
            if ("SUCCESS".equals(p.getStatus()) && p.getCreatedAt().isAfter(LocalDateTime.now().minusMonths(6))) {
                String monthName = p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
                if (monthlyRevenue.containsKey(monthName)) {
                    monthlyRevenue.put(monthName, monthlyRevenue.get(monthName) + p.getAmount().doubleValue());
                }
            }
        }
        
        kpis.put("chartLabels", monthlyRevenue.keySet());
        kpis.put("chartData", monthlyRevenue.values());
        
        return kpis;
    }
}
