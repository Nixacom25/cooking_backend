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
        
        // Calcul de l'ARPU (basé sur 9.99€ mensuel moyen)
        double arpu = totalUsers > 0 ? (premiumUsers * 9.99) / totalUsers : 0;

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalUsers", totalUsers);
        kpis.put("premiumUsers", premiumUsers);
        kpis.put("conversionRate", String.format("%.2f%%", conversionRate));
        kpis.put("arpu", String.format("%.2f€", arpu));
        kpis.put("ltv", String.format("%.2f€", arpu * 12)); // Estimation sur 12 mois
        kpis.put("churn", "2.1%"); // Valeur simulée
        
        // Récupération des 10 dernières transactions
        List<SubscriptionPayment> recentPayments = paymentRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .collect(Collectors.toList());
                
        kpis.put("recentTransactions", recentPayments);
        
        return kpis;
    }
}
