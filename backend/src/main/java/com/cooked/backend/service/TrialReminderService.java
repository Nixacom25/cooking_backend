package com.cooked.backend.service;

import com.cooked.backend.entity.User;
import com.cooked.backend.entity.UserSubscription;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends the single "your trial ends tomorrow" email, exactly once per trial.
 * Runs hourly (trials can start - and so expire - at any time of day, not
 * just at midnight) and looks 23-25h ahead so no user is missed between runs.
 */
@Service
@RequiredArgsConstructor
public class TrialReminderService {
    private static final Logger log = LoggerFactory.getLogger(TrialReminderService.class);

    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;

    @Value("${subscription.monthly.price:$9.99/month}")
    private String monthlyPrice;

    @Value("${subscription.yearly.price:$59.99/year}")
    private String yearlyPrice;

    @Scheduled(cron = "0 15 * * * ?")
    public void sendTrialEndingReminders() {
        LocalDateTime from = LocalDateTime.now().plusHours(23);
        LocalDateTime to = LocalDateTime.now().plusHours(25);

        List<User> endingSoon = userRepository.findUsersWithTrialEndingSoon(from, to);
        if (endingSoon.isEmpty()) {
            return;
        }

        log.info("Sending trial-ends-tomorrow reminder to {} user(s)", endingSoon.size());

        for (User user : endingSoon) {
            boolean isYearly = userSubscriptionRepository.findByUserId(user.getId())
                    .map(UserSubscription::getIsYearly)
                    .map(Boolean::booleanValue)
                    .orElse(false);

            String planName = isYearly ? "Yearly" : "Monthly";
            String price = isYearly ? yearlyPrice : monthlyPrice;

            emailService.sendTrialEndsTomorrowEmail(user.getEmail(), user.getFirstname(), planName, price);
            pushNotificationService.sendPush(user.getFcmToken(), "Your trial ends tomorrow",
                    "Your " + planName + " trial (" + price + ") ends tomorrow. Keep cooking without interruption.",
                    java.util.Map.of("type", "trial_ends_tomorrow"));

            user.setTrialEndingReminderSent(true);
            userRepository.save(user);
        }
    }
}
