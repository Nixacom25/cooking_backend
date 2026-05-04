package com.cooked.backend.service;

import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class DripFunnelService {
    private static final Logger log = LoggerFactory.getLogger(DripFunnelService.class);

    private final UserRepository userRepository;

    /**
     * Job quotidien pour envoyer les relances aux non-convertis.
     * Exécuté à 10h chaque matin.
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void processDripCampaigns() {
        log.info("Starting Drip Funnel processing...");
        
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        // Utilisateurs à J+3 (Offre -20%)
        List<User> usersJ3 = userRepository.findUsersForDrip(threeDaysAgo, SubscriptionStatus.FREE);
        usersJ3.forEach(user -> sendPromotion(user, "-20% sur votre abonnement Premium"));

        // Utilisateurs à J+7 (Dernière chance)
        List<User> usersJ7 = userRepository.findUsersForDrip(sevenDaysAgo, SubscriptionStatus.FREE);
        usersJ7.forEach(user -> sendPromotion(user, "⚠️ Dernière chance pour profiter de votre offre"));
    }

    private void sendPromotion(User user, String message) {
        log.info("Sending Drip Push/Email to {}: {}", user.getEmail(), message);
        // Ici, intégration avec Firebase Cloud Messaging (FCM) ou Mailgun/SendGrid
    }
}
