package com.cooked.backend.service;

import com.cooked.backend.entity.DripSend;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.DripSendRepository;
import com.cooked.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Nudges free users 3 and 7 days after sign-up (push when the app registered a
 * token, email otherwise). Off until "Free users drip" is turned on in Settings;
 * each user gets each step at most once (drip_sends).
 */
@Service
@RequiredArgsConstructor
public class DripFunnelService {

    private static final Logger log = LoggerFactory.getLogger(DripFunnelService.class);

    public enum Step {
        DAY3(3, "Cook more with Cooked Premium", "Unlimited scans, imports and recipe ideas are one tap away.",
                "Cuisinez plus avec Cooked Premium", "Scans, imports et idées de recettes sans limite, à portée de main."),
        DAY7(7, "Your Premium trial is still waiting", "Try Cooked Premium free and plan your week in minutes.",
                "Votre essai Premium vous attend", "Essayez Cooked Premium gratuitement et planifiez votre semaine en quelques minutes.");

        final int day;
        final String titleEn, bodyEn, titleFr, bodyFr;

        Step(int day, String titleEn, String bodyEn, String titleFr, String bodyFr) {
            this.day = day; this.titleEn = titleEn; this.bodyEn = bodyEn; this.titleFr = titleFr; this.bodyFr = bodyFr;
        }
    }

    private final UserRepository userRepository;
    private final DripSendRepository dripSends;
    private final PushNotificationService push;
    private final EmailService emailService;
    private final WorkspaceSettingsService settings;

    @Scheduled(cron = "0 0 10 * * ?")
    public void processDripCampaigns() {
        if (!settings.current().isDripEnabled()) {
            log.info("Drip funnel is off (Settings)");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        for (Step step : Step.values()) {
            LocalDateTime to = now.minusDays(step.day);
            for (User user : userRepository.findDripCohort(SubscriptionStatus.FREE, to.minusDays(1), to)) {
                if (send(user, step)) sent++;
            }
        }
        log.info("Drip funnel: {} message(s) sent", sent);
    }

    /** Sends one step to one user unless already done; returns true when something went out. */
    boolean send(User user, Step step) {
        if (user.getId() == null || dripSends.existsByUserIdAndStep(user.getId(), step.name())) return false;
        boolean fr = isFrench(user.getLanguage());
        String title = fr ? step.titleFr : step.titleEn;
        String body = fr ? step.bodyFr : step.bodyEn;
        String channel;
        if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            push.sendPush(user.getFcmToken(), title, body, Map.of("type", "drip", "step", step.name()));
            channel = "PUSH";
        } else if (user.getEmail() != null) {
            emailService.sendDripEmail(user.getEmail(), user.getFirstname(), title, body);
            channel = "EMAIL";
        } else {
            return false;
        }
        dripSends.save(DripSend.builder().userId(user.getId()).step(step.name()).channel(channel).build());
        return true;
    }

    static boolean isFrench(String language) {
        return language != null && language.trim().toUpperCase().startsWith("FR");
    }
}
