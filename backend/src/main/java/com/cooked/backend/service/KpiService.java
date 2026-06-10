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

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.Role;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.Random;
import java.util.LinkedHashMap;

@Service
public class KpiService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserRepository userRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final EntityManager entityManager;

    public KpiService(AnalyticsEventRepository analyticsEventRepository, 
                      UserRepository userRepository,
                      SubscriptionPaymentRepository paymentRepository,
                      EntityManager entityManager) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
    }

    @PostConstruct
    @Transactional
    public void initMocks() {
        if (paymentRepository.count() == 0) {
            System.out.println("Generating Mock Subscription Payments...");
            User mockUser = new User();
            mockUser.setFirstname("Mock");
            mockUser.setLastname("User");
            mockUser.setEmail("mock" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
            mockUser.setPassword("password");
            mockUser.setRole(Role.CLIENT);
            mockUser.setSubscriptionStatus(SubscriptionStatus.PREMIUM);
            mockUser = userRepository.save(mockUser);

            Random random = new Random();
            for (int i = 0; i < 50; i++) {
                SubscriptionPayment payment = new SubscriptionPayment();
                payment.setUser(mockUser);
                payment.setAmount(random.nextBoolean() ? new BigDecimal("9.99") : new BigDecimal("99.99"));
                payment.setPlanType(payment.getAmount().doubleValue() < 20 ? "MONTHLY" : "YEARLY");
                payment.setStatus(random.nextInt(10) > 1 ? "SUCCESS" : "FAILED");
                payment.setStore(random.nextBoolean() ? "Google" : "Apple");
                payment.setStripePaymentId("pi_mock_" + UUID.randomUUID().toString().substring(0, 8));
                paymentRepository.save(payment);
                
                // Backdate manually using native query to bypass @CreationTimestamp
                int minusDays = random.nextInt(180);
                entityManager.createNativeQuery("UPDATE subscription_payments SET created_at = :date WHERE id = :id")
                    .setParameter("date", LocalDateTime.now().minusDays(minusDays))
                    .setParameter("id", payment.getId())
                    .executeUpdate();
            }
        }
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
        kpis.put("churn", "2.1%"); // Valeur simulée
        
        // Récupération des transactions récentes formattées
        List<Map<String, Object>> recentTransactions = allPayments.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("customer", p.getUser().getFirstname() + " " + p.getUser().getLastname());
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
