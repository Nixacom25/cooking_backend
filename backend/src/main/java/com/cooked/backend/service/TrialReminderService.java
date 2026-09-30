package com.cooked.backend.service;

import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final EmailService emailService;
    private final PushNotificationService pushNotificationService;

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
            // Only the yearly plan has a free trial (monthly is paid from day
            // one), so a trial always converts to yearly. The legacy
            // UserSubscription.isYearly flag defaulted to false at sign-up and
            // is never updated by RevenueCat purchases - reading it made every
            // reminder say "Monthly".
            String planName = "Yearly";
            // Real store price reported by the app (localized); never a
            // hardcoded amount. Omitted when unknown.
            String price = user.getPlanPriceLabel();

            emailService.sendTrialEndsTomorrowEmail(user.getEmail(), user.getFirstname(), planName, price);
            if (user.isPushEnabled() && user.isPushRemindersEnabled()) {
                pushNotificationService.sendPush(user.getFcmToken(), "Your free trial ends tomorrow",
                        "Your 3-day free trial ends tomorrow, then your " + planName + " plan"
                                + (price != null ? " (" + price + ")" : "")
                                + " starts. Keep cooking without interruption.",
                        java.util.Map.of("type", "trial_ends_tomorrow"));
            }

            user.setTrialEndingReminderSent(true);
            userRepository.save(user);
        }
    }
}
