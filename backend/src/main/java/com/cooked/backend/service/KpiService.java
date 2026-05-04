package com.cooked.backend.service;

import com.cooked.backend.repository.AnalyticsEventRepository;
import com.cooked.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KpiService {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getGlobalKpis() {
        long totalUsers = userRepository.count();
        long premiumUsers = userRepository.countBySubscriptionStatus(com.cooked.backend.entity.SubscriptionStatus.PREMIUM);
        
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
        kpis.put("churn", "2.4%"); // Valeur simulée pour l'exemple
        
        return kpis;
    }
}
